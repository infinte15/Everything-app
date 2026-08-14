package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Smart Scheduler auf Basis von Google OR-Tools CP-SAT.
 *
 * Modell (Reclaim-Stil):
 *  - Zeitachse   : Slots à {@link #GRID} Minuten ab startDate. Feiner als nötig wäre teuer,
 *                  gröber würde 15-Minuten-Raster der UI nicht mehr treffen.
 *  - Blockiert   : fixe Termine, Kurspläne und gepinnte Workouts werden VOR dem Modell zu einer
 *                  disjunkten Liste verschmolzen (siehe {@link #mergeBlocked}). Dadurch kann das
 *                  Modell nicht mehr INFEASIBLE werden, auch wenn sich zwei gepinnte Termine
 *                  überlappen.
 *  - Vergangenheit: geplant wird erst ab {@link #replanCutoff}. Blöcke, die davor begonnen haben,
 *                  werden weder gelöscht noch verschoben, sondern zählen wie gepinnte Termine
 *                  (Zeit blockiert, Minuten auf den Task, Tag auf die Wochenquote der Habit).
 *  - Arbeitszeit : NICHT als Schlaf-Blöcke modelliert, sondern als Ober-/Untergrenze pro Tag an
 *                  jedem Item (siehe DayWindow). Das ist billiger und verhindert zusätzlich, dass
 *                  ein Item über Mitternacht läuft.
 *  - Items       : Tasks (in Chunks zerlegt), Habit-Slots und flexible Workouts sind jeweils
 *                  OPTIONALE Intervalle mit Präsenz-Literal. Was nicht passt, wird verworfen und
 *                  als {@link AtRiskItem} gemeldet — der Kalender wird nie leer.
 *  - Einstellungen: bufferMinutes (Luft um Termine), breakDurationMinutes (Pause zwischen zwei
 *                  automatisch geplanten Blöcken) und peakProductivityTime (Leistungshoch, weicher
 *                  Zug auf Task-Blöcke) fließen direkt ins Modell ein.
 *  - Ziel        : zweistufig lexikografisch. Phase 1 minimiert die gewichtete Menge verworfener
 *                  Items, Phase 2 optimiert die Platzierungsqualität unter der Nebenbedingung,
 *                  Phase 1 nicht zu verschlechtern. Ein einzelner gewichteter Summenterm ist hier
 *                  nicht robust: die nötige Drop-Strafe hängt von der Item-Anzahl ab.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmartSchedulerService {

    // ACHTUNG: Diese Konstruktor-Argumentliste ist ein Test-Kopplungspunkt —
    // SmartSchedulerServiceTest injiziert über @InjectMocks.
    private final CalendarEventRepository    calendarEventRepository;
    private final HabitRepository            habitRepository;
    private final HabitCompletionRepository  habitCompletionRepository;
    private final WorkoutSessionRepository   workoutSessionRepository;
    private final CourseScheduleRepository   courseScheduleRepository;
    // Bewusst das Repository und nicht ProjectService: es gibt keine Platzhalter zu erzeugen
    // (Projekt-Sessions haben keine eigene Entität), und ProjectService publiziert selbst
    // ScheduleChangedEvent — eine Service-zu-Service-Kante wäre ein Zyklus in Wartung.
    private final ProjectRepository          projectRepository;
    private final UserService                userService;
    private final CalendarEventService       calendarEventService;
    private final TaskService                taskService;
    private final WorkoutPlanService         workoutPlanService;
    private final LastScheduleRunStore       lastRunStore;

    /**
     * Nur fürs Messen (siehe {@link #statementCount()}), deshalb per Feld und nicht über den
     * Konstruktor: die Argumentliste oben ist ein Test-Kopplungspunkt, und ein Messinstrument
     * hat dort nichts verloren. Im Mockito-Test bleibt das Feld null, der Zähler meldet dann -1.
     */
    @Autowired(required = false)
    private EntityManagerFactory entityManagerFactory;

    /**
     * Minuten pro Slot.
     *
     * 15 statt der früheren 5: die Domäne jeder Startvariablen schrumpft damit von 4320 auf 1440
     * Slots (14 Tage), was den Solve um ein Vielfaches beschleunigt — und genau das war der
     * spürbarste Teil der Wartezeit nach einem Verschieben. Geplante Zeiten liegen danach auf
     * Viertelstunden, was im Kalender ohnehin die ruhigere Darstellung ist.
     *
     * 15 teilt 30/45/60/90/120, die üblichen Task- und Workout-Dauern bleiben also exakt.
     * Kürzere Blöcke (10-Minuten-Habits) belegen einen vollen Slot; die Kachel im Kalender bleibt
     * über {@code realMinutes} bei ihrer echten Länge, nur der Solver reserviert etwas mehr.
     */
    private static final int GRID          = 15;
    private static final int SLOTS_PER_DAY = 1440 / GRID;

    private static final int DEFAULT_HABIT_DURATION_MIN    = 30;
    private static final int DEFAULT_WORKOUT_DURATION_MIN  = 45;
    private static final int MAX_CHUNKS_PER_TASK           = 12;
    private static final int FALLBACK_MIN_CHUNK_MIN        = 30;
    private static final int FALLBACK_MAX_CHUNK_MIN        = 120;
    private static final int FALLBACK_MAX_TASK_MIN_PER_DAY = 480;
    /**
     * Obergrenze für ALLE automatisch verplante Zeit eines Tages, wenn nichts eingestellt ist.
     *
     * Bewusst großzügig (10 Stunden): der Deckel ist ein Sicherheitsnetz gegen zugestellte Tage,
     * keine Vorgabe, wie viel jemand zu schaffen hat. Zu klein gewählt würde er still Blöcke
     * verwerfen, die problemlos gepasst hätten.
     */
    private static final int FALLBACK_MAX_SCHEDULED_MIN_PER_DAY = 600;
    private static final int DEFAULT_PROJECT_SESSION_MIN    = 60;
    /** Deckel gegen ein explodierendes Modell, falls jemand eine absurde Wochenzahl schickt. */
    private static final int MAX_PROJECT_SESSIONS_PER_WEEK  = 14;

    /**
     * Status, die Projektzeit verdienen. PLANNING ist bewusst dabei: das ist der Status, den
     * ProjectService.createProject vergibt — ohne ihn bekäme ein frisches Projekt nie einen Block.
     */
    private static final Set<ProjectStatus> SCHEDULABLE_PROJECT_STATUS =
            EnumSet.of(ProjectStatus.PLANNING, ProjectStatus.IN_PROGRESS, ProjectStatus.ACTIVE);

    // Phase-2-Gewichte. Alle Terme sind in Slots, damit sie vergleichbar sind.
    // Eine Deadline um einen Tag zu reißen kostet 40*prio*96; einen Tag später zu liegen 1*prio*96.
    // Der Solver sortiert also ~40 Items um, um eine Deadline zu retten.
    private static final long W_LATE      = 40;
    private static final long W_URGENCY   = 1;
    private static final long W_HABIT_DEV = 2;

    /**
     * Gewicht des selbst hergeleiteten Wunschzeit-Ankers einer Gewohnheit (siehe
     * {@link #habitWindows}). Bewusst das kleinste Gewicht im Modell: es muss nur das sonst
     * völlig flache Ziel aufbrechen, damit gleichwertige Lagen nicht alle auf den Arbeitsbeginn
     * kollabieren. Alles Weitere — Wunschfenster des Nutzers, Stabilität, Dringlichkeit —
     * soll es überstimmen können.
     */
    private static final long W_HABIT_ANCHOR = 1;
    /** Preis pro verschobenem Slot, sobald sich ein Block überhaupt bewegt. */
    private static final long W_MOVE      = 3;

    /**
     * Fester Preis dafür, einen bereits liegenden Block ÜBERHAUPT anzufassen — die Totzone.
     * Herleitung der Höhe siehe {@link #addStabilityTerm}.
     */
    private static final long W_MOVE_FIXED = 400;

    private static final long W_PEAK      = 2;

    /**
     * Strafe dafür, einen Task-Block in die Abendstunden zu legen (nach {@code coreHoursEnd}).
     *
     * Nötig geworden, seit die Dringlichkeit tageweise statt slotweise zählt: davor drückte sie
     * jeden Block an den Arbeitsbeginn, seither ist der Tag nach innen flach — und bei einem
     * Arbeitsende von 22:00 wäre 21:00 sonst eine gleichwertige Lage für eine Aufgabe.
     *
     * Die Höhe ist wie beim Leistungshoch nach oben begrenzt: die Abweichung kann höchstens die
     * Spanne coreHoursEnd..Arbeitsende erreichen (bei 18–22 Uhr sind das 16 Slots), kostet also
     * maximal 3*prio*16 = 48*prio, während ein Tag später zu liegen mindestens 1*prio*96 kostet.
     * Die Strafe kann einen Block damit nie auf einen anderen Tag schieben.
     */
    private static final long W_EVENING   = 3;

    /** Ende der Kernzeit, wenn der Nutzer nichts eingestellt hat. */
    private static final LocalTime DEFAULT_CORE_HOURS_END = LocalTime.of(18, 0);

    /**
     * Gewicht des fehlenden Ruhetags zwischen zwei Trainings.
     *
     * Muss die Dringlichkeit deutlich überbieten, sonst gewinnt "früher ist besser": drei
     * Einheiten auf die Tage 0/1/2 zu legen kostet an Dringlichkeit 0+96+192 = 288 Slots, auf
     * 0/2/4 dagegen 0+192+384 = 576 — also 288 mehr. Dafür fällt der Rückstand zum Ruhetag von
     * 2*96 = 192 Slots auf 0. Ab einem Gewicht von 2 kippt die Rechnung zugunsten der Verteilung;
     * 4 lässt Luft, damit sie auch neben Stabilitätsanker und Wunschfenstern bestehen bleibt.
     */
    private static final long W_REST      = 4;

    /** Angestrebter Abstand zwischen zwei Trainings: ein voller Ruhetag dazwischen. */
    private static final int  REST_DAYS_BETWEEN_WORKOUTS = 2;

    /**
     * Obergrenze für Phase 1 — ein DECKEL, kein Anteil.
     *
     * Vorher war es ein fester Anteil (0.35, dann 0.6) am Gesamtbudget. Beides ging am Verhalten
     * der beiden Phasen vorbei: Phase 1 ist ein Alles-oder-nichts, beweist Optimalität aber
     * meistens früh und gibt dann von sich aus ab — an einem echten Bestand (211 Intervalle)
     * gemessen nach ~0.38s von 6s erlaubter Zeit. Der Rest verfiel, denn Phase 2 bekam nur ihren
     * eigenen Anteil. Phase 2 wiederum beweist NIE Optimalität und verbraucht jede Sekunde, die
     * sie bekommt.
     *
     * Deckel plus Rest bildet genau das ab: Phase 1 bekommt, was sie höchstens braucht, Phase 2
     * alles Übrige. Damit wird das Gesamtbudget zur ehrlichen Obergrenze eines Laufs — und erst
     * dadurch lässt es sich überhaupt sinnvoll senken.
     */
    private static final double PHASE1_CAP_SECONDS = 1.5;

    /** Untergrenze für Phase 2, falls Phase 1 wider Erwarten ihren Deckel ausschöpft. */
    private static final double PHASE2_MIN_SECONDS = 1.0;

    /**
     * Zeitbudget für den Wiederholungslauf, wenn Phase 2 ohne Lösung endet.
     *
     * Deutlich kleiner als Phase 1 selbst: Der Lauf muss keine Lösung mehr SUCHEN, sondern nur die
     * bereits bekannte aus Phase 1 wiederherstellen — und die wird seit dem vollständigen Hint
     * (Startzeiten UND Präsenz-Literale) direkt gefunden. Vorher stand hier derselbe Anteil wie
     * für Phase 1, womit ein einzelner Lauf im schlechtesten Fall 6+4+6 = 16 Sekunden dauern
     * konnte.
     */
    private static final double PHASE1_RETRY_SECONDS = 1.0;

    /**
     * Nachhol-Fenster für einen bereits überfälligen Task, in Tagen ab jetzt.
     *
     * Eine harte Obergrenze gibt es hier nicht mehr zu erben — die Deadline ist vorbei, jede Lage
     * ist zu spät. Trotzdem darf der Block nicht irgendwo im Monat landen: "überfällig" heißt
     * "jetzt", nicht "in drei Wochen". Drei Tage lassen dem Solver genug Luft, um einen vollen
     * Tag zu umgehen, halten die Domäne der Startvariablen aber klein.
     */
    private static final int  CATCHUP_DAYS = 3;

    /**
     * Drop-Gewichte der wiederkehrenden Items — und die Invariante, die sie zusammenhält.
     *
     * <b>Jede echte Aufgabe schlägt beim Verdrängen jedes wiederkehrende Item.</b> Ein Training
     * oder eine Gewohnheit kommt nächste Woche wieder, eine Aufgabe nicht: sie hat eine Deadline,
     * oder sie bleibt eben liegen. Vorher galt das nicht einmal annähernd — eine Aufgabe ohne
     * Deadline kam auf {@code prio*100} = 100..500 und verlor damit gegen JEDES Training (300)
     * und gegen die meisten Gewohnheiten. Wer eine wichtige Aufgabe ohne Termin eintrug, sah sie
     * gegen das Vokabeltraining verlieren, ohne dass irgendwo stand, warum.
     *
     * Die Bänder sind deshalb getrennt statt überlappend:
     * <pre>
     *   Gewohnheit    prio*60                       =  60 .. 300
     *   Training      W_DROP_WORKOUT                =        240
     *   Projektzeit   W_DROP_PROJECT                =        200
     *   Aufgabe       W_DROP_TASK_BASE + prio*100*U = 500 .. 4400
     * </pre>
     * {@code min(Aufgabe) = 500} liegt strikt über {@code max(Wiederkehrendes) = 300}. Innerhalb
     * jedes Bandes bleibt die alte Ordnung monoton erhalten; es kippt ausschließlich das
     * Verhältnis Aufgabe-zu-Wiederkehrendem, und genau das ist der Zweck.
     *
     * Projektzeit bleibt das Verzichtbarste: deadlinefrei, quotenbasiert und nächste Woche wieder da.
     */
    private static final long W_DROP_PROJECT   = 200;
    private static final long W_DROP_WORKOUT   = 240;
    private static final long W_DROP_HABIT_PRIO = 60;
    private static final long W_DROP_TASK_BASE = 400;

    // Feld-Initialisierung, damit Mockito-Tests ohne Spring-Kontext einen sinnvollen Wert haben.
    @Value("${scheduler.solver-time-limit-seconds:2.0}")
    private double solverTimeLimitSeconds = 2.0;

    /**
     * Suchthreads für Phase 1 bzw. Phase 2.
     *
     * Die vier für Phase 1 stehen hier seit der Messung, dass zwölf Worker den Lauf bei einem
     * Budget von 1.5s von "stabil" auf "mal 19 Blöcke, mal keine Lösung" kippen ließen: jeder
     * Worker baut seine eigene Kopie des Modells, und das Hochfahren fraß den Anteil auf.
     *
     * Für Phase 2 gilt diese Rechnung nicht mehr. Sie bekommt seit dem Deckel-Verfahren fast das
     * gesamte Budget, das Modell ist seit dem rollierenden Horizont weniger als halb so groß, und
     * sie beweist ohnehin nie Optimalität — genau der Fall, in dem sich das Portfolio aus
     * feasibility_jump, quick_restart und den LP-Subsolvern auszahlt, die erst ab mehr Workern
     * überhaupt mitlaufen.
     */
    @Value("${scheduler.solver-workers-phase1:4}")
    private int solverWorkersPhase1 = 4;

    @Value("${scheduler.solver-workers-phase2:8}")
    private int solverWorkersPhase2 = 8;

    /**
     * Wie weit im Voraus TASKS Blöcke bekommen — gemessen in Tagen ab startDate, unabhängig vom
     * Gesamthorizont. Habits und Workouts laufen weiterhin über den vollen (seit dem rollierenden
     * Fenster: kurzen) Horizont: sie sind wiederkehrend und sollen in JEDER Woche im Kalender
     * stehen. Ein Task-Block Wochen im Voraus wäre dagegen wertlos (bis dahin hat sich die
     * Aufgabenlage längst geändert) und teuer: jeder Task-Chunk bekommt eine Tages-Boolean pro
     * Horizont-Tag, ein Habit-Slot nur für die sieben Tage seiner eigenen Woche.
     *
     * Seit {@code horizon-days=28} ist der Wert meist gar nicht mehr bindend — {@code taskLastDay}
     * ist das Minimum aus beiden. Er bleibt als eigene Grenze stehen, damit ein versuchsweise
     * längerer Horizont den Task-Teil des Modells nicht sofort mitwachsen lässt.
     *
     * Gilt seit {@link #taskBounds} nur noch für Tasks OHNE Deadline. Mit Deadline reicht das
     * Fenster genau bis dorthin — auch über diesen Nahbereich hinaus, denn dann ist es nach oben
     * ohnehin scharf begrenzt und bleibt klein. Ebenso wandert der Nahbereich mit, wenn
     * {@code notBefore} hinter ihm liegt; sonst bekäme so ein Task gar kein Fenster.
     */
    @Value("${scheduler.task-horizon-days:14}")
    private int taskHorizonDays = 14;

    /**
     * Feines Planungsfenster, wenn der Aufrufer kein Enddatum mitgibt — rollierend statt lang.
     *
     * Rund 90% des Modells kommen aus den wiederkehrenden Items (Gewohnheiten, Trainings,
     * Projektsitzungen), und die vervielfachen sich linear mit dem Horizont: bei 84 Tagen standen
     * ~500 Intervalle in EINEM globalen addNoOverlap, dessen Propagierung überlinear wächst. Ein
     * Plan für übernächsten Monat ist zugleich der wertloseste Teil des Ergebnisses — bis dahin
     * hat sich die Lage geändert. Weitergeschoben wird das Fenster täglich von
     * ScheduleRollForwardScheduler.
     */
    @Value("${scheduler.horizon-days:28}")
    private int horizonDays = 28;

    /** Aufräumfenster für generierte Blöcke; siehe {@link #cleanupHorizonEnd}. */
    @Value("${scheduler.cleanup-horizon-days:120}")
    private int cleanupHorizonDays = 120;

    /** Spiegelfenster für Vorlesungen; siehe {@link #classHorizonEnd}. */
    @Value("${scheduler.class-horizon-days:120}")
    private int classHorizonDays = 120;

    // OR-Tools JNI einmalig laden.
    static {
        Loader.loadNativeLibraries();
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    // Ein Lock pro User: zwei schnell aufeinanderfolgende Auslöser (z.B. "Plan anlegen" direkt
    // gefolgt von "Plan aktivieren") dürfen sich nicht überholen, sonst lesen beide Läufe
    // "0 vorhandene Platzhalter" und legen jeweils einen eigenen Satz an.
    private final Map<Long, Object> schedulingLocks = new java.util.concurrent.ConcurrentHashMap<>();

    @Transactional
    public ScheduleResult generateOptimalSchedule(Long userId, LocalDate startDate, LocalDate endDate) {
        // Ohne eigene Angabe reichen die Vorlesungen genau so weit wie die Planung. Wer den
        // Stundenplan darüber hinaus im Kalender sehen will, sagt es ausdrücklich — siehe unten.
        return generateOptimalSchedule(userId, startDate, endDate, endDate);
    }

    /**
     * Wie oben, aber mit eigenem Fenster für die Vorlesungen.
     *
     * Seit das Planungsfenster kurz und rollierend ist, fallen die beiden Zeiträume auseinander:
     * geplant wird auf Wochen, der Stundenplan soll aber auf Monate hinaus im Kalender stehen. Er
     * wird nicht geplant, sondern abgebildet, kostet den Löser also nichts.
     *
     * Bewusst ein Parameter und keine stille Ableitung im Rumpf: sonst schriebe ein Aufruf für
     * "plane mir diese eine Woche" ungefragt Vorlesungstermine bis in den übernächsten Monat.
     */
    @Transactional
    public ScheduleResult generateOptimalSchedule(Long userId, LocalDate startDate, LocalDate endDate,
                                                  LocalDate classEndDate) {
        Object lock = schedulingLocks.computeIfAbsent(userId, k -> new Object());
        synchronized (lock) {
            return doGenerateOptimalSchedule(userId, startDate, endDate, classEndDate);
        }
    }

    /** Standard-Enddatum des Horizonts, damit Aufrufer den Wert nicht doppelt konfigurieren müssen. */
    public LocalDate defaultHorizonEnd(LocalDate startDate) {
        return startDate.plusDays(Math.max(1, horizonDays));
    }

    /**
     * Bis wohin generierte Blöcke weggeräumt werden — deutlich weiter als geplant wird.
     *
     * {@code clearScheduledEvents} löscht nur INNERHALB des übergebenen Fensters. Würde hier der
     * Planungshorizont stehen, blieben die Blöcke aus früheren, längeren Läufen jenseits davon für
     * immer im Kalender stehen: geplant wird dort nichts mehr, gelöscht aber auch nicht. Das
     * Aufräumfenster ist damit die Bedingung dafür, dass der Planungshorizont überhaupt
     * verkleinert werden darf.
     */
    public LocalDate cleanupHorizonEnd(LocalDate startDate) {
        return startDate.plusDays(Math.max(1, Math.max(cleanupHorizonDays, horizonDays)));
    }

    /**
     * Bis wohin Vorlesungen in den Kalender gespiegelt werden.
     *
     * Bewusst unabhängig vom Planungshorizont: der Stundenplan wird nicht geplant, sondern
     * abgebildet. Er kostet den Löser nichts — außerhalb des Planungsfensters sperrt er keine Zeit
     * (siehe {@link #collectBlockedSlots}) — und ein Kalender, in dem in sechs Wochen keine
     * Vorlesung mehr steht, wäre schlicht falsch.
     */
    public LocalDate classHorizonEnd(LocalDate startDate) {
        return startDate.plusDays(Math.max(1, Math.max(classHorizonDays, horizonDays)));
    }

    private ScheduleResult doGenerateOptimalSchedule(Long userId, LocalDate startDate, LocalDate endDate,
                                                     LocalDate classEndDate) {
        log.info("Generiere CP-SAT Schedule für User {} | {} – {}", userId, startDate, endDate);

        long runStart        = System.nanoTime();
        long statementsStart = statementCount();

        UserPreferences prefs = userService.getOrCreatePreferences(userId);
        generateWorkoutPlaceholders(userId, startDate, endDate);

        LocalDateTime cutoff = replanCutoff(startDate);
        ScheduleInput input = collectScheduleInput(userId, startDate, endDate, cutoff);
        long collectMs = (System.nanoTime() - runStart) / 1_000_000;

        int totalDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        Axis axis = new Axis(startDate, totalDays);

        // Letzter Tag, an dem noch Task-Blöcke liegen dürfen (siehe taskHorizonDays).
        int taskLastDay = Math.min(totalDays - 1, Math.max(0, taskHorizonDays));

        // Gepinnte und eingefrorene Blöcke sind für die Zerlegung dasselbe: beide sind bereits
        // vergeben und dürfen weder neu geplant noch doppelt verplant werden.
        //
        // Welche ZEIT dabei blockiert ist, sagt weiterhin input.getFixedEvents() — auf den Horizont
        // beschnitten, weil nur Zeit auf der Zeitachse den Solver interessiert (collectBlockedSlots).
        //
        // Für die BUCHHALTUNG zählt dagegen die unbeschnittene Liste: ein Block, den der Nutzer
        // über den Horizont hinaus gezogen hat, blockiert dort zwar keine Zeit auf der Zeitachse,
        // versorgt sein Item aber trotzdem. Ohne diese Trennung hielt der Solver das Item für
        // ungeplant und legte im Horizont einen zweiten Block an — der verschobene blieb daneben
        // stehen, und genau das sah der Nutzer als Duplikat nach jedem Drag-and-Drop.
        //
        // Vereinigung statt Ersatz: die beiden Abfragen schneiden unterschiedlich. fixedEvents
        // greift Überlappungen (ein Block von gestern 23 Uhr, der in den Horizont hineinragt),
        // pinnedCommitments filtert über die Startzeit. Zusammen fällt keiner von beiden weg;
        // dedupById entfernt die große Schnittmenge.
        List<CalendarEvent> committed = dedupById(concat(
                input.getPinnedCommitments(), input.getFixedEvents(), input.getFrozenEvents()));

        // Verpasste Blöcke überfälliger Tasks sind davon ausgenommen: sie sperren ihre Zeit in der
        // Vergangenheit zwar weiter, dürfen dem Task aber weder Minuten gutschreiben noch ihm einen
        // Termin zurückschreiben (siehe isMissedBlock).
        List<CalendarEvent> credited = committed.stream()
                .filter(e -> !isMissedBlock(e, cutoff))
                .collect(Collectors.toList());

        // Fürs Wochenpensum zählen übersprungene Ausführungen mit, für alles andere nicht: sie
        // sperren keine Zeit und schreiben keine Minuten gut. Deshalb eine eigene Liste und nicht
        // einfach committed erweitert.
        List<CalendarEvent> counted = dedupById(concat(committed, input.getSkippedEvents()));

        Map<Long, Integer> pinnedMinutes = pinnedMinutesPerTask(credited);
        List<TaskChunk> chunks     = decomposeTasks(input.getTasks(), prefs, pinnedMinutes);
        List<HabitSlot> habitSlots = expandHabitSlots(input.getHabits(), prefs, startDate, endDate,
                pinnedDatesPerHabit(counted));
        List<ProjectSlot> projectSlots = expandProjectSlots(input.getProjects(), startDate, endDate,
                committedDatesPerProject(counted));

        long solveStart = System.nanoTime();
        SolveOutcome outcome = solveWithCpSat(chunks, habitSlots, projectSlots, input, axis, prefs,
                startDate, endDate, cutoff, taskLastDay);
        long solveMs = (System.nanoTime() - solveStart) / 1_000_000;

        // Der entscheidende Unterschied zur alten Implementierung: gelöscht wird ERST, wenn eine
        // verwertbare Lösung vorliegt. Ein leerer Kalender ist schlechter als ein veralteter.
        if (!outcome.isUsable()) {
            log.warn("CP-SAT lieferte keine Lösung ({}). Bestehender Schedule bleibt unverändert.",
                    outcome.getStatus());
            ScheduleResult failed = new ScheduleResult();
            failed.setSolverStatus(outcome.getStatus().name());
            failed.setMessage("Zeitplan konnte nicht neu berechnet werden — bestehende Termine bleiben erhalten.");
            return failed;
        }

        long persistStart = System.nanoTime();

        // Erst ab dem Umplanzeitpunkt anfassen: was bereits begonnen hat, ist Geschichte und
        // bleibt im Kalender stehen.
        //
        // Nach hinten reicht das Fenster bewusst weiter als geplant wird (cleanupHorizonEnd):
        // sonst überlebten die Blöcke früherer, längerer Läufe jenseits des Planungsfensters für
        // immer. Gepinntes ist nicht betroffen, gefiltert wird auf isFixed=false.
        reconcileScheduledEvents(userId, cutoff,
                cleanupHorizonEnd(startDate).atTime(23, 59, 59), outcome.getItems());
        log.info("Gespeichert: {} geplante Blöcke", outcome.getItems().size());
        writeBackTaskSpans(outcome, input.getTasks(), credited);

        // Die Vorlesungen laufen getrennt und landen NICHT in outcome.getItems(): ScheduleResult
        // zählt alles, was kein TASK ist, als "Habits/Workouts" und summiert es in
        // totalHoursScheduled. Eingemischt bliese das die Kennzahl mit Stunden auf, die der
        // Solver nie geplant hat.
        syncClassEvents(userId, startDate, classEndDate);
        long persistMs = (System.nanoTime() - persistStart) / 1_000_000;

        List<ScheduledItem> scheduledTasks = outcome.getItems().stream()
                .filter(i -> i.getType() == ScheduledItemType.TASK)
                .collect(Collectors.toList());
        // Alles, was kein TASK ist — Habits, Workouts und Projekt-Sessions. Sie gehören in
        // denselben Topf: wiederkehrende, quotenbasierte Blöcke ohne Deadline.
        List<ScheduledItem> scheduledRest = outcome.getItems().stream()
                .filter(i -> i.getType() != ScheduledItemType.TASK)
                .collect(Collectors.toList());

        ScheduleResult result = new ScheduleResult();
        result.setScheduledTasks(scheduledTasks);
        result.setScheduledHabits(scheduledRest);
        result.setUnscheduledTasks(findUnscheduledTasks(input.getTasks(), scheduledTasks, credited));
        result.setTotalTasksScheduled(scheduledTasks.size());
        result.setTotalHoursScheduled(calculateTotalHours(scheduledTasks, scheduledRest));
        result.setAtRisk(outcome.getAtRisk());
        result.setSolverStatus(outcome.getStatus().name());

        // Erst hier, nicht am Anfang: nur ein Lauf, der auch etwas geschrieben hat, zählt als
        // "heute geplant". Sonst hielte ein früh abgebrochener Lauf den Nachzügler-Sweep davon ab,
        // es noch einmal zu versuchen.
        userService.markScheduleRun(userId, startDate);

        // Damit die At-Risk-Liste auch den entprellten Hintergrundlauf überlebt — sonst erführe der
        // Nutzer nur bei dem einen manuell angestoßenen Lauf, dass eine Aufgabe liegen bleibt.
        lastRunStore.record(userId, outcome.getStatus().name(), outcome.getAtRisk(),
                scheduledTasks.size() + scheduledRest.size());

        logRunMetrics(userId, startDate, endDate, outcome, collectMs, solveMs, persistMs,
                (System.nanoTime() - runStart) / 1_000_000, statementsStart,
                scheduledTasks.size(), scheduledRest.size());
        return result;
    }

    /**
     * Eine Zeile pro Lauf, aus der sich jede Optimierung belegen lässt.
     *
     * Ohne sie ist jede Aussage über "schneller" Glaubenssache: die Laufzeit hängt am Zeitbudget
     * des Lösers, die Anzahl der Statements am Persistenzweg, und beides bewegt sich unabhängig
     * voneinander. Das Format ist bewusst {@code schlüssel=wert} und einzeilig, damit sich
     * mehrere Läufe mit grep und sort vergleichen lassen.
     */
    private void logRunMetrics(Long userId, LocalDate startDate, LocalDate endDate,
                               SolveOutcome outcome, long collectMs, long solveMs, long persistMs,
                               long totalMs, long statementsStart, int taskBlocks, int restBlocks) {
        long statements = statementsStart < 0 ? -1 : statementCount() - statementsStart;
        log.info("SCHED user={} tage={} totalMs={} collectMs={} solveMs={} p1Ms={} p2Ms={} "
                        + "persistMs={} intervalle={} placeables={} bloecke={}+{} drop={} obj={} "
                        + "status={} p2Retry={} statements={} atRisk={}",
                userId, ChronoUnit.DAYS.between(startDate, endDate) + 1, totalMs, collectMs,
                solveMs, outcome.getPhase1Ms(), outcome.getPhase2Ms(), persistMs,
                outcome.getIntervals(), outcome.getPlaceables(), taskBlocks, restBlocks,
                outcome.getDrop(), Math.round(outcome.getPlacementObjective()),
                outcome.getStatus(), outcome.isPhase2Retried(), statements,
                outcome.getAtRisk().size());
    }

    /**
     * Anzahl der bisher vorbereiteten JDBC-Statements, oder -1, wenn nicht messbar.
     *
     * Braucht {@code hibernate.generate_statistics=true}. Fehlt die Einstellung — oder läuft der
     * Service im Mockito-Test ganz ohne Spring-Kontext —, entfällt der Zähler stillschweigend;
     * er ist ein Messinstrument und darf einen Lauf niemals zum Scheitern bringen.
     */
    private long statementCount() {
        if (entityManagerFactory == null) return -1;
        try {
            Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            return stats.isStatisticsEnabled() ? stats.getPrepareStatementCount() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Füllt flexible WorkoutSession-Platzhalter für jede ISO-Woche im Horizont auf. */
    private void generateWorkoutPlaceholders(Long userId, LocalDate startDate, LocalDate endDate) {
        WorkoutPlan activePlan = workoutPlanService.getActivePlan(userId);
        if (activePlan == null) return;

        LocalDate weekCursor = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!weekCursor.isAfter(endDate)) {
            workoutPlanService.generateWeeklyPlaceholders(userId, activePlan, weekCursor);
            weekCursor = weekCursor.plusWeeks(1);
        }
    }

    // =========================================================================
    // ZEITACHSE
    // =========================================================================

    /** Rechnet zwischen LocalDateTime und Slot-Index um. */
    private static final class Axis {
        final LocalDateTime origin;
        final int totalDays;
        final int horizonSlots;

        Axis(LocalDate startDate, int totalDays) {
            this.origin       = startDate.atStartOfDay();
            this.totalDays    = totalDays;
            this.horizonSlots = totalDays * SLOTS_PER_DAY;
        }

        // In Sekunden gerechnet, nicht in Minuten: ChronoUnit.MINUTES schneidet die angebrochene
        // Minute ab, womit ein Termin, der um 10:45:34 endet, als "endet 10:45:00" gälte — der
        // Solver dürfte seine letzten Sekunden überplanen. Betrifft auch "jetzt", das immer
        // Sekundenbruchteile hat.
        private static final int SECONDS_PER_SLOT = GRID * 60;

        int floorSlot(LocalDateTime t) {
            return (int) Math.floorDiv(ChronoUnit.SECONDS.between(origin, t), SECONDS_PER_SLOT);
        }

        /** Aufrunden — für untere Schranken, damit nicht in einen angebrochenen Slot geplant wird. */
        int ceilSlot(LocalDateTime t) {
            long s = ChronoUnit.SECONDS.between(origin, t);
            return (int) -Math.floorDiv(-s, SECONDS_PER_SLOT);
        }

        LocalDateTime timeOf(int slot) {
            return origin.plusMinutes((long) slot * GRID);
        }

        static int slotsFor(int minutes) {
            return Math.max(1, (minutes + GRID - 1) / GRID);
        }
    }

    /** Ein erlaubtes Zeitfenster an einem konkreten Tag, bereits in Slots und startbezogen. */
    private record DayWindow(int day, int lo, int hi) {}

    /**
     * Wie weit ein Task in den Horizont hinein darf.
     *
     * @param lastDay       letzter erlaubter Tag (Index ab startDate)
     * @param latestEndSlot späteste Endzeit in Slots, oder {@code null} für unbegrenzt
     */
    private record TaskBounds(int lastDay, Integer latestEndSlot) {}

    // =========================================================================
    // OPTIONALE INTERVALLE
    // =========================================================================

    /** Ein flexibel platzierbares Item: optionales Intervall + Präsenz-Literal + Tages-Booleans. */
    private static final class Placeable {
        BoolVar present;
        IntVar  start;
        int     sizeSlots;
        int     realMinutes;
        IntervalVar interval;
        final Map<Integer, BoolVar> inDay = new LinkedHashMap<>();
        /** Je erlaubtem Tag die Schranken [lo, hi] für start — für brauchbare Startwerte (Hints). */
        final Map<Integer, int[]> dayBounds = new LinkedHashMap<>();
        /**
         * Wo dieser Block zuletzt lag, sofern es ihn schon gab (siehe addStabilityTerm).
         *
         * Wird für den Phase-2-Startwert gebraucht: ohne ihn schlägt {@link #preferredHint} die
         * WUNSCHzeit vor und arbeitet damit gegen den Stabilitätsanker, der den Block gerade auf
         * seiner alten Lage halten soll.
         */
        Integer previousSlot;
    }

    private Placeable makePlaceable(CpModel model, String name, int sizeSlots, int realMinutes,
                                    List<DayWindow> windows, int gapSlots) {
        Placeable p = new Placeable();
        p.sizeSlots   = sizeSlots;
        p.realMinutes = realMinutes;

        int lb = windows.stream().mapToInt(DayWindow::lo).min().orElse(0);
        int ub = windows.stream().mapToInt(DayWindow::hi).max().orElse(lb);
        p.present = model.newBoolVar("p_" + name);
        p.start   = model.newIntVar(lb, Math.max(lb, ub), "s_" + name);
        // Die Pause hängt am Intervall, nicht an einer eigenen Constraint pro Paar: das wäre
        // quadratisch in der Item-Anzahl. Das Tagesfenster oben rechnet weiterhin mit der echten
        // Größe, sonst würde die Pause am Feierabend stillschweigend Arbeitszeit wegnehmen.
        p.interval = model.newOptionalFixedSizeIntervalVar(
                p.start, sizeSlots + gapSlots, p.present, "iv_" + name);

        // "Entweder verworfen, oder an genau einem erlaubten Tag." Diese eine Constraint liefert
        // Tageszuordnung, Tagesbeschränkung und Drop-Option gleichzeitig.
        List<Literal> alternatives = new ArrayList<>();
        alternatives.add(p.present.not());
        for (DayWindow w : windows) {
            BoolVar b = model.newBoolVar(name + "_d" + w.day());
            p.inDay.put(w.day(), b);
            p.dayBounds.put(w.day(), new int[]{ w.lo(), w.hi() });
            model.addGreaterOrEqual(p.start, w.lo()).onlyEnforceIf(b);
            model.addLessOrEqual(p.start, w.hi()).onlyEnforceIf(b);
            alternatives.add(b);
        }
        model.addExactlyOne(alternatives);

        // Kanonisierung. Ohne das lässt CP-SAT start bei present=false frei laufen; jeder davon
        // abgeleitete Objective-Term erzeugt dann Phantomkosten, die den Solver dazu bringen,
        // Items grundlos zu verwerfen. Zusätzlich wird jeder abgeleitete Term unten auf 0 gepinnt.
        model.addEquality(p.start, lb).onlyEnforceIf(p.present.not());
        return p;
    }

    /** Variable, die im platzierten Fall {@code expr} entspricht und sonst 0 ist. */
    /**
     * Die Tagesdeckel: wie viel an einem Kalendertag automatisch verplant werden darf.
     *
     * {@code addCumulative} wäre hier falsch — es begrenzt die MOMENTANE Auslastung, nicht das
     * Integral über einen Tag. Die inDay-Booleans drücken genau das aus, was gemeint ist.
     *
     * Drei Grenzen statt bisher einer:
     * <ol>
     *   <li><b>Aufgaben-Minuten</b> ({@code maxTaskMinutesPerDay}) — unverändert.</li>
     *   <li><b>Gesamt-Minuten</b> ({@code maxScheduledMinutesPerDay}) — neu. Vorher war nur die
     *       Aufgabenzeit gedeckelt; Gewohnheiten, Trainings und Projektzeit liefen an jedem Limit
     *       vorbei und konnten einen Tag füllen, bevor die Aufgaben überhaupt drankamen.</li>
     *   <li><b>Anzahl der Aufgaben-Blöcke</b> ({@code maxTasksPerDay}) — die Einstellung gab es
     *       längst, gelesen hat sie im Löser bisher niemand.</li>
     * </ol>
     *
     * Gezählt wird über eine Tag-zu-Items-Zuordnung statt über eine Schleife durch alle Tage mal
     * alle Items: die allermeisten Paare sind ohnehin unmöglich (ein Item hat Tages-Booleans nur
     * für seine eigenen erlaubten Tage), und über den vollen Horizont war das eine spürbare
     * Menge Leerlauf beim Modellaufbau.
     */
    private void addDailyLoadLimits(CpModel model, UserPreferences prefs, Axis axis,
                                    List<TaskChunk> chunks, List<Placeable> allPlaceables) {
        Set<Placeable> taskPlaceables = chunks.stream()
                .map(c -> c.placeable)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        // Tag -> Items, die an diesem Tag liegen könnten.
        Map<Integer, List<Placeable>> perDay = new LinkedHashMap<>();
        for (Placeable p : allPlaceables) {
            for (Integer day : p.inDay.keySet()) {
                perDay.computeIfAbsent(day, k -> new ArrayList<>()).add(p);
            }
        }

        int taskCapSlots = Axis.slotsFor(nz(prefs.getMaxTaskMinutesPerDay(), FALLBACK_MAX_TASK_MIN_PER_DAY));
        int totalCapSlots = Axis.slotsFor(
                nz(prefs.getMaxScheduledMinutesPerDay(), FALLBACK_MAX_SCHEDULED_MIN_PER_DAY));
        Integer maxTasksPerDay = prefs.getMaxTasksPerDay();

        for (Map.Entry<Integer, List<Placeable>> e : perDay.entrySet()) {
            LinearExprBuilder taskLoad  = LinearExpr.newBuilder();
            LinearExprBuilder totalLoad = LinearExpr.newBuilder();
            LinearExprBuilder taskCount = LinearExpr.newBuilder();
            boolean anyTask = false;

            for (Placeable p : e.getValue()) {
                BoolVar b = p.inDay.get(e.getKey());
                int slots = Axis.slotsFor(p.realMinutes);
                totalLoad.addTerm(b, slots);
                if (taskPlaceables.contains(p)) {
                    taskLoad.addTerm(b, slots);
                    taskCount.addTerm(b, 1);
                    anyTask = true;
                }
            }

            if (anyTask) {
                model.addLessOrEqual(taskLoad.build(), taskCapSlots);
                if (maxTasksPerDay != null && maxTasksPerDay > 0) {
                    model.addLessOrEqual(taskCount.build(), maxTasksPerDay);
                }
            }
            model.addLessOrEqual(totalLoad.build(), totalCapSlots);
        }
    }

    private IntVar gated(CpModel model, Placeable p, String name, LinearArgument expr, int max) {
        IntVar v = model.newIntVar(0, max, name);
        model.addEquality(v, expr).onlyEnforceIf(p.present);
        model.addEquality(v, 0).onlyEnforceIf(p.present.not());
        return v;
    }

    /**
     * "Wie viele Tage nach Beginn des Horizonts liegt dieses Item" — als gegateter Term.
     *
     * Braucht keine neue Struktur: die Tages-Booleans stehen ohnehin schon in {@code p.inDay},
     * hier werden sie nur mit ihrem Tagesindex gewichtet aufsummiert. Genau EINE davon ist wahr
     * (siehe das {@code addExactlyOne} in makePlaceable), solange das Item platziert ist; ist es
     * verworfen, sind alle falsch und die Summe damit von selbst 0 — es braucht also kein
     * zusätzliches Gating für den verworfenen Fall.
     */
    private IntVar gatedDayIndex(CpModel model, Placeable p, String name, Axis axis) {
        LinearExprBuilder tage = LinearExpr.newBuilder();
        for (Map.Entry<Integer, BoolVar> e : p.inDay.entrySet()) {
            tage.addTerm(e.getValue(), e.getKey());
        }
        IntVar v = model.newIntVar(0, Math.max(0, axis.totalDays - 1), name);
        model.addEquality(v, tage.build());
        return v;
    }

    // =========================================================================
    // BLOCKIERTE ZEITEN
    // =========================================================================

    /**
     * Verschmilzt [start,end)-Slotpaare zu einer disjunkten, sortierten Liste.
     * Erst dadurch ist das Modell garantiert erfüllbar: überlappende gepinnte Termine würden als
     * separate Pflicht-Intervalle in addNoOverlap sofort INFEASIBLE erzeugen, und dagegen helfen
     * optionale Intervalle nicht.
     */
    private List<int[]> mergeBlocked(List<int[]> raw, int horizonSlots) {
        List<int[]> clipped = raw.stream()
                .map(b -> new int[]{ Math.max(0, b[0]), Math.min(horizonSlots, b[1]) })
                .filter(b -> b[1] > b[0])
                .sorted(Comparator.comparingInt(b -> b[0]))
                .collect(Collectors.toList());

        List<int[]> out = new ArrayList<>();
        for (int[] b : clipped) {
            if (!out.isEmpty() && b[0] <= out.get(out.size() - 1)[1]) {
                int[] last = out.get(out.size() - 1);
                last[1] = Math.max(last[1], b[1]);
            } else {
                out.add(new int[]{ b[0], b[1] });
            }
        }
        return out;
    }

    /**
     * Alles, was der Solver nicht anfassen darf. Der Puffer wird bewusst nur um Termine gelegt,
     * nicht um flexible Items: sonst kollidiert der nachlaufende Puffer mit der Tagesgrenze und
     * verkürzt die nutzbare Arbeitszeit stillschweigend. Effekt: Blöcke am Stück bleiben eng
     * aneinander, aber um Meetings herum entsteht Luft — genau das macht Reclaim auch.
     *
     * Vor einem Termin wird der Puffer um die Pause gekürzt, die jeder flexible Block ohnehin
     * hinter sich herzieht. Sonst addieren sich beide Einstellungen: bei 10 Minuten Puffer und
     * 15 Minuten Pause lägen 25 Minuten Leerlauf vor jedem Meeting, obwohl der Nutzer nirgends
     * mehr als 15 verlangt hat. Gemeint ist das Maximum der beiden, nicht ihre Summe.
     */
    private List<int[]> collectBlockedSlots(Axis axis, ScheduleInput input, LocalDate startDate,
                                            LocalDate endDate, int bufferSlots, int gapSlots) {
        List<int[]> raw = new ArrayList<>();
        int leadSlots = Math.max(0, bufferSlots - gapSlots);

        for (CalendarEvent ev : nz(input.getFixedEvents())) {
            if (ev.getStartTime() == null || ev.getEndTime() == null) continue;
            addBlock(raw, axis, ev.getStartTime(), ev.getEndTime(), leadSlots, bufferSlots);
        }

        // Eingefrorene Blöcke: vorne keine Luft — davor liegt die Vergangenheit, dort ist ohnehin
        // nichts mehr zu holen. Hinten die Pause, denn auf einen gerade beendeten Block folgt
        // genauso eine Pause wie zwischen zwei neu geplanten.
        for (CalendarEvent ev : nz(input.getFrozenEvents())) {
            if (ev.getStartTime() == null || ev.getEndTime() == null) continue;
            addBlock(raw, axis, ev.getStartTime(), ev.getEndTime(), 0, gapSlots);
        }

        for (LectureOccurrence o : expandCourseSchedules(input.getCourseSchedules(), startDate, endDate)) {
            addBlock(raw, axis, o.start(), o.end(), leadSlots, bufferSlots);
        }

        for (WorkoutSession w : nz(input.getFixedWorkouts())) {
            if (w.getStartTime() == null) continue;
            LocalDateTime end = w.getEndTime() != null
                    ? w.getEndTime()
                    : w.getStartTime().plusMinutes(nz(w.getDurationMinutes(), DEFAULT_WORKOUT_DURATION_MIN));
            addBlock(raw, axis, w.getStartTime(), end, leadSlots, bufferSlots);
        }

        return mergeBlocked(raw, axis.horizonSlots);
    }

    /** Ein konkreter Vorlesungstermin, den ein Stundenplan im betrachteten Zeitraum erzeugt. */
    private record LectureOccurrence(CourseSchedule schedule, LocalDateTime start, LocalDateTime end) {}

    /**
     * Expandiert Stundenpläne zu konkreten Terminen.
     *
     * Einzige Stelle, an der die Semestergrenzen ausgewertet werden: die Sperrzeiten für den
     * Solver und die Kalendereinträge müssen dieselbe Antwort bekommen. Liefen sie auseinander,
     * blockierte eine Vorlesung Zeit, die im Kalender gar nicht steht — oder umgekehrt.
     *
     * Stundenpläne gelten nur innerhalb ihres Semesters. Ohne Semester (oder ohne Datumsgrenzen
     * daran) gelten sie weiterhin unbegrenzt — Bestandsnutzer haben ihre Vorlesungen ohne
     * Semesterbezug angelegt, und deren Kalender darf sich hier nicht still verändern.
     */
    private List<LectureOccurrence> expandCourseSchedules(List<CourseSchedule> schedules,
                                                          LocalDate startDate, LocalDate endDate) {
        List<LectureOccurrence> out = new ArrayList<>();
        for (CourseSchedule cs : nz(schedules)) {
            if (cs.getDayOfWeek() == null || cs.getStartTime() == null || cs.getEndTime() == null) continue;
            LocalDate validFrom = semesterStart(cs);
            LocalDate validTo   = semesterEnd(cs);

            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                if (d.getDayOfWeek() != cs.getDayOfWeek()) continue;
                if (validFrom != null && d.isBefore(validFrom)) continue;
                if (validTo   != null && d.isAfter(validTo))    continue;
                out.add(new LectureOccurrence(cs, d.atTime(cs.getStartTime()), d.atTime(cs.getEndTime())));
            }
        }
        return out;
    }

    /**
     * Schreibt die Vorlesungen des Stundenplans als CLASS-Termine in den Kalender.
     *
     * Idempotent: gelöscht und neu angelegt wird ausschließlich ab dem Umplanzeitpunkt. Das
     * Zeitfenster von {@link CalendarEventService#clearClassEvents} und der Filter unten MÜSSEN
     * identisch bleiben — ist das Fenster zu klein, entstehen Duplikate; ist es zu groß, fehlen
     * Termine.
     *
     * Bewusst eigenständig und nicht Teil des Solver-Ergebnisses: die Vorlesungen werden nicht
     * geplant, sondern abgebildet, und müssen deshalb auch dann stimmen, wenn der Solver gar
     * nicht läuft (siehe ScheduleRegenerationCoordinator bei abgeschalteter Autoplanung).
     */
    @Transactional
    public int syncClassEvents(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime cutoff = replanCutoff(startDate);
        calendarEventService.clearClassEvents(userId, cutoff, endDate.atTime(23, 59, 59));

        List<ScheduledItem> items = expandCourseSchedules(
                        courseScheduleRepository.findByUserId(userId), startDate, endDate).stream()
                .filter(o -> !o.start().isBefore(cutoff))
                .map(o -> {
                    ScheduledItem item = new ScheduledItem();
                    item.setCourseSchedule(o.schedule());
                    item.setStartTime(o.start());
                    item.setEndTime(o.end());
                    item.setType(ScheduledItemType.CLASS);
                    return item;
                })
                .collect(Collectors.toList());

        saveScheduleToDatabase(userId, items);
        log.info("Vorlesungen synchronisiert: {} Termine für User {}", items.size(), userId);
        return items.size();
    }

    /** Beginn der Gültigkeit eines Stundenplans; {@code null} heißt „unbegrenzt". */
    private static LocalDate semesterStart(CourseSchedule cs) {
        Semester semester = semesterOf(cs);
        return semester != null ? semester.getStartDate() : null;
    }

    /** Ende der Gültigkeit eines Stundenplans; {@code null} heißt „unbegrenzt". */
    private static LocalDate semesterEnd(CourseSchedule cs) {
        Semester semester = semesterOf(cs);
        return semester != null ? semester.getEndDate() : null;
    }

    private static Semester semesterOf(CourseSchedule cs) {
        return cs.getCourse() != null ? cs.getCourse().getSemesterRef() : null;
    }

    private void addBlock(List<int[]> out, Axis axis, LocalDateTime start, LocalDateTime end,
                          int leadSlots, int trailSlots) {
        int s = axis.floorSlot(start) - leadSlots;
        int e = axis.ceilSlot(end) + trailSlots;
        if (e > s) out.add(new int[]{ s, e });
    }

    // =========================================================================
    // ZERLEGUNG: TASKS -> CHUNKS
    // =========================================================================

    /** Ein einzelner zu planender Block eines Tasks. */
    private static final class TaskChunk {
        Task task;
        int  durationMinutes;
        Placeable placeable;
    }

    /**
     * Ein Block gilt als VERPASST, wenn er vor dem Umplanzeitpunkt lag, nicht abgehakt wurde und zu
     * einem Task mit abgelaufener Deadline gehört. Die Deadline ist verstrichen und der Task steht
     * immer noch offen — die Zeit ist offensichtlich nicht genutzt worden.
     *
     * Seine Minuten dürfen deshalb nicht als geleistet zählen: sonst bleibt in {@link #chunkSizes}
     * nichts mehr übrig ({@code remaining <= 0}), der Task erzeugt keinen einzigen Chunk, erreicht
     * den Solver nie und {@link #writeBackTaskSpans} schreibt ihm seinen alten Termin von gestern
     * zurück — er verharrt für immer auf TODO mit einem Block in der Vergangenheit.
     *
     * Bewusst NUR bei abgelaufener Deadline: solange sie in der Zukunft liegt, bleibt es bei der
     * bisherigen Annahme "vergangener Block = gelaufene Zeit". Der Task-Status muss nicht geprüft
     * werden, findSchedulableTasks liefert ohnehin nur TODO.
     */
    private boolean isMissedBlock(CalendarEvent e, LocalDateTime cutoff) {
        if (e.getRelatedTask() == null || e.getCompletedAt() != null) return false;
        if (e.getStartTime() == null || !e.getStartTime().isBefore(cutoff)) return false;
        LocalDateTime deadline = e.getRelatedTask().getDeadline();
        return deadline != null && deadline.isBefore(LocalDateTime.now());
    }

    private Map<Long, Integer> pinnedMinutesPerTask(List<CalendarEvent> fixedEvents) {
        return nz(fixedEvents).stream()
                .filter(e -> e.getRelatedTask() != null && e.getStartTime() != null && e.getEndTime() != null)
                // Erledigte Blöcke zählen hier NICHT mit: ihre Minuten sind bereits
                // gutgeschrieben — bei einem Lernziel über logHours (das
                // estimatedDurationMinutes neu berechnet), sonst über Task.completedMinutes.
                // Doppelt abgezogen schrumpfte ein Sechs-Stunden-Ziel nach zwei erledigten
                // Blöcken auf drei statt viereinhalb Stunden Rest.
                .filter(e -> e.getCompletedAt() == null)
                .collect(Collectors.groupingBy(
                        e -> e.getRelatedTask().getId(),
                        Collectors.summingInt(e -> (int) ChronoUnit.MINUTES.between(e.getStartTime(), e.getEndTime()))));
    }

    /** Tage, an denen je Habit bereits ein gepinnter Termin liegt (manuell verschoben). */
    private Map<Long, WeeklyCommitments> pinnedDatesPerHabit(List<CalendarEvent> fixedEvents) {
        Map<Long, WeeklyCommitments> out = new LinkedHashMap<>();
        for (CalendarEvent e : nz(fixedEvents)) {
            if (e.getRelatedHabit() == null || e.getStartTime() == null) continue;
            out.computeIfAbsent(e.getRelatedHabit().getId(), k -> new WeeklyCommitments())
               .add(e.getStartTime().toLocalDate(), chargedDay(e), chargedWeek(e));
        }
        return out;
    }

    /**
     * Tage, an denen je Projekt bereits ein gepinnter oder eingefrorener Block liegt.
     *
     * Ein Set genügt für beide Aufgaben — Tagesausschluss und Kürzen der Wochenquote —, weil pro
     * Projekt und Tag ohnehin höchstens eine Session erlaubt ist (siehe Wochengruppen-Constraint).
     */
    private Map<Long, WeeklyCommitments> committedDatesPerProject(List<CalendarEvent> committed) {
        Map<Long, WeeklyCommitments> out = new LinkedHashMap<>();
        for (CalendarEvent e : nz(committed)) {
            if (e.getRelatedProject() == null || e.getStartTime() == null) continue;
            // Ist- und Solltag sind hier immer identisch: Projekte kennen nur die Wochenquote,
            // keine festen Wochentage — verschoben wird über chargedWeek verbucht.
            LocalDate day = e.getStartTime().toLocalDate();
            out.computeIfAbsent(e.getRelatedProject().getId(), k -> new WeeklyCommitments())
               .add(day, day, chargedWeek(e));
        }
        return out;
    }

    /**
     * Was für ein Item bereits festliegt.
     *
     * Drei getrennte Sichten, weil ein verschobener Block mehreres gleichzeitig ist:
     * <ul>
     *   <li>{@code days} — der <b>tatsächlich belegte</b> Tag. Dorthin darf keine zweite Session.</li>
     *   <li>{@code chargedDays} — der Tag, dessen <b>Ausführung damit abgegolten</b> ist. Bei
     *       Wochentags-Gewohnheiten der Ursprungstag, sonst derselbe wie oben.</li>
     *   <li>{@code perWeek} — das Pensum der <b>Woche</b>, aus der er stammt.</li>
     * </ul>
     * Ist und Soll auseinanderzuhalten ist der ganze Punkt: sonst gibt ein auf Mittwoch gezogener
     * Montagsblock den Montag wieder frei und die Gewohnheit bekommt dort einen zweiten Termin.
     */
    private static final class WeeklyCommitments {
        final Set<LocalDate> days = new HashSet<>();
        final Set<LocalDate> chargedDays = new HashSet<>();
        final Map<LocalDate, Integer> perWeek = new HashMap<>();

        void add(LocalDate day, LocalDate chargedDay, LocalDate week) {
            days.add(day);
            if (chargedDay != null) chargedDays.add(chargedDay);
            perWeek.merge(week, 1, Integer::sum);
        }

        int doneIn(LocalDate weekStart) {
            return perWeek.getOrDefault(weekStart, 0);
        }
    }

    private static LocalDate weekOf(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Die Woche, auf deren Pensum ein festliegender Block gebucht ist.
     *
     * Bestandszeilen ohne {@code targetWeekStart} zählen wie früher zu der Woche, in der sie
     * tatsächlich liegen.
     */
    private static LocalDate chargedWeek(CalendarEvent e) {
        return e.getTargetWeekStart() != null
                ? e.getTargetWeekStart()
                : weekOf(e.getStartTime().toLocalDate());
    }

    /**
     * Der Tag, dessen Ausführung ein festliegender Block abdeckt — das Tages-Pendant zu
     * {@link #chargedWeek}.
     *
     * Bestandszeilen ohne {@code targetDate} zählen wie früher zu dem Tag, an dem sie tatsächlich
     * liegen. Der Unterschied wird erst nach einem Drag-and-Drop sichtbar: dann steht hier noch
     * der Montag, während der Block schon am Mittwoch liegt.
     */
    private static LocalDate chargedDay(CalendarEvent e) {
        return e.getTargetDate() != null
                ? e.getTargetDate()
                : e.getStartTime().toLocalDate();
    }

    /**
     * Tage, an denen bereits ein Training steht, das der Solver nicht mehr anfassen darf —
     * gepinnt, in Arbeit oder erledigt.
     *
     * Bewusst ein flaches Set statt einer Map nach Id: die Regel lautet "höchstens ein Training
     * pro Tag", unabhängig von der Routine. Neben den Kalendereinträgen zählen auch die
     * {@code fixedWorkouts} mit, denn eine Einheit mit fester Uhrzeit muss ihren Tag auch dann
     * verbrauchen, wenn ihr Kalendereintrag (noch) fehlt.
     *
     * Gelesen wird zusätzlich aus {@code pinnedCommitments} — Buchhaltung, also ohne
     * Horizontgrenze. Ein Set von Tagen verträgt die Überschneidung mit {@code fixedEvents} ohnehin.
     */
    private Set<LocalDate> committedWorkoutDates(ScheduleInput input) {
        Set<LocalDate> days = concat(input.getPinnedCommitments(), input.getFixedEvents(),
                                     input.getFrozenEvents()).stream()
                .filter(e -> e.getRelatedWorkout() != null && e.getStartTime() != null)
                .map(e -> e.getStartTime().toLocalDate())
                .collect(Collectors.toCollection(HashSet::new));

        nz(input.getFixedWorkouts()).stream()
                .filter(w -> w.getStartTime() != null)
                .map(w -> w.getStartTime().toLocalDate())
                .forEach(days::add);

        return days;
    }

    private List<TaskChunk> decomposeTasks(List<Task> tasks, UserPreferences prefs,
                                           Map<Long, Integer> pinnedMinutes) {
        List<TaskChunk> out = new ArrayList<>();
        for (Task t : nz(tasks)) {
            int pinned = pinnedMinutes.getOrDefault(t.getId(), 0);
            List<Integer> sizes = chunkSizes(t, prefs, pinned);
            for (int i = 0; i < sizes.size(); i++) {
                TaskChunk c = new TaskChunk();
                c.task            = t;
                c.durationMinutes = sizes.get(i);
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Zerlegt die Restdauer eines Tasks in möglichst gleich große Blöcke.
     *
     * Die Größen sind bewusst KEINE Solver-Variablen: das wäre ein symmetrisches
     * Zahlpartitionsproblem, mit dem CP-SAT schlecht umgeht und das von Lauf zu Lauf
     * unterschiedliche Aufteilungen liefert. 300 Min bei max 120 ergibt [100,100,100],
     * nicht [120,120,60] — gleichmäßiger und näher an Reclaims Verhalten.
     */
    private List<Integer> chunkSizes(Task t, UserPreferences prefs, int pinnedMinutes) {
        int remaining = nz(t.getEstimatedDurationMinutes(), 0)
                - nz(t.getCompletedMinutes(), 0)
                - Math.max(0, pinnedMinutes);
        if (remaining <= 0) return List.of();

        int max = maxChunk(t, prefs);
        int min = minChunk(t, prefs);
        if (Boolean.FALSE.equals(t.getSplittable()) || remaining <= max) return List.of(remaining);

        int n = Math.min(MAX_CHUNKS_PER_TASK, (int) Math.ceil(remaining / (double) max));
        while (n > 1 && remaining / n < min) n--;   // keine Splitter unterhalb der Mindestgröße

        List<Integer> sizes = new ArrayList<>(n);
        int base = remaining / n, rest = remaining % n;
        for (int i = 0; i < n; i++) sizes.add(base + (i < rest ? 1 : 0));
        return sizes;
    }

    private int minChunk(Task t, UserPreferences p) {
        if (t.getMinChunkMinutes() != null && t.getMinChunkMinutes() > 0) return t.getMinChunkMinutes();
        return nz(p.getDefaultMinChunkMinutes(), FALLBACK_MIN_CHUNK_MIN);
    }

    private int maxChunk(Task t, UserPreferences p) {
        if (t.getMaxChunkMinutes() != null && t.getMaxChunkMinutes() > 0) return t.getMaxChunkMinutes();
        return nz(p.getDefaultMaxChunkMinutes(), FALLBACK_MAX_CHUNK_MIN);
    }

    // =========================================================================
    // ZERLEGUNG: HABITS -> SLOTS
    // =========================================================================

    /** Eine zu planende Habit-Ausführung. */
    private static final class HabitSlot {
        Habit habit;
        int   durationMinutes;
        /** Tage (Index im Horizont), an denen dieser Slot liegen darf. */
        final List<Integer> allowedDays = new ArrayList<>();
        /** Wunschfenster in Minuten ab Tagesbeginn; start == end bedeutet Punktwunsch. */
        int windowStartMin;
        int windowEndMin;
        /** true, wenn die Wunschzeit hergeleitet und nicht vom Nutzer gesetzt wurde. */
        boolean derivedWindow;
        LocalDate legacyDate;
        /** Nicht-null bei flexiblen Habits: alle Slots einer Habit-Woche teilen diesen Schlüssel. */
        String weekGroup;
        /** Montag der Woche und laufende Nummer darin — nur bei flexiblen Habits gesetzt. */
        LocalDate weekStart;
        int indexInWeek;
        Placeable placeable;
    }

    /**
     * Erzeugt die zu planenden Habit-Ausführungen.
     *
     * Zwei Modi:
     *  - Flexibel (timesPerWeek gesetzt): pro ISO-Woche k austauschbare Slots, die der Solver auf
     *    die erlaubten Tage verteilt — Reclaims "N mal pro Woche".
     *  - Legacy (timesPerWeek null): eine Ausführung pro gesetztem Wochentag-Flag, ausschließlich
     *    an diesem Tag. Bestandsdaten haben nach ddl-auto=update überall NULL, verhalten sich also
     *    unverändert.
     */
    private List<HabitSlot> expandHabitSlots(List<Habit> habits, UserPreferences prefs,
                                             LocalDate startDate, LocalDate endDate,
                                             Map<Long, WeeklyCommitments> pinnedDates) {
        List<HabitSlot> slots = new ArrayList<>();

        // Alle Erledigungen des Horizonts in EINER Abfrage, statt einer je Gewohnheit. Der
        // Zuschnitt auf den engeren Zeitraum der einzelnen Habit passiert unten im Speicher —
        // ihre Grenzen liegen ohnehin innerhalb des Horizonts.
        Map<Long, Set<LocalDate>> completionsByHabit = completionsInRange(nz(habits), startDate, endDate);

        // Wunschzeiten für alle Gewohnheiten auf einmal: die Anker entstehen erst im Vergleich
        // untereinander, lassen sich also nicht je Habit einzeln bestimmen.
        Map<Long, HabitTime> windows = habitWindows(habits, prefs);

        for (Habit habit : nz(habits)) {
            LocalDate rangeStart = (habit.getStartDate() != null && habit.getStartDate().isAfter(startDate))
                    ? habit.getStartDate() : startDate;
            LocalDate rangeEnd = (habit.getEndDate() != null && habit.getEndDate().isBefore(endDate))
                    ? habit.getEndDate() : endDate;
            if (rangeStart.isAfter(rangeEnd)) continue;

            final LocalDate from = rangeStart, to = rangeEnd;

            // Ein Tag, an dem bereits ein gepinnter Termin dieser Habit liegt, zählt wie ein
            // erledigter: der Tag ist belegt und die Wochenquote ist um eins reduziert. Ohne
            // das legt der Solver nach einem Drag-and-Drop eine ZWEITE Ausführung am selben
            // Tag an — der verschobene Block bliebe stehen, aber doppelt.
            //
            // Tag und Woche werden dabei getrennt geführt: eine Erledigung zählt auf die Woche,
            // in der sie stattfand, ein verschobener Block dagegen auf die Woche, aus der er
            // stammt. Sonst fiele die verlassene Woche unter ihr Pensum und bekäme Ersatz.
            WeeklyCommitments committed = new WeeklyCommitments();
            completionsByHabit.getOrDefault(habit.getId(), Set.of()).stream()
                    .filter(d -> !d.isBefore(from) && !d.isAfter(to))
                    // Eine Erledigung ist an ihrem Tag passiert — Ist und Soll fallen zusammen.
                    .forEach(d -> committed.add(d, d, weekOf(d)));

            WeeklyCommitments pinned = pinnedDates.get(habit.getId());
            if (pinned != null) {
                pinned.days.forEach(committed.days::add);
                pinned.chargedDays.forEach(committed.chargedDays::add);
                pinned.perWeek.forEach((w, n) -> committed.perWeek.merge(w, n, Integer::sum));
            }

            int duration = nz(habit.getDurationMinutes(), DEFAULT_HABIT_DURATION_MIN);
            int[] w = windowMinutes(habit, prefs);
            HabitTime window = windows.getOrDefault(habit.getId(), new HabitTime(w[0], w[1], false));

            if (isFlexible(habit)) {
                slots.addAll(flexibleSlots(habit, duration, window, committed, startDate, rangeStart, rangeEnd));
            } else {
                for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
                    if (!isHabitScheduledOn(habit, d)) continue;
                    // Zwei Gründe, den Tag zu überspringen, und beide sind nötig:
                    // days      — hier liegt schon ein Block dieser Gewohnheit (Tag ist belegt).
                    // chargedDays — die Ausführung DIESES Tages ist bereits versorgt, auch wenn
                    //               der Block inzwischen woanders liegt. Ohne das entsteht nach
                    //               jedem Drag-and-Drop ein zweiter Block am Ursprungstag.
                    if (committed.days.contains(d) || committed.chargedDays.contains(d)) continue;

                    HabitSlot s = new HabitSlot();
                    s.habit           = habit;
                    s.durationMinutes = duration;
                    s.windowStartMin  = window.startMin();
                    s.windowEndMin    = window.endMin();
                    s.derivedWindow   = window.derived();
                    s.legacyDate      = d;
                    s.allowedDays.add((int) ChronoUnit.DAYS.between(startDate, d));
                    slots.add(s);
                }
            }
        }
        return slots;
    }

    /** Erledigungen aller übergebenen Gewohnheiten im Horizont, in einer Abfrage, nach Habit-Id. */
    private Map<Long, Set<LocalDate>> completionsInRange(List<Habit> habits,
                                                         LocalDate startDate, LocalDate endDate) {
        List<Long> ids = habits.stream().map(Habit::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();

        return habitCompletionRepository
                .findByHabitIdInAndCompletionDateBetween(ids, startDate, endDate)
                .stream()
                .filter(c -> c.getHabit() != null && c.getCompletionDate() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getHabit().getId(),
                        Collectors.mapping(HabitCompletion::getCompletionDate, Collectors.toSet())));
    }

    private boolean isFlexible(Habit h) {
        return h.getTimesPerWeek() != null && h.getTimesPerWeek() > 0;
    }

    private List<HabitSlot> flexibleSlots(Habit habit, int duration, HabitTime window,
                                          WeeklyCommitments committed, LocalDate horizonStart,
                                          LocalDate rangeStart, LocalDate rangeEnd) {
        List<HabitSlot> out = new ArrayList<>();
        boolean anyWeekdayFlag = anyWeekdayFlagSet(habit);

        LocalDate cursor = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!cursor.isAfter(rangeEnd)) {
            final LocalDate weekCursor = cursor;   // effectively final für die Lambda unten
            LocalDate weekEnd = weekCursor.plusDays(6);

            // Tage dieser Woche, die im Horizont liegen und erlaubt sind.
            List<Integer> days = new ArrayList<>();
            int daysInRange = 0;
            for (LocalDate d = weekCursor; !d.isAfter(weekEnd); d = d.plusDays(1)) {
                if (d.isBefore(rangeStart) || d.isAfter(rangeEnd)) continue;
                daysInRange++;
                if (anyWeekdayFlag && !isHabitScheduledOn(habit, d)) continue;
                if (committed.days.contains(d)) continue;
                days.add((int) ChronoUnit.DAYS.between(horizonStart, d));
            }

            if (!days.isEmpty()) {
                int target = habit.getTimesPerWeek();
                // Angebrochene Woche am Rand des Horizonts anteilig planen.
                if (daysInRange < 7) target = (int) Math.ceil(target * daysInRange / 7.0);
                // Bereits erledigte bzw. festliegende Ausführungen dieser Woche abziehen.
                int done = committed.doneIn(weekCursor);
                int k = Math.min(days.size(), Math.max(0, target - done));

                String group = "h" + habit.getId() + "w" + weekCursor;
                for (int i = 0; i < k; i++) {
                    HabitSlot s = new HabitSlot();
                    s.habit           = habit;
                    s.durationMinutes = duration;
                    s.windowStartMin  = window.startMin();
                    s.windowEndMin    = window.endMin();
                    s.derivedWindow   = window.derived();
                    s.weekGroup       = group;
                    s.weekStart       = weekCursor;
                    s.indexInWeek     = i;
                    s.allowedDays.addAll(days);
                    out.add(s);
                }
            }
            cursor = cursor.plusWeeks(1);
        }
        return out;
    }

    private boolean anyWeekdayFlagSet(Habit h) {
        return Boolean.TRUE.equals(h.getMonday())    || Boolean.TRUE.equals(h.getTuesday())
            || Boolean.TRUE.equals(h.getWednesday()) || Boolean.TRUE.equals(h.getThursday())
            || Boolean.TRUE.equals(h.getFriday())    || Boolean.TRUE.equals(h.getSaturday())
            || Boolean.TRUE.equals(h.getSunday());
    }

    /**
     * Wunschzeiten aller Gewohnheiten — die Fenster aus {@link #windowMinutes}, breite Fenster
     * aber zu einem konkreten Ankerpunkt verdichtet.
     *
     * <p>Grund: {@link #windowDeviation} ist innerhalb des Fensters exakt 0. Ein Habit ohne
     * Wunschzeit bekommt als Fenster den ganzen Arbeitstag, ein MORNING-Habit immer noch sechs
     * Stunden — jede Lage darin kostet gleich viel. Bei so flachem Ziel behält CP-SAT die Lösung
     * aus Phase 1, und die sitzt auf der unteren Schranke der Startvariablen, also am
     * Arbeitsbeginn. Genau daher kam "eine Gewohnheit klebt direkt hinter der nächsten": nicht
     * aus Dringlichkeit, sondern aus Gleichgültigkeit.
     *
     * <p>Gewohnheiten mit demselben Fenster werden deshalb gleichmäßig darüber verteilt — jede
     * bekommt die Mitte ihrer Scheibe. Sortiert wird nach Id, damit derselbe Bestand immer
     * dieselben Anker ergibt und der Stabilitätsterm {@link #W_MOVE} weiter greift.
     *
     * <p>Der Anker bleibt weich: {@link #windowDeviation} ist ein Hinge-Loss, kein harter
     * Bereich. Ist die Wunschzeit belegt, rutscht der Block daneben, statt zu verschwinden.
     */
    private Map<Long, HabitTime> habitWindows(List<Habit> habits, UserPreferences prefs) {
        Map<Long, HabitTime>     base   = new LinkedHashMap<>();
        Map<String, List<Habit>> spread = new LinkedHashMap<>();

        for (Habit h : nz(habits)) {
            if (h.getId() == null) continue;
            int[] w = windowMinutes(h, prefs);
            base.put(h.getId(), new HabitTime(w[0], w[1], false));
            // Punktwünsche (preferredTime) bleiben unangetastet, nur breite Fenster brauchen Anker.
            if (w[1] > w[0]) spread.computeIfAbsent(w[0] + "-" + w[1], k -> new ArrayList<>()).add(h);
        }

        for (List<Habit> group : spread.values()) {
            List<Habit> ordered = group.stream()
                    .sorted(Comparator.comparing(Habit::getId))
                    .toList();

            for (int i = 0; i < ordered.size(); i++) {
                Habit h = ordered.get(i);
                HabitTime w = base.get(h.getId());
                int duration = nz(h.getDurationMinutes(), DEFAULT_HABIT_DURATION_MIN);
                int anchor = anchorWithin(w.startMin(), w.endMin(), duration, i, ordered.size());
                base.put(h.getId(), new HabitTime(anchor, anchor, true));
            }
        }
        return base;
    }

    /**
     * Wunschzeit einer Gewohnheit. {@code derived} unterscheidet den vom Nutzer gesetzten Wunsch
     * vom selbst hergeleiteten Anker — Letzterer wiegt im Ziel deutlich leichter (siehe
     * {@link #W_HABIT_ANCHOR}).
     */
    private record HabitTime(int startMin, int endMin, boolean derived) {}

    /**
     * Mitte der {@code index}-ten von {@code count} gleich breiten Scheiben des Fensters.
     *
     * Die Mitte und nicht der Rand: bei zwei Gewohnheiten in einem Sechs-Stunden-Fenster lägen
     * sonst eine ganz am Anfang und eine ganz am Ende, was denselben unruhigen Eindruck macht wie
     * das Stapeln vorher — nur umgekehrt.
     */
    private int anchorWithin(int from, int to, int durationMinutes, int index, int count) {
        // Spätester Start, bei dem der Block noch ins Fenster passt.
        int latest = Math.max(from, to - durationMinutes);
        if (count <= 1 || latest <= from) return from;

        int minute = from + (int) Math.round((double) (latest - from) * (2 * index + 1) / (2 * count));
        return Math.min(latest, (minute / GRID) * GRID);
    }

    /**
     * Wunschfenster in Minuten ab Tagesbeginn.
     *
     * Die Reihenfolge ist wichtig für die Rückwärtskompatibilität: ohne gesetztes Fenster, aber
     * mit preferredTime, entsteht das entartete Fenster [t, t] — die Abweichung ist dann exakt
     * |start − preferredTime| und damit das unveränderte alte Verhalten.
     */
    private int[] windowMinutes(Habit h, UserPreferences prefs) {
        HabitWindow w = h.getIdealWindow();
        if (w == HabitWindow.CUSTOM && h.getIdealWindowStart() != null && h.getIdealWindowEnd() != null) {
            return new int[]{ minuteOfDay(h.getIdealWindowStart()), minuteOfDay(h.getIdealWindowEnd()) };
        }
        if (w != null && w.hasFixedRange()) {
            return new int[]{ minuteOfDay(w.defaultStart()), minuteOfDay(w.defaultEnd()) };
        }
        if (w != HabitWindow.ANYTIME && h.getPreferredTime() != null) {
            int t = minuteOfDay(h.getPreferredTime());
            return new int[]{ t, t };
        }
        return new int[]{ minuteOfDay(workStart(prefs)), minuteOfDay(workEnd(prefs)) };
    }

    /** Spiegelt die Wochentag-Flag-Prüfung von Habit.isScheduledToday() im Frontend. */
    private boolean isHabitScheduledOn(Habit habit, LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY    -> Boolean.TRUE.equals(habit.getMonday());
            case TUESDAY   -> Boolean.TRUE.equals(habit.getTuesday());
            case WEDNESDAY -> Boolean.TRUE.equals(habit.getWednesday());
            case THURSDAY  -> Boolean.TRUE.equals(habit.getThursday());
            case FRIDAY    -> Boolean.TRUE.equals(habit.getFriday());
            case SATURDAY  -> Boolean.TRUE.equals(habit.getSaturday());
            case SUNDAY    -> Boolean.TRUE.equals(habit.getSunday());
        };
    }

    // =========================================================================
    // ZERLEGUNG: PROJEKTE -> SESSIONS
    // =========================================================================

    /**
     * Eine zu planende Projekt-Session.
     *
     * Bewusst OHNE eigene Entität — anders als beim Workout gibt es keinen Session-Zustand
     * (abhaken, Notizen, Ist-Dauer), der eine Zeile rechtfertigen würde. Die Wochenquote wird bei
     * jedem Lauf neu aus {@code Project.weeklySessionCount} gerechnet, der Kalenderblock ist die
     * einzige Kopie seiner Zeit. Das erspart Platzhalter-Aufstockung, Waisen-Zeilen beim
     * Verkleinern der Quote und das Zurückschreiben verschobener Zeiten.
     */
    private static final class ProjectSlot {
        Project project;
        int     durationMinutes;
        /** Tage (Index im Horizont), an denen dieser Slot liegen darf. */
        final List<Integer> allowedDays = new ArrayList<>();
        /** Alle Slots einer Projekt-Woche teilen diesen Schlüssel. */
        String    weekGroup;
        LocalDate weekStart;
        int       indexInWeek;
        Placeable placeable;
    }

    /**
     * Erzeugt pro ISO-Woche k austauschbare Projekt-Sessions — dieselbe Form wie flexible Habits
     * ("N mal pro Woche"), weshalb auch dieselben Wochengruppen-Constraints und derselbe
     * Stabilitätsanker greifen.
     */
    private List<ProjectSlot> expandProjectSlots(List<Project> projects, LocalDate startDate,
                                                 LocalDate endDate,
                                                 Map<Long, WeeklyCommitments> committedDates) {
        List<ProjectSlot> slots = new ArrayList<>();

        for (Project project : nz(projects)) {
            Integer weekly = project.getWeeklySessionCount();
            if (weekly == null || weekly <= 0) continue;   // 0 ist der Opt-out
            int perWeek = Math.min(MAX_PROJECT_SESSIONS_PER_WEEK, weekly);
            int duration = nz(project.getSessionDurationMinutes(), DEFAULT_PROJECT_SESSION_MIN);
            if (duration <= 0) continue;

            LocalDate rangeStart = (project.getStartDate() != null && project.getStartDate().isAfter(startDate))
                    ? project.getStartDate() : startDate;
            LocalDate rangeEnd = endDate;
            if (project.getTargetEndDate() != null && project.getTargetEndDate().isBefore(rangeEnd)) {
                rangeEnd = project.getTargetEndDate();
            }
            // Ein tatsächlich beendetes Projekt bekommt keine Zeit mehr, egal was der Status sagt.
            if (project.getActualEndDate() != null && project.getActualEndDate().isBefore(rangeEnd)) {
                rangeEnd = project.getActualEndDate();
            }
            if (rangeStart.isAfter(rangeEnd)) continue;

            WeeklyCommitments committed =
                    committedDates.getOrDefault(project.getId(), new WeeklyCommitments());

            LocalDate cursor = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            while (!cursor.isAfter(rangeEnd)) {
                final LocalDate weekStart = cursor;
                LocalDate weekEnd = weekStart.plusDays(6);

                List<Integer> days = new ArrayList<>();
                int daysInRange = 0;
                for (LocalDate d = weekStart; !d.isAfter(weekEnd); d = d.plusDays(1)) {
                    if (d.isBefore(rangeStart) || d.isAfter(rangeEnd)) continue;
                    daysInRange++;
                    // Tag mit gepinntem/eingefrorenem Block ist belegt — sonst legt der Solver
                    // nach einem Drag-and-Drop eine zweite Session am selben Tag an.
                    if (committed.days.contains(d)) continue;
                    days.add((int) ChronoUnit.DAYS.between(startDate, d));
                }

                if (!days.isEmpty()) {
                    int target = perWeek;
                    // Angebrochene Woche am Rand des Horizonts anteilig planen.
                    if (daysInRange < 7) target = (int) Math.ceil(target * daysInRange / 7.0);
                    // Gezählt wird über targetWeekStart, nicht über den Termin: ein in eine andere
                    // Woche gezogener Block bleibt auf seine Ursprungswoche gebucht. Sonst stünde
                    // diese Woche wieder unter Pensum und bekäme Ersatz am alten Platz.
                    int done = committed.doneIn(weekStart);
                    int k = Math.min(days.size(), Math.max(0, target - done));

                    String group = "p" + project.getId() + "w" + weekStart;
                    for (int i = 0; i < k; i++) {
                        ProjectSlot s = new ProjectSlot();
                        s.project         = project;
                        s.durationMinutes = duration;
                        s.weekGroup       = group;
                        s.weekStart       = weekStart;
                        s.indexInWeek     = i;
                        s.allowedDays.addAll(days);
                        slots.add(s);
                    }
                }
                cursor = cursor.plusWeeks(1);
            }
        }
        return slots;
    }

    // =========================================================================
    // CP-SAT
    // =========================================================================

    private SolveOutcome solveWithCpSat(List<TaskChunk> chunks, List<HabitSlot> habitSlots,
                                        List<ProjectSlot> projectSlots,
                                        ScheduleInput input, Axis axis, UserPreferences prefs,
                                        LocalDate startDate, LocalDate endDate, LocalDateTime cutoff,
                                        int taskLastDay) {

        List<WorkoutSession> flexibleWorkouts = nz(input.getFlexibleWorkouts());
        if (chunks.isEmpty() && habitSlots.isEmpty() && flexibleWorkouts.isEmpty()
                && projectSlots.isEmpty()) {
            return SolveOutcome.empty();
        }

        CpModel model = new CpModel();

        int workStartSlot = minuteOfDay(workStart(prefs)) / GRID;
        int workEndSlot   = minuteOfDay(workEnd(prefs)) / GRID;
        if (workEndSlot <= workStartSlot) workEndSlot = SLOTS_PER_DAY;   // defensiv gegen Fehlkonfiguration

        // Ende der Kernzeit, auf den Arbeitstag begrenzt: eine Kernzeit, die hinter dem Arbeitsende
        // liegt, schaltet die Abendstrafe schlicht ab, statt sie überall greifen zu lassen.
        int coreEndSlot = Math.min(workEndSlot, minuteOfDay(coreHoursEnd(prefs)) / GRID);

        int bufferSlots = slotsAufgerundet(nz(prefs.getBufferMinutes(), 0));
        // Derselbe Zeitpunkt, ab dem auch gelöscht und neu geschrieben wird — nicht ein zweites,
        // minimal späteres LocalDateTime.now(). Sonst entscheidet das Modell "überfällig" nach
        // einer anderen Uhr als die Meldung in extract.
        int nowSlot     = Math.max(0, axis.ceilSlot(cutoff));
        int[] peakWindow = peakWindow(prefs);

        // Mindestpause zwischen zwei automatisch geplanten Blöcken. Ohne sie stapelt der Solver
        // acht Stunden Arbeit lückenlos aufeinander — technisch optimal, menschlich unbrauchbar.
        int gapSlots = slotsAufgerundet(nz(prefs.getBreakDurationMinutes(), 0));

        // Platzhalter-Zuordnung für flexible Workouts, bewusst lokal: ein Feld würde
        // zwischen zwei Läufen (auch verschiedener User) Zustand verschleppen.
        Map<Long, Placeable> workoutPlaceables = new HashMap<>();

        List<int[]> blocked = collectBlockedSlots(axis, input, startDate, endDate, bufferSlots, gapSlots);

        List<IntervalVar> allIntervals = new ArrayList<>();
        for (int i = 0; i < blocked.size(); i++) {
            int[] b = blocked.get(i);
            allIntervals.add(model.newFixedInterval(b[0], b[1] - b[0], "blocked_" + i));
        }

        Map<String, Integer> previousStarts = previousStartSlots(input, axis, chunks);

        // Phase-1-Ziel: gewichtete Summe der VERWORFENEN Items. Der konstante Anteil bleibt im
        // Ausdruck, weil er für die spätere Schranke addLessOrEqual(dropCost, bestDrop) zählt.
        LinearExprBuilder dropB = LinearExpr.newBuilder();
        long dropConst = 0;

        // Phase-2-Ziel: Platzierungsqualität.
        List<IntVar> qVars = new ArrayList<>();
        List<Long>   qWeights = new ArrayList<>();

        List<Placeable> allPlaceables = new ArrayList<>();

        // Wunschzeit je Gewohnheit (Minute ab Tagesbeginn) — Startwert für Phase 2, siehe unten.
        Map<Placeable, Integer> desiredMinuteOfDay = new HashMap<>();

        // --- Task-Chunks ---
        Map<Long, List<TaskChunk>> chunksByTask = chunks.stream()
                .collect(Collectors.groupingBy(c -> c.task.getId(), LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<Long, List<TaskChunk>> e : chunksByTask.entrySet()) {
            List<TaskChunk> group = e.getValue();
            Task task = group.get(0).task;
            long weight = calculateTaskWeight(task, startDate);

            int earliest = nowSlot;
            if (task.getNotBefore() != null) earliest = Math.max(earliest, axis.ceilSlot(task.getNotBefore()));

            Integer deadlineSlot = task.getDeadline() != null ? axis.floorSlot(task.getDeadline()) : null;

            // Deadline schneidet das Fenster hart zu (siehe taskBounds). taskLastDay ist damit nur
            // noch der Nahbereich für Tasks OHNE Deadline.
            TaskBounds bounds = taskBounds(task, axis, taskLastDay, nowSlot, earliest);

            for (int ci = 0; ci < group.size(); ci++) {
                TaskChunk c = group.get(ci);
                int sizeSlots = Axis.slotsFor(c.durationMinutes);
                String name = "task" + task.getId() + "_c" + ci;

                List<DayWindow> windows = dayWindows(axis, workStartSlot, workEndSlot, sizeSlots, earliest,
                        null, bounds.lastDay(), bounds.latestEndSlot());
                Placeable p = makePlaceable(model, name, sizeSlots, c.durationMinutes, windows, gapSlots);
                c.placeable = p;
                allPlaceables.add(p);
                allIntervals.add(p.interval);

                dropB.addTerm(p.present, -weight);
                dropConst += weight;

                int prio = Math.max(1, nz(task.getPriority(), 3));

                // Dringlichkeit: der früher liegende TAG ist besser. Der Deadline-Faktor bricht den
                // Gleichstand zwischen zwei gleich wichtigen Aufgaben, von denen eine morgen und
                // eine nächsten Monat fällig ist — beide halten ihren Termin, aber nur eine
                // Reihenfolge davon ergibt Sinn.
                //
                // Bewusst der TAGESINDEX und nicht mehr p.start. Über p.start belohnte der Term
                // jeden einzelnen Slot: innerhalb desselben Tages war 08:00 messbar besser als
                // 10:00, mit prio*PLACEMENT_URGENCY pro Viertelstunde. Das erdrückte das
                // Leistungshoch (Gewicht 2), arbeitete gegen den Stabilitätsanker und presste alle
                // Blöcke an den Arbeitsbeginn — der sichtbare Teil der Beschwerde "Aufgaben liegen
                // zu ungünstigen Zeiten". Mit dem Tagesindex bleibt das Verhalten ÜBER Tage exakt
                // wie bisher (das Gewicht trägt dafür den Faktor SLOTS_PER_DAY, alle dokumentierten
                // Vergleiche im Modell sind ohnehin in "×96 pro Tag" formuliert), während der Tag
                // nach innen flach wird. Welche Uhrzeit es dort wird, entscheiden Leistungshoch,
                // Abendstrafe und der Stabilitätsanker — also die Terme, die dafür da sind.
                qVars.add(gatedDayIndex(model, p, "urg_" + name, axis));
                qWeights.add(W_URGENCY * prio * PLACEMENT_URGENCY[deadlineBucket(task, startDate)]
                        * SLOTS_PER_DAY);

                // Und ein schwacher Gleichstandsbrecher INNERHALB des Tages: liegen zwei Aufgaben
                // am selben Tag, soll die früher fällige zuerst drankommen. Ohne ihn wäre der Tag
                // nach innen völlig flach und die Reihenfolge beliebig — das war eine echte
                // Verschlechterung, kein Testartefakt.
                //
                // Bewusst NUR die Deadline-Stufe als Gewicht (1..3 pro Slot), ohne W_URGENCY und
                // ohne Priorität. Vorher stand hier prio*Stufe, also bis zu 15 pro Slot — damit
                // überbot der Term das Leistungshoch (2*prio) und presste alles an den
                // Arbeitsbeginn. Jetzt kostet die Reihenfolge über einen ganzen Arbeitstag
                // höchstens 3*56 = 168 und bleibt damit klar unter der Totzone des
                // Stabilitätsankers (400): Ordnung ja, aber niemals um den Preis, dass ein
                // liegender Block noch einmal verschoben wird.
                qVars.add(gated(model, p, "urgday_" + name, p.start, axis.horizonSlots));
                qWeights.add((long) PLACEMENT_URGENCY[deadlineBucket(task, startDate)]);

                // Abendstrafe: ohne sie ist 21:00 bei einem Arbeitsende von 22:00 eine völlig
                // gleichwertige Lage, seit die Dringlichkeit innerhalb des Tages nicht mehr zieht.
                // Die Schranke ist so gewählt, dass sie einen Block nie auf einen anderen Tag
                // schieben kann: von coreHoursEnd bis Arbeitsende sind es höchstens 16 Slots, also
                // 3*prio*16 = 48*prio gegen mindestens 1*prio*96 für einen Tagessprung.
                if (coreEndSlot > workStartSlot) {
                    qVars.add(windowDeviation(model, p, sizeSlots, workStartSlot * GRID,
                            coreEndSlot * GRID, axis, "eve_" + name));
                    qWeights.add(W_EVENING * prio);
                }

                // Leistungshoch: die Einstellung war bislang reine Dekoration. Das Gewicht liegt
                // über der Dringlichkeit, damit ein Block ins Hoch wandert, solange dort Platz
                // ist — aber weit unter der Deadline-Strafe, damit nichts dafür zu spät wird.
                //
                // Der Prioritätsfaktor gehört hier zwingend dazu, obwohl das Hoch nur die TAGESZEIT
                // wählt: er hält das Verhältnis zur Dringlichkeit (ebenfalls * prio) für jede
                // Priorität gleich. Und genau dieses Verhältnis ist die Sicherheitsgrenze — die
                // Abweichung kann innerhalb eines Tages höchstens die Breite des Arbeitstags
                // erreichen (bei 08–17 rund 36 Slots), kostet also maximal 2*prio*36 = 72*prio,
                // während ein Tag später zu liegen 1*prio*96 kostet. 72 < 96: das Hoch kann einen
                // Block niemals auf einen anderen Tag ziehen. Ohne den Faktor kippt das je nach
                // Priorität in die eine oder andere Richtung.
                if (peakWindow != null) {
                    qVars.add(windowDeviation(model, p, sizeSlots, peakWindow[0], peakWindow[1],
                            axis, "peak_" + name));
                    qWeights.add(W_PEAK * prio);
                }

                // Deadline ist jetzt WEICH. Vorher wurde ein überfälliger Task hart ans
                // Horizontende geklemmt; jetzt landet er so früh wie möglich und wird gemeldet.
                if (deadlineSlot != null) {
                    // Bei abgelaufener Deadline ist deadlineSlot negativ, die geforderte Verspätung
                    // also größer als der Horizont. Mit der alten Obergrenze horizonSlots war die
                    // Bedingung unten für einen Task, der länger als der Horizont überfällig ist,
                    // unerfüllbar — CP-SAT musste den Block verwerfen und writeBackTaskSpans hat ihm
                    // anschließend den Termin gelöscht. Der konstante Anteil |deadlineSlot| ist für
                    // alle Chunks des Tasks gleich und verschiebt das Optimum nicht: minimiert wird
                    // weiterhin W_LATE * prio * start, also "so früh wie möglich".
                    long lateMax = (long) axis.horizonSlots + sizeSlots + Math.max(0, -deadlineSlot);
                    IntVar late = model.newIntVar(0, lateMax, "late_" + name);
                    model.addGreaterOrEqual(
                            LinearExpr.newBuilder().addTerm(late, 1).addTerm(p.start, -1).build(),
                            (long) sizeSlots - deadlineSlot).onlyEnforceIf(p.present);
                    model.addEquality(late, 0).onlyEnforceIf(p.present.not());
                    qVars.add(late);
                    qWeights.add(W_LATE * prio);
                }

                // Bekannte Grenze: der Anker hängt am Chunk-INDEX. Ändert sich die Blockzahl
                // (Stunden erfasst, Ziel geändert), verschieben sich die Anker um eine Position
                // und die übrigen Blöcke werden auf fremde Vorgänger gezogen. Das korrigiert
                // sich beim nächsten Lauf von selbst; nach Chunk-Identität zu schlüsseln wäre
                // teurer als der Fehler.
                addStabilityTerm(model, p, previousStarts.get("task:" + task.getId() + ":" + ci),
                        axis, qVars, qWeights, name);
            }

            // Symmetriebrechung zwischen den Chunks eines Tasks.
            for (int ci = 1; ci < group.size(); ci++) {
                Placeable prev = group.get(ci - 1).placeable;
                Placeable cur  = group.get(ci).placeable;
                // Chronologische Ordnung tötet die gesamte Permutationssymmetrie.
                model.addLessOrEqual(
                        LinearExpr.newBuilder().addTerm(prev.start, 1).add(prev.sizeSlots).build(),
                        cur.start).onlyEnforceIf(new Literal[]{ prev.present, cur.present });
                // Präfix-Präsenz: passen nur 2 von 3 Chunks, bekommt man 1 und 2 — nie 1 und 3,
                // sonst stünde im Kalender "(1/3)" und "(3/3)" ohne "(2/3)".
                model.addImplication(cur.present, prev.present);
            }

            // Höchstens k Blöcke desselben Tasks an einem Tag.
            //
            // Bewusst HART und nicht als Strafterm: der Dringlichkeitsterm belohnt ausschließlich
            // „früher ist besser", eine weiche Verteilung würde er jederzeit überbieten. Die
            // Symmetriebrechung oben erlaubt ausdrücklich prev.end == cur.start — ohne diese
            // Grenze klebt also alles am Anfang des ersten freien Tages aneinander.
            //
            // Unlösbar wird davon nichts: makePlaceable lässt jedem Chunk über addExactlyOne den
            // Zweig present.not(). Passt die Verteilung nicht in den Horizont, fällt ein Block
            // heraus und wird als NO_ROOM gemeldet, statt das Modell zu sprengen.
            Integer perDay = task.getMaxChunksPerDay();
            if (perDay != null && perDay > 0 && group.size() > perDay) {
                for (int d = 0; d <= taskLastDay; d++) {
                    LinearExprBuilder sameDay = LinearExpr.newBuilder();
                    int candidates = 0;
                    for (TaskChunk c : group) {
                        BoolVar b = c.placeable != null ? c.placeable.inDay.get(d) : null;
                        if (b != null) { sameDay.addTerm(b, 1); candidates++; }
                    }
                    if (candidates > perDay) model.addLessOrEqual(sameDay.build(), perDay);
                }
            }
        }

        // --- Habit-Slots ---
        for (int i = 0; i < habitSlots.size(); i++) {
            HabitSlot s = habitSlots.get(i);
            int sizeSlots = Axis.slotsFor(s.durationMinutes);
            String name = "habit" + s.habit.getId() + "_" + i;

            List<DayWindow> windows = dayWindows(axis, workStartSlot, workEndSlot, sizeSlots, nowSlot,
                    s.allowedDays, axis.totalDays - 1);
            Placeable p = makePlaceable(model, name, sizeSlots, s.durationMinutes, windows, gapSlots);
            s.placeable = p;
            allPlaceables.add(p);
            allIntervals.add(p.interval);

            long weight = calculateHabitWeight(s.habit);
            dropB.addTerm(p.present, -weight);
            dropConst += weight;

            // Die harte Schranke bleibt die Arbeitszeit, nicht das Fenster — ein Habit, das nicht
            // in sein Wunschfenster passt, wird lieber daneben geplant als verworfen.
            // Mitte des Wunschfensters als Startwert für Phase 2; bei einem Punktwunsch ist das
            // genau die Wunschzeit.
            desiredMinuteOfDay.put(p, (s.windowStartMin + s.windowEndMin) / 2);

            IntVar dev = windowDeviation(model, p, sizeSlots, s.windowStartMin, s.windowEndMin,
                    axis, "dev_" + name);
            qVars.add(dev);
            // Ein hergeleiteter Anker wiegt bewusst viel leichter als ein gesetzter Wunsch — und
            // ohne Priorität, denn die sagt etwas über die Wichtigkeit der Gewohnheit aus, nicht
            // über die ihrer Uhrzeit. Er muss nur das flache Ziel aufbrechen; würde er auch den
            // Stabilitätsanker überbieten, spränge eine eingespielte Gewohnheit bei jedem Lauf
            // auf ihren berechneten Platz zurück.
            qWeights.add(s.derivedWindow
                    ? W_HABIT_ANCHOR
                    : W_HABIT_DEV * Math.max(1, nz(s.habit.getPriority(), 3)));

            // Flexible Slots sind untereinander austauschbar und haben deshalb kein Datum, an dem
            // sich ein Anker festmachen ließe. Sie werden über ihre Position innerhalb der Woche
            // zugeordnet — beide Seiten sind chronologisch sortiert (die Slots erzwungen durch die
            // Symmetriebrechung weiter unten), also trifft der i-te Slot den i-ten Vorgänger.
            String key = s.legacyDate != null
                    ? "habit:" + s.habit.getId() + ":" + s.legacyDate
                    : "habitweek:" + s.habit.getId() + ":" + s.weekStart + ":" + s.indexInWeek;
            addStabilityTerm(model, p, previousStarts.get(key), axis, qVars, qWeights, name);
        }

        // --- Gruppen-Constraints für flexible Habits (je Habit und ISO-Woche) ---
        // Die Anzahl "höchstens k mal pro Woche" ergibt sich bereits daraus, dass genau k Slots
        // erzeugt wurden und jeder höchstens einmal platziert wird. Nötig sind hier nur noch die
        // Verteilung über verschiedene Tage und das Brechen der Symmetrie zwischen den k
        // austauschbaren Slots — ohne Letzteres durchsucht CP-SAT k! gleichwertige Zuordnungen.
        Map<String, List<HabitSlot>> weekGroups = habitSlots.stream()
                .filter(s -> s.weekGroup != null && s.placeable != null)
                .collect(Collectors.groupingBy(s -> s.weekGroup, LinkedHashMap::new, Collectors.toList()));

        for (List<HabitSlot> group : weekGroups.values()) {
            Set<Integer> days = new LinkedHashSet<>();
            group.forEach(s -> days.addAll(s.allowedDays));
            addWeekGroupConstraints(model, group.stream().map(s -> s.placeable).toList(), days);
        }

        // --- Flexible Workouts ---
        // Tage, an denen schon ein gepinntes, laufendes oder erledigtes Training liegt. Anders als
        // bei Habits und Projekten wird NICHT nach Id gruppiert: "höchstens ein Training pro Tag"
        // gilt unabhängig von der Routine. Ohne diesen Ausschluss legt der Solver nach einem
        // Drag-and-Drop am Abend desselben Tages eine zweite Einheit an — die Zeit ist ja frei.
        Set<LocalDate> takenWorkoutDays = committedWorkoutDates(input);

        // Nach Zielwoche gruppiert, damit unten je Woche dieselben Constraints greifen wie bei
        // Habits und Projekten. Sortiert nach Id, weil das Repository keine Reihenfolge zusichert
        // und die Gruppenordnung sonst zwischen zwei Läufen kippen könnte — was den
        // Stabilitätsanker wertlos machen würde.
        Map<LocalDate, List<Placeable>> workoutWeeks = new LinkedHashMap<>();
        Map<LocalDate, Set<Integer>>    workoutWeekDays = new LinkedHashMap<>();

        List<WorkoutSession> orderedWorkouts = flexibleWorkouts.stream()
                .sorted(Comparator.comparing(WorkoutSession::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        for (WorkoutSession w : orderedWorkouts) {
            int duration  = nz(w.getDurationMinutes(), DEFAULT_WORKOUT_DURATION_MIN);
            int sizeSlots = Axis.slotsFor(duration);
            String name = "wo" + w.getId();

            LocalDate weekStart = w.getTargetWeekStart() != null ? w.getTargetWeekStart() : startDate;
            LocalDate weekEnd   = weekStart.plusDays(6);
            LocalDate from = weekStart.isBefore(startDate) ? startDate : weekStart;
            LocalDate to   = weekEnd.isAfter(endDate) ? endDate : weekEnd;
            if (from.isAfter(to)) continue;   // Zielwoche liegt außerhalb des Horizonts

            List<Integer> days = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (takenWorkoutDays.contains(d)) continue;
                days.add((int) ChronoUnit.DAYS.between(startDate, d));
            }
            // Jeder Tag der Zielwoche ist bereits belegt: die Einheit fällt aus, statt als
            // aussichtsloses Intervall im Modell zu stehen.
            if (days.isEmpty()) continue;

            List<DayWindow> windows = dayWindows(axis, workStartSlot, workEndSlot, sizeSlots, nowSlot,
                    days, axis.totalDays - 1);
            Placeable p = makePlaceable(model, name, sizeSlots, duration, windows, gapSlots);
            allPlaceables.add(p);
            allIntervals.add(p.interval);
            workoutPlaceables.put(w.getId(), p);

            dropB.addTerm(p.present, -W_DROP_WORKOUT);
            dropConst += W_DROP_WORKOUT;

            // Bewusst nur einfaches Gewicht. Früher stand hier W_URGENCY * 3 — das stärkste
            // "früher ist besser" im ganzen Modell — und hat sämtliche Einheiten einer Woche an
            // deren Anfang gezogen. Zusammen mit der fehlenden Tagesverteilung war das die
            // Ursache für zwei Trainings am selben Tag.
            qVars.add(gated(model, p, "urg_" + name, p.start, axis.horizonSlots));
            qWeights.add(W_URGENCY);

            addStabilityTerm(model, p, previousStarts.get("workout:" + w.getId()), axis, qVars, qWeights, name);

            workoutWeeks.computeIfAbsent(weekStart, k -> new ArrayList<>()).add(p);
            workoutWeekDays.computeIfAbsent(weekStart, k -> new LinkedHashSet<>()).addAll(days);
        }

        // --- Gruppen-Constraints für flexible Workouts (je Zielwoche) ---
        // Derselbe Helfer wie bei Habits und Projekten: höchstens eine Einheit pro Tag (hart) plus
        // chronologische Ordnung, die zugleich die Permutationssymmetrie der austauschbaren
        // Platzhalter bricht und damit den Solve beschleunigt.
        for (Map.Entry<LocalDate, List<Placeable>> e : workoutWeeks.entrySet()) {
            List<Placeable> group = e.getValue();
            Set<Integer> days = workoutWeekDays.get(e.getKey());
            addWeekGroupConstraints(model, group, days);
            addRestDayRule(model, group, days, "wow" + e.getKey(), qVars, qWeights);
        }

        // --- Projekt-Sessions ---
        for (int i = 0; i < projectSlots.size(); i++) {
            ProjectSlot s = projectSlots.get(i);
            int sizeSlots = Axis.slotsFor(s.durationMinutes);
            String name = "proj" + s.project.getId() + "_" + i;

            // Voller Horizont wie Habits und Workouts: Projektzeit ist wiederkehrend und soll in
            // JEDER Woche im Kalender stehen, nicht nur im 14-Tage-Task-Fenster.
            List<DayWindow> windows = dayWindows(axis, workStartSlot, workEndSlot, sizeSlots, nowSlot,
                    s.allowedDays, axis.totalDays - 1);
            Placeable p = makePlaceable(model, name, sizeSlots, s.durationMinutes, windows, gapSlots);
            s.placeable = p;
            allPlaceables.add(p);
            allIntervals.add(p.interval);

            dropB.addTerm(p.present, -W_DROP_PROJECT);
            dropConst += W_DROP_PROJECT;

            // Kein windowDeviation: ein Projekt hat kein Wunschfenster, die Session darf überall
            // in der Arbeitszeit liegen.
            qVars.add(gated(model, p, "urg_" + name, p.start, axis.horizonSlots));
            qWeights.add(W_URGENCY * 2);

            addStabilityTerm(model, p,
                    previousStarts.get("projectweek:" + s.project.getId() + ":" + s.weekStart + ":" + s.indexInWeek),
                    axis, qVars, qWeights, name);
        }

        // --- Gruppen-Constraints für Projekt-Sessions (je Projekt und ISO-Woche) ---
        Map<String, List<ProjectSlot>> projectWeeks = projectSlots.stream()
                .filter(s -> s.weekGroup != null && s.placeable != null)
                .collect(Collectors.groupingBy(s -> s.weekGroup, LinkedHashMap::new, Collectors.toList()));

        for (List<ProjectSlot> group : projectWeeks.values()) {
            Set<Integer> days = new LinkedHashSet<>();
            group.forEach(s -> days.addAll(s.allowedDays));
            addWeekGroupConstraints(model, group.stream().map(s -> s.placeable).toList(), days);
        }

        // --- Kernconstraint: nichts überlappt ---
        model.addNoOverlap(allIntervals.toArray(new IntervalVar[0]));

        addDailyLoadLimits(model, prefs, axis, chunks, allPlaceables);

        LinearExpr dropCost = dropB.add(dropConst).build();

        CpSolver solver = new CpSolver();
        solver.getParameters().setLogSearchProgress(false);
        // Fester Zufallskeim. Er nimmt eine Quelle der Streuung heraus, macht den Lauf aber NICHT
        // reproduzierbar: das Zeitbudget ist eine Wanduhr-Grenze, und welcher der Worker seine
        // Lösung zuerst hat, hängt damit an der Maschinenlast. Gemessen lieferten fünf Läufe über
        // denselben unveränderten Bestand fünf verschiedene Zielwerte (628705 bis 637651).
        //
        // Bit-genaue Wiederholbarkeit gäbe es nur über setMaxDeterministicTime — dann wäre aber
        // die Laufzeit nicht mehr beschränkt, und genau die ist hier das Ziel. Dass der Kalender
        // trotzdem stillhält, ist deshalb NICHT Aufgabe des Lösers, sondern des Stabilitätsankers
        // (siehe addStabilityTerm): er zieht jeden Block auf seine bisherige Lage zurück.
        solver.getParameters().setRandomSeed(42);

        // ---- Phase 1: möglichst viel (gewichtet) überhaupt unterbringen ----
        //
        // Bewusst OHNE Startwert. Ein Hint "alles platzieren" liegt nahe, ist aber genau dann
        // unerfüllbar, wenn Phase 1 überhaupt gebraucht wird — CP-SAT verbrachte danach das ganze
        // Budget mit dem Reparieren und lieferte gar keine Lösung mehr (UNKNOWN statt FEASIBLE).
        model.minimize(dropCost);
        solver.getParameters().setNumSearchWorkers(solverWorkersPhase1);
        solver.getParameters().setMaxTimeInSeconds(
                Math.max(0.05, Math.min(PHASE1_CAP_SECONDS, solverTimeLimitSeconds)));
        long p1Start = System.nanoTime();
        CpSolverStatus s1 = solver.solve(model);
        long phase1Ms = (System.nanoTime() - p1Start) / 1_000_000;
        if (s1 != CpSolverStatus.OPTIMAL && s1 != CpSolverStatus.FEASIBLE) {
            log.warn("CP-SAT Phase 1 ohne Lösung: {}", s1);
            return SolveOutcome.unusable(s1);
        }
        long bestDrop = Math.round(solver.objectiveValue());

        // ---- Phase 2: Qualität, ohne Phase 1 zu verschlechtern ----
        //
        // Phase 2 bekommt den gesamten Rest des Budgets. Ein früherer Versuch, ihr die ungenutzte
        // Zeit aus Phase 1 zuzuschlagen, galt als Rückschritt ("aus 6.6s wurden 10s, ohne dass
        // sich am Zielwert etwas tat") — das war aber die Beobachtung, dass Phase 2 jede geschenkte
        // Sekunde auch verbraucht, nicht dass sie sie verschwendet. Genau daraus folgt die heutige
        // Aufteilung: nicht "Phase 2 bekommt weniger", sondern "das Gesamtbudget ist die ehrliche
        // Obergrenze eines Laufs" — und die darf dann klein sein.
        //
        // Der Startwert kommt bewusst NICHT unverändert aus Phase 1: dort zählt nur, wie viel
        // überhaupt untergebracht wird, die Uhrzeit ist völlig beliebig. Phase 2 müsste von dort
        // aus die gute Lage erst suchen — und genau dafür reicht das knappe Zeitbudget nicht;
        // im Versuch landete "Vor dem Schlafen lesen" nach 2 Sekunden um 15:45 und erst nach 20
        // Sekunden um 21:30. Deshalb wird der Tag aus Phase 1 übernommen, die Uhrzeit darin aber
        // auf die Wunschzeit gesetzt. Ein Hint ist unverbindlich: passt er nicht, verwirft CP-SAT
        // ihn und sucht wie bisher weiter.
        //
        // Mitgegeben wird zusätzlich, WELCHE Items Phase 1 überhaupt platziert hat. Ohne die
        // Präsenz-Literale ist der Hint unvollständig: der Löser kennt Startzeiten für Items, von
        // denen er noch gar nicht weiß, ob sie vorkommen sollen. Vollständig ist er dagegen eine
        // nachweislich zulässige Lösung — deshalb reicht für den Notfall-Wiederholungslauf unten
        // eine Sekunde statt der vollen Phase-1-Zeit.
        model.clearHints();
        for (Placeable p : allPlaceables) {
            model.addHint(p.start, preferredHint(p, (int) solver.value(p.start), desiredMinuteOfDay));
            model.addHint(p.present, solver.booleanValue(p.present) ? 1 : 0);
        }
        model.addLessOrEqual(dropCost, bestDrop);
        model.minimize(LinearExpr.weightedSum(
                qVars.toArray(new LinearArgument[0]),
                qWeights.stream().mapToLong(Long::longValue).toArray()));
        solver.getParameters().setNumSearchWorkers(solverWorkersPhase2);
        solver.getParameters().setMaxTimeInSeconds(
                Math.max(PHASE2_MIN_SECONDS, solverTimeLimitSeconds - phase1Ms / 1000.0));
        // Kein setRelativeGapLimit: es lag hier kurzzeitig auf 2%, um das Budget nicht immer voll
        // auszuschöpfen, kostet aber genau die Feinarbeit, für die Phase 2 da ist. Der Löser
        // steigt aus, sobald er nah genug dran ist, und "nah genug" ist eine Viertelstunde
        // Verschiebung: derselbe Projektblock landete ohne jede Änderung am Bestand einmal um
        // 08:15 und im nächsten Lauf um 08:00. Für den Nutzer sieht das aus wie ein Kalender, der
        // von selbst herumspringt — genau dagegen gibt es den Stabilitätsterm.
        long p2Start = System.nanoTime();
        CpSolverStatus s2 = solver.solve(model);
        long phase2Ms = (System.nanoTime() - p2Start) / 1_000_000;
        // Der Zielwert der Platzierung, fürs Log. Er ist die einzige Möglichkeit, die Wirkung des
        // Zeitbudgets zu beurteilen: der Status bleibt bei realistischem Bestand immer FEASIBLE,
        // aber der Zielwert zeigt, ab wann mehr Zeit nichts mehr bringt.
        double placementObjective = (s2 == CpSolverStatus.OPTIMAL || s2 == CpSolverStatus.FEASIBLE)
                ? solver.objectiveValue() : Double.NaN;

        CpSolverStatus effective = (s2 == CpSolverStatus.OPTIMAL || s2 == CpSolverStatus.FEASIBLE) ? s2 : s1;
        boolean phase2Retried = false;
        if (s2 != CpSolverStatus.OPTIMAL && s2 != CpSolverStatus.FEASIBLE) {
            phase2Retried = true;
            // Phase 2 hat das Zeitbudget gerissen; Phase 1 hatte aber eine gültige Lösung.
            // Die ist zwar schlechter platziert, aber vollständig gültig — also erneut lösen,
            // damit der Solver-Zustand wieder zur Phase-1-Lösung passt.
            log.warn("CP-SAT Phase 2 ohne Lösung ({}), nutze Phase-1-Platzierung.", s2);
            model.minimize(dropCost);
            solver.getParameters().setNumSearchWorkers(solverWorkersPhase1);
            solver.getParameters().setMaxTimeInSeconds(PHASE1_RETRY_SECONDS);
            CpSolverStatus retry = solver.solve(model);
            if (retry != CpSolverStatus.OPTIMAL && retry != CpSolverStatus.FEASIBLE) {
                return SolveOutcome.unusable(retry);
            }
        }

        log.info("CP-SAT {} | Intervalle: {} | Chunks: {} Habits: {} Workouts: {} Projekte: {} | drop={} obj={}",
                effective, allIntervals.size(), chunks.size(), habitSlots.size(),
                flexibleWorkouts.size(), projectSlots.size(), bestDrop,
                Math.round(placementObjective));

        SolveOutcome outcome = extract(solver, effective, chunks, habitSlots, flexibleWorkouts,
                workoutPlaceables, projectSlots, axis, cutoff);
        outcome.setPhase1Ms(phase1Ms);
        outcome.setPhase2Ms(phase2Ms);
        outcome.setIntervals(allIntervals.size());
        outcome.setPlaceables(allPlaceables.size());
        outcome.setDrop(bestDrop);
        outcome.setPlacementObjective(placementObjective);
        outcome.setPhase2Retried(phase2Retried);
        return outcome;
    }

    /**
     * Constraints für eine Gruppe austauschbarer Slots derselben Woche (flexible Habits,
     * Projekt-Sessions).
     *
     * Die Anzahl "höchstens k mal pro Woche" ergibt sich bereits daraus, dass genau k Slots
     * erzeugt wurden und jeder höchstens einmal platziert wird. Nötig sind hier nur noch die
     * Verteilung über verschiedene Tage und das Brechen der Symmetrie zwischen den k
     * austauschbaren Slots — ohne Letzteres durchsucht CP-SAT k! gleichwertige Zuordnungen.
     *
     * Die chronologische Ordnung ist zugleich das, was den Stabilitätsanker treffsicher macht:
     * previousStartSlots nummeriert die Vorgängertermine ebenfalls chronologisch, also trifft
     * der i-te Slot den i-ten Vorgänger.
     */
    private void addWeekGroupConstraints(CpModel model, List<Placeable> group, Collection<Integer> days) {
        for (int day : days) {
            List<Literal> sameDay = new ArrayList<>();
            for (Placeable p : group) {
                BoolVar b = p.inDay.get(day);
                if (b != null) sameDay.add(b);
            }
            if (sameDay.size() > 1) model.addAtMostOne(sameDay);
        }

        for (int i = 1; i < group.size(); i++) {
            Placeable a = group.get(i - 1);
            Placeable b = group.get(i);
            model.addLessThan(a.start, b.start).onlyEnforceIf(new Literal[]{ a.present, b.present });
            model.addImplication(b.present, a.present);   // Slots der Reihe nach füllen
        }
    }

    /**
     * Ruhetag zwischen zwei aufeinanderfolgenden Trainings.
     *
     * <p>Wenn die erlaubten Tage es hergeben, <b>hart</b> formuliert — und zwar über die
     * Tages-Booleans, nicht über die Startzeiten: "mindestens 48 Stunden Abstand" wäre strenger
     * als gemeint (Montag 20:00 → Mittwoch 09:00 sind nur 37 Stunden, liegen aber sehr wohl zwei
     * Tage auseinander) und könnte das Modell unnötig unlösbar machen.
     *
     * <p>Die harte Form ist hier der Punkt: als reiner Strafterm muss der Solver die gute
     * Verteilung erst suchen, und genau dafür reicht das knappe Zeitbudget nicht — im Versuch
     * lagen die Einheiten nach 2 Sekunden auf aufeinanderfolgenden Tagen und erst nach 20
     * Sekunden richtig verteilt. Als Constraint steht die Verteilung dagegen schon in der ersten
     * zulässigen Lösung.
     *
     * <p>Nur wenn die Tage es nicht hergeben — fünf Einheiten in sieben Tagen —, bleibt es beim
     * weichen Term: lieber zwei Trainings an aufeinanderfolgenden Tagen als ein verworfenes.
     */
    private void addRestDayRule(CpModel model, List<Placeable> group, Collection<Integer> days,
                                String groupName, List<IntVar> qVars, List<Long> qWeights) {
        List<Integer> sorted = days.stream().sorted().toList();

        if (restDaysFit(sorted, group.size())) {
            for (int i = 1; i < group.size(); i++) {
                Placeable a = group.get(i - 1);
                Placeable b = group.get(i);
                // "a am Tag d" schließt "b am Tag d+1" aus. Denselben Tag verbietet bereits das
                // addAtMostOne aus addWeekGroupConstraints, die Reihenfolge die dortige Ordnung.
                for (int d : sorted) {
                    BoolVar ad = a.inDay.get(d);
                    BoolVar bd = b.inDay.get(d + 1);
                    if (ad != null && bd != null) model.addImplication(ad, bd.not());
                }
            }
            return;
        }

        int wanted = REST_DAYS_BETWEEN_WORKOUTS * SLOTS_PER_DAY;
        for (int i = 1; i < group.size(); i++) {
            Placeable a = group.get(i - 1);
            Placeable b = group.get(i);

            IntVar shortfall = model.newIntVar(0, wanted, "rest_" + groupName + "_" + i);
            // shortfall >= wanted - (b.start - a.start); die Minimierung drückt ihn auf genau
            // max(0, Rückstand). Nur wenn beide Einheiten tatsächlich geplant sind.
            model.addGreaterOrEqual(
                    LinearExpr.newBuilder()
                            .addTerm(shortfall, 1)
                            .addTerm(b.start, 1)
                            .addTerm(a.start, -1)
                            .build(),
                    wanted).onlyEnforceIf(new Literal[]{ a.present, b.present });

            // Kanonisierung wie bei den übrigen abgeleiteten Termen: ohne sie erzeugt ein
            // verworfenes Paar Phantomkosten, die den Solver zu grundlosen Verwerfungen treiben.
            model.addEquality(shortfall, 0).onlyEnforceIf(a.present.not());
            model.addEquality(shortfall, 0).onlyEnforceIf(b.present.not());

            qVars.add(shortfall);
            qWeights.add(W_REST);
        }
    }

    /**
     * Startwert für Phase 2, in dieser Reihenfolge: bisherige Lage, sonst Wunschzeit am Tag aus
     * Phase 1, sonst der Wert aus Phase 1.
     *
     * Die bisherige Lage steht bewusst VORNE. Sonst schlägt der Hint die Wunschzeit vor, während
     * der Stabilitätsanker den Block gerade dort festhalten will, wo er schon liegt — zwei Kräfte
     * in verschiedene Richtungen, und der Löser verbringt sein Budget damit, zwischen ihnen hin
     * und her zu suchen. Mit der Totzone aus {@link #addStabilityTerm} gewinnt am Ende ohnehin
     * die alte Lage; sie gleich als Startwert zu setzen, spart genau diese Suche.
     *
     * Ohne beides — Tasks, Workouts und Projekt-Sessions ohne Vorgeschichte — bleibt es beim Wert
     * aus Phase 1. Der Rückgabewert wird auf die Schranken des Tages begrenzt, damit der Hint eine
     * zulässige Lage beschreibt und der Solver ihn nicht gleich wieder verwerfen muss.
     */
    private int preferredHint(Placeable p, int phase1Slot, Map<Placeable, Integer> desiredMinuteOfDay) {
        if (p.previousSlot != null) return p.previousSlot;

        Integer desired = desiredMinuteOfDay.get(p);
        if (desired == null) return phase1Slot;

        int day = phase1Slot / SLOTS_PER_DAY;
        int[] bounds = p.dayBounds.get(day);
        if (bounds == null) return phase1Slot;   // Phase 1 hat das Item verworfen

        int candidate = day * SLOTS_PER_DAY + desired / GRID;
        return Math.max(bounds[0], Math.min(bounds[1], candidate));
    }

    /**
     * Lassen sich {@code k} der erlaubten Tage mit je einem Ruhetag dazwischen auswählen?
     *
     * Gierig von vorn: der früheste zulässige Tag ist immer eine optimale Wahl, weil er die
     * meisten Möglichkeiten für die restlichen offen lässt. Damit ist die Antwort exakt und die
     * harte Variante oben beweisbar erfüllbar — sie kann also keine Einheit kosten.
     */
    private boolean restDaysFit(List<Integer> sortedDays, int k) {
        if (k <= 1) return true;

        int used = 0;
        Integer last = null;
        for (int d : sortedDays) {
            if (last == null || d - last >= REST_DAYS_BETWEEN_WORKOUTS) {
                used++;
                last = d;
                if (used >= k) return true;
            }
        }
        return false;
    }

    /**
     * Pro Tag ein erlaubtes Startfenster, bereits um "jetzt" und die Arbeitszeit beschnitten.
     * {@code lastDay} begrenzt zusätzlich, wie weit in den Horizont hinein das Item überhaupt
     * darf — für Tasks der Task-Horizont bzw. ihre Deadline, für Habits und Workouts der volle
     * Horizont.
     */
    private List<DayWindow> dayWindows(Axis axis, int workStartSlot, int workEndSlot, int sizeSlots,
                                       int earliestSlot, List<Integer> restrictToDays, int lastDay) {
        return dayWindows(axis, workStartSlot, workEndSlot, sizeSlots, earliestSlot, restrictToDays,
                lastDay, null);
    }

    /**
     * Wie oben, zusätzlich mit einer spätesten ENDZEIT.
     *
     * {@code lastDay} allein wäre tagesgenau: eine Deadline am Freitag um 12:00 ließe noch einen
     * Block am Freitagnachmittag zu. {@code latestEndSlot} schneidet den letzten Tag deshalb an
     * der echten Uhrzeit ab. Ist danach kein Fenster mehr übrig, hat das Item keine zulässige
     * Lage — es wird über sein Präsenz-Literal verworfen und in {@link #extract} gemeldet.
     */
    private List<DayWindow> dayWindows(Axis axis, int workStartSlot, int workEndSlot, int sizeSlots,
                                       int earliestSlot, List<Integer> restrictToDays, int lastDay,
                                       Integer latestEndSlot) {
        List<DayWindow> out = new ArrayList<>();
        int upper = Math.min(lastDay, axis.totalDays - 1);
        for (int d = 0; d <= upper; d++) {
            if (restrictToDays != null && !restrictToDays.contains(d)) continue;
            int base = d * SLOTS_PER_DAY;
            int lo = Math.max(base + workStartSlot, earliestSlot);
            int hi = base + workEndSlot - sizeSlots;
            if (latestEndSlot != null) hi = Math.min(hi, latestEndSlot - sizeSlots);
            if (lo <= hi) out.add(new DayWindow(d, lo, hi));
        }
        return out;
    }

    /**
     * Bis wohin die Blöcke eines Tasks reichen dürfen.
     *
     * Die Deadline ist damit eine HARTE Grenze und nicht mehr nur eine Strafe: was nicht mehr
     * davor passt, bekommt kein Fenster, wird verworfen und als {@link AtRiskItem} gemeldet. Ein
     * Block nach der Deadline half niemandem — er stand im Kalender, als sei die Sache geplant,
     * obwohl der Termin längst gerissen war.
     *
     * Der Zuschnitt ist zugleich der größte Hebel für die Laufzeit: ein Task mit Deadline in zwei
     * Tagen bekommt zwei statt {@code taskHorizonDays} Tages-Booleans, und die Domäne seiner
     * Startvariablen schrumpft entsprechend. Deshalb darf ein Task MIT Deadline auch über den
     * Nahbereich hinausreichen (bis zum vollen Horizont), ohne dass das Modell wächst: sein
     * Fenster ist genau so groß, wie es sein muss.
     */
    private TaskBounds taskBounds(Task task, Axis axis, int defaultLastDay, int nowSlot,
                                  int earliestSlot) {
        int maxDay = axis.totalDays - 1;
        int lastDay;
        Integer latestEnd = null;

        if (task.getDeadline() == null) {
            lastDay = defaultLastDay;
        } else {
            int deadlineSlot = axis.floorSlot(task.getDeadline());
            if (deadlineSlot <= nowSlot) {
                // Überfällig: die Deadline als Obergrenze wäre leer, jede Lage ist zu spät.
                // Stattdessen ein kurzes Nachhol-Fenster ab jetzt — der weiche late-Term sorgt
                // darin weiter für "so früh wie möglich".
                lastDay = Math.min(maxDay, nowSlot / SLOTS_PER_DAY + CATCHUP_DAYS);
            } else {
                // Der Nahbereich bleibt die Obergrenze, auch wenn die Deadline weiter weg liegt.
                // Ohne diesen Deckel wuchsen Aufgaben mit Deadline in vier oder fünf Wochen
                // (Klausurvorbereitung, Steuererklärung) von 14 auf 31 Tages-Booleans, und Phase 1
                // fand in ihrem Anteil am Zeitbudget keine brauchbare Menge mehr: an einem echten
                // Bestand blieb sie bei Drop 28200 statt 800 stehen und ließ 16 von 17 Aufgaben
                // ungeplant. Für die Deadline kostet der Deckel nichts — eine Aufgabe, die schon
                // in den nächsten 14 Tagen eingeplant wird, reißt einen Termin in vier Wochen nicht.
                lastDay = Math.min(defaultLastDay, deadlineSlot / SLOTS_PER_DAY);
                // Die scharfe Endzeit nur, wenn die Deadline auch wirklich im Fenster liegt.
                if (deadlineSlot / SLOTS_PER_DAY <= defaultLastDay) latestEnd = deadlineSlot;
            }
        }

        // notBefore kann hinter jede der obigen Grenzen fallen — bei einem Nahbereich von 14 Tagen
        // und "nicht vor in drei Wochen" bliebe kein einziges Fenster übrig, und der Task wurde
        // bisher zwangsverworfen und fälschlich als NO_ROOM gemeldet. Der Nahbereich wandert
        // deshalb mit dem frühesten erlaubten Start mit. Mit harter Deadline-Grenze wäre das
        // sinnlos: dort liegt die Obergrenze fest, ein späterer notBefore heißt schlicht, dass der
        // Termin nicht mehr zu halten ist.
        if (latestEnd == null) {
            int earliestDay = Math.max(0, earliestSlot) / SLOTS_PER_DAY;
            if (earliestDay > lastDay) lastDay = Math.min(maxDay, earliestDay + defaultLastDay);
        }

        return new TaskBounds(Math.max(0, lastDay), latestEnd);
    }

    /**
     * Hinge-Loss auf ein Tagesfenster: innerhalb kostenfrei, außerhalb linear ansteigend.
     *
     * Das Fenster ist bewusst weich. Hart formuliert würde ein Item, das nirgends in sein Fenster
     * passt, verworfen — ein Block am „falschen“ Tageszeitpunkt ist aber immer noch besser als
     * gar keiner. Bei einem entarteten Fenster (start == end) fallen die beiden Schranken
     * zusammen und die Abweichung ist exakt |start − Wunschzeit|.
     */
    private IntVar windowDeviation(CpModel model, Placeable p, int sizeSlots,
                                   int windowStartMin, int windowEndMin, Axis axis, String name) {
        IntVar dev = model.newIntVar(0, axis.horizonSlots, name);
        for (Map.Entry<Integer, BoolVar> de : p.inDay.entrySet()) {
            int dayBase = de.getKey() * SLOTS_PER_DAY;
            int wLo = dayBase + windowStartMin / GRID;
            // Spätester Start, bei dem der Block noch im Fenster endet.
            int wHi = windowEndMin > windowStartMin
                    ? Math.max(wLo, dayBase + windowEndMin / GRID - sizeSlots)
                    : wLo;
            model.addGreaterOrEqual(
                    LinearExpr.newBuilder().addTerm(dev, 1).addTerm(p.start, 1).build(), wLo)
                 .onlyEnforceIf(de.getValue());
            model.addGreaterOrEqual(
                    LinearExpr.newBuilder().addTerm(dev, 1).addTerm(p.start, -1).build(), -wHi)
                 .onlyEnforceIf(de.getValue());
        }
        model.addEquality(dev, 0).onlyEnforceIf(p.present.not());
        return dev;
    }

    /**
     * Hält einen Block auf seiner bisherigen Lage — mit einer Totzone statt einer reinen
     * Wegstrecken-Strafe.
     *
     * Vorher war das ein einziger linearer Term mit {@link #W_MOVE} pro Slot. Die Rechnung ging
     * nicht auf: eine Viertelstunde Verschiebung kostete 3, während der Leistungshoch-Term dafür
     * {@code W_PEAK * prio = 2*prio} PRO SLOT ausgeben durfte. Ab Priorität 2 verschob er einen
     * längst liegenden Block also mit Gewinn — derselbe Projektblock stand ohne jede Änderung am
     * Bestand einmal um 08:00 und im nächsten Lauf um 08:15. Ein größeres W_MOVE hätte nur die
     * Schwelle verschoben; falsch war die FORM.
     *
     * Deshalb zusätzlich ein fester Preis dafür, sich überhaupt zu bewegen. Die Höhe ist gegen die
     * übrigen Gewichte gerechnet:
     * <ul>
     *   <li>{@code 400 > W_PEAK * prio_max * maxAbweichung = 2*5*36 = 360} — das Leistungshoch
     *       kann einen liegenden Block nie mehr allein bewegen. Damit ist das Springen weg.</li>
     *   <li>{@code 400 > W_URGENCY * 3 * 1 * SLOTS_PER_DAY = 288} für den Regelfall (Prio 3, keine
     *       Deadline) — "früher ist besser" wirft einen fertigen Plan nicht mehr um.</li>
     *   <li>{@code 400 < W_LATE * prio * SLOTS_PER_DAY >= 3840} — eine Deadline erzwingt die
     *       Bewegung weiterhin, immer. Genau das soll sie auch.</li>
     * </ul>
     * Die Wegstrecke bleibt zusätzlich im Ziel: muss ein Block weichen, soll er möglichst nah an
     * seiner alten Lage landen und nicht irgendwo.
     */
    private void addStabilityTerm(CpModel model, Placeable p, Integer previousSlot, Axis axis,
                                  List<IntVar> qVars, List<Long> qWeights, String name) {
        if (previousSlot == null) return;
        p.previousSlot = previousSlot;

        // abs() verträgt kein Enforcement-Literal, deshalb wird die Differenz vorher gegated.
        IntVar diff = model.newIntVar(-axis.horizonSlots, axis.horizonSlots, "diff_" + name);
        model.addEquality(diff, LinearExpr.newBuilder().addTerm(p.start, 1).add(-previousSlot).build())
             .onlyEnforceIf(p.present);
        model.addEquality(diff, 0).onlyEnforceIf(p.present.not());

        IntVar move = model.newIntVar(0, axis.horizonSlots, "move_" + name);
        model.addAbsEquality(move, diff);

        BoolVar moved = model.newBoolVar("moved_" + name);
        model.addEquality(move, 0).onlyEnforceIf(moved.not());
        model.addGreaterOrEqual(move, 1).onlyEnforceIf(moved);

        qVars.add(moved);
        qWeights.add(W_MOVE_FIXED);
        qVars.add(move);
        qWeights.add(W_MOVE);
    }

    /**
     * Bisherige Platzierungen, damit der Stabilitätsterm etwas hat, woran er sich festhalten kann.
     *
     * Die Anker eines Tasks hängen am Chunk-INDEX. Das trägt nur, solange die Zerlegung dieselbe
     * geblieben ist: hat sich die Zahl der Blöcke geändert (Aufgabe verlängert, teilweise
     * erledigt, Chunk-Grenzen in den Einstellungen verstellt), zeigt Index i auf einen ganz
     * anderen Block als beim letzten Mal. Früher korrigierte sich das von selbst — der Anker war
     * mit Gewicht 3 pro Slot billig genug, um ihn zu ignorieren. Mit der Totzone
     * ({@link #W_MOVE_FIXED}) ist er das nicht mehr: ein falsch zugeordneter Anker würde die
     * Aufgabe aktiv an eine unsinnige Lage nageln. Deshalb fallen die Anker eines Tasks komplett
     * weg, sobald die Blockzahl nicht mehr passt — lieber gar kein Anker als ein falscher.
     */
    private Map<String, Integer> previousStartSlots(ScheduleInput input, Axis axis,
                                                    List<TaskChunk> chunks) {
        Map<Long, Long> chunkCountPerTask = chunks.stream()
                .filter(c -> c.task != null && c.task.getId() != null)
                .collect(Collectors.groupingBy(c -> c.task.getId(), Collectors.counting()));

        Map<String, Integer> out = new HashMap<>();
        Map<Long, List<CalendarEvent>> byTask  = new HashMap<>();
        Map<String, List<CalendarEvent>> byHabitWeek = new HashMap<>();
        Map<String, List<CalendarEvent>> byProjectWeek = new HashMap<>();

        for (CalendarEvent ev : nz(input.getPreviousScheduledEvents())) {
            if (ev.getStartTime() == null) continue;
            if (ev.getRelatedTask() != null) {
                byTask.computeIfAbsent(ev.getRelatedTask().getId(), k -> new ArrayList<>()).add(ev);
            } else if (ev.getRelatedProject() != null) {
                LocalDate week = ev.getStartTime().toLocalDate()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                byProjectWeek.computeIfAbsent(ev.getRelatedProject().getId() + ":" + week,
                        k -> new ArrayList<>()).add(ev);
            } else if (ev.getRelatedHabit() != null) {
                out.put("habit:" + ev.getRelatedHabit().getId() + ":" + ev.getStartTime().toLocalDate(),
                        axis.floorSlot(ev.getStartTime()));
                LocalDate week = ev.getStartTime().toLocalDate()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                byHabitWeek.computeIfAbsent(ev.getRelatedHabit().getId() + ":" + week,
                        k -> new ArrayList<>()).add(ev);
            } else if (ev.getRelatedWorkout() != null) {
                out.put("workout:" + ev.getRelatedWorkout().getId(), axis.floorSlot(ev.getStartTime()));
            }
        }
        byTask.forEach((taskId, evs) -> {
            // Nur ankern, wenn die Zerlegung dieselbe geblieben ist — siehe Javadoc oben.
            long jetzt = chunkCountPerTask.getOrDefault(taskId, 0L);
            if (jetzt != evs.size()) return;

            evs.sort(Comparator.comparing(CalendarEvent::getStartTime));
            for (int i = 0; i < evs.size(); i++) {
                out.put("task:" + taskId + ":" + i, axis.floorSlot(evs.get(i).getStartTime()));
            }
        });
        // Anker für flexible Habits: pro Habit und ISO-Woche chronologisch durchnummeriert.
        byHabitWeek.forEach((groupKey, evs) -> {
            evs.sort(Comparator.comparing(CalendarEvent::getStartTime));
            for (int i = 0; i < evs.size(); i++) {
                out.put("habitweek:" + groupKey + ":" + i, axis.floorSlot(evs.get(i).getStartTime()));
            }
        });
        // Anker für Projekt-Sessions: identisches Schema, pro Projekt und ISO-Woche.
        byProjectWeek.forEach((groupKey, evs) -> {
            evs.sort(Comparator.comparing(CalendarEvent::getStartTime));
            for (int i = 0; i < evs.size(); i++) {
                out.put("projectweek:" + groupKey + ":" + i, axis.floorSlot(evs.get(i).getStartTime()));
            }
        });
        return out;
    }

    // =========================================================================
    // LÖSUNG AUSLESEN
    // =========================================================================

    private SolveOutcome extract(CpSolver solver, CpSolverStatus status, List<TaskChunk> chunks,
                                 List<HabitSlot> habitSlots, List<WorkoutSession> flexibleWorkouts,
                                 Map<Long, Placeable> workoutPlaceables,
                                 List<ProjectSlot> projectSlots, Axis axis, LocalDateTime cutoff) {
        List<ScheduledItem> items  = new ArrayList<>();
        List<AtRiskItem>    atRisk = new ArrayList<>();

        // --- Tasks: platzierte Chunks je Task sammeln, angrenzende verschmelzen, dann nummerieren ---
        Map<Long, List<TaskChunk>> byTask = chunks.stream()
                .collect(Collectors.groupingBy(c -> c.task.getId(), LinkedHashMap::new, Collectors.toList()));

        for (List<TaskChunk> group : byTask.values()) {
            Task task = group.get(0).task;
            List<int[]> placed = new ArrayList<>();   // [startSlot, realMinutes]
            int missingMinutes = 0;

            for (TaskChunk c : group) {
                if (c.placeable != null && Boolean.TRUE.equals(solver.booleanValue(c.placeable.present))) {
                    placed.add(new int[]{ (int) solver.value(c.placeable.start), c.durationMinutes });
                } else {
                    missingMinutes += c.durationMinutes;
                }
            }
            // Genau EIN Eintrag pro Task. Vorher konnte ein Task mit drei Chunks bis zu vier
            // Meldungen erzeugen (einmal "kein Platz" plus je eine pro Block hinter der Deadline);
            // in der Oberfläche las sich das wie vier verschiedene Probleme.
            //
            // Ein überfälliger Task wird auch dann gemeldet, wenn jeder Block untergebracht ist:
            // seine Deadline ist bereits gerissen, das bleibt der wichtigere Befund. Seit die
            // Deadline das Fenster hart begrenzt (taskBounds), ist der Nachhol-Fall die einzige
            // Lage, in der überhaupt noch ein Block hinter einer Deadline stehen kann.
            AtRiskReason reason = null;
            if (task.getDeadline() != null && task.getDeadline().isBefore(cutoff)) {
                reason = AtRiskReason.PAST_DEADLINE;
            } else if (missingMinutes > 0) {
                reason = task.getDeadline() != null
                        ? AtRiskReason.WOULD_MISS_DEADLINE   // Deadline noch vor uns, passt aber nicht mehr davor
                        : AtRiskReason.NO_ROOM;
            }
            if (reason != null) {
                atRisk.add(AtRiskItem.forTask(task.getId(), task.getTitle(), missingMinutes, reason));
            }
            if (placed.isEmpty()) continue;

            placed.sort(Comparator.comparingInt(a -> a[0]));

            // Blöcke werden bewusst NICHT zusammengefasst, auch wenn sie exakt aneinandergrenzen.
            // Bei leerem Kalender packt der Solver alle Chunks hintereinander; würde man die dann
            // verschmelzen, wäre vom Chunking genau im häufigsten Fall nichts mehr zu sehen.
            // Reclaim zeigt ebenfalls einzelne Sessions statt eines Monolithen.
            for (int i = 0; i < placed.size(); i++) {
                LocalDateTime start = axis.timeOf(placed.get(i)[0]);
                LocalDateTime end   = start.plusMinutes(placed.get(i)[1]);   // echte Dauer

                ScheduledItem item = new ScheduledItem();
                item.setTask(task);
                item.setStartTime(start);
                item.setEndTime(end);
                item.setType(ScheduledItemType.TASK);
                item.setChunkIndex(i + 1);
                item.setChunkCount(placed.size());
                items.add(item);
            }
        }

        // --- Habits ---
        for (HabitSlot s : habitSlots) {
            if (s.placeable == null || !Boolean.TRUE.equals(solver.booleanValue(s.placeable.present))) {
                atRisk.add(AtRiskItem.forHabit(s.habit.getId(), s.habit.getName(),
                        s.durationMinutes, AtRiskReason.NO_ROOM));
                continue;
            }
            LocalDateTime start = axis.timeOf((int) solver.value(s.placeable.start));
            ScheduledItem item = new ScheduledItem();
            item.setHabit(s.habit);
            // Nur flexible Slots haben eine Wochenquote; feste Wochentags-Habits (weekStart null)
            // hängen ohnehin an ihrem Tag — die werden über targetDate gebucht.
            item.setTargetWeekStart(s.weekStart);
            item.setTargetDate(s.legacyDate);
            item.setStartTime(start);
            item.setEndTime(start.plusMinutes(s.durationMinutes));   // echte Dauer, nicht die aufgerundete
            item.setType(ScheduledItemType.HABIT);
            items.add(item);
        }

        // --- Flexible Workouts ---
        for (WorkoutSession w : flexibleWorkouts) {
            Placeable p = workoutPlaceables.get(w.getId());
            if (p == null || !Boolean.TRUE.equals(solver.booleanValue(p.present))) continue;

            LocalDateTime start = axis.timeOf((int) solver.value(p.start));
            LocalDateTime end   = start.plusMinutes(p.realMinutes);
            w.setStartTime(start);
            w.setEndTime(end);
            workoutSessionRepository.save(w);

            ScheduledItem item = new ScheduledItem();
            item.setWorkoutSession(w);
            item.setStartTime(start);
            item.setEndTime(end);
            item.setType(ScheduledItemType.WORKOUT);
            items.add(item);
        }

        // --- Projekt-Sessions ---
        // Kein AtRiskItem: eine nicht platzierbare Session fällt still weg — sie ist Wochenquote,
        // keine Zusage, und steht nächste Woche wieder zur Verfügung (wie beim flexiblen Workout).
        for (ProjectSlot s : projectSlots) {
            if (s.placeable == null || !Boolean.TRUE.equals(solver.booleanValue(s.placeable.present))) continue;

            LocalDateTime start = axis.timeOf((int) solver.value(s.placeable.start));
            ScheduledItem item = new ScheduledItem();
            item.setProject(s.project);
            item.setStartTime(start);
            item.setEndTime(start.plusMinutes(s.durationMinutes));   // echte Dauer, nicht die aufgerundete
            item.setType(ScheduledItemType.PROJECT);
            item.setTargetWeekStart(s.weekStart);
            items.add(item);
        }

        return new SolveOutcome(status, items, atRisk);
    }

    // =========================================================================
    // INPUT
    // =========================================================================

    private ScheduleInput collectScheduleInput(Long userId, LocalDate startDate, LocalDate endDate,
                                               LocalDateTime cutoff) {
        ScheduleInput input = new ScheduleInput();
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end   = endDate.atTime(23, 59, 59);

        input.setTasks(taskService.getSchedulableTasks(userId));
        // Übersprungene Blöcke sperren keine Zeit mehr — das ist der halbe Sinn des
        // Überspringens. Sie werden weiter unten getrennt eingesammelt, weil sie trotzdem auf
        // das Wochenpensum zählen.
        List<CalendarEvent> fixed = nz(calendarEventService.getFixedEvents(userId, start, end));
        input.setFixedEvents(fixed.stream()
                .filter(e -> e.getSkippedAt() == null)
                .collect(Collectors.toList()));

        // Dieselben Blöcke, aber ohne Horizontgrenze — siehe ScheduleInput#pinnedCommitments.
        // Ein Block, den der Nutzer weit in die Zukunft gezogen hat, muss seinem Item weiter
        // gutgeschrieben werden, sonst bekommt es hier im Horizont einen zweiten.
        List<CalendarEvent> pinned = nz(calendarEventService.getPinnedScheduledEventsFrom(userId, start));
        input.setPinnedCommitments(pinned.stream()
                .filter(e -> e.getSkippedAt() == null)
                .collect(Collectors.toList()));
        input.setHabits(habitRepository.findHabitsActiveInRange(userId, startDate, endDate));
        input.setCourseSchedules(courseScheduleRepository.findByUserId(userId));
        input.setProjects(projectRepository.findByUserIdAndStatusIn(userId, SCHEDULABLE_PROJECT_STATUS));

        // Vor dem Löschen einsammeln: der Stabilitätsterm braucht die bisherigen Platzierungen.
        // PROJECT muss hier mit drin sein — sonst sind eingefrorene Projektblöcke weder in
        // fixedEvents (sie sind isFixed=false) noch in frozenEvents, ihre Zeit würde nicht
        // blockiert und der Stabilitätsanker fehlte.
        List<CalendarEvent> previous = nz(calendarEventRepository
                .findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                        userId,
                        List.of(EventType.TASK, EventType.HABIT, EventType.WORKOUT, EventType.PROJECT),
                        false, start, end));

        // Bereits begonnene Blöcke werden eingefroren statt neu geplant. Sie taugen deshalb auch
        // nicht als Stabilitätsanker — ein Anker in der Vergangenheit würde den zugehörigen neuen
        // Block gegen die Jetzt-Grenze ziehen.
        // Erledigte Blöcke gelten unabhängig vom Umplanzeitpunkt als eingefroren: sie sperren
        // ihre Zeit weiter, taugen aber nicht als Stabilitätsanker für einen neuen Block.
        // Übersprungene Blöcke gehören in keinen der beiden Töpfe: sie sollen weder Zeit sperren
        // noch als Stabilitätsanker dienen. Ihr einziger Beitrag ist die verbrauchte Wochenquote.
        //
        // Aus BEIDEN Quellen: wer einen Block erst verschiebt und dann überspringt, hat ihn
        // gepinnt — der steckt dann in pinned und nicht in previous. Nur aus previous gelesen,
        // fiele seine Wochenquote unter den Tisch und die Woche bekäme doch wieder Ersatz.
        // Gepinnte Blöcke kommen aus der unbeschnittenen Liste, damit das auch gilt, wenn der
        // Block außerhalb des Horizonts liegt.
        input.setSkippedEvents(dedupById(concat(pinned, fixed, previous)).stream()
                .filter(e -> e.getStartTime() != null && e.getSkippedAt() != null)
                .collect(Collectors.toList()));

        Map<Boolean, List<CalendarEvent>> split = previous.stream()
                .filter(e -> e.getStartTime() != null && e.getSkippedAt() == null)
                .collect(Collectors.partitioningBy(
                        e -> e.getCompletedAt() != null || e.getStartTime().isBefore(cutoff)));
        input.setFrozenEvents(split.get(true));
        input.setPreviousScheduledEvents(split.get(false));

        // Ein manuell verschobenes Workout hat ein gepinntes Kalender-Event. Es bleibt in der
        // Datenbank zwar "flexibel", darf aber nicht mehr umgeplant werden — sonst zieht der
        // Solver es sofort wieder weg und der Drag-and-Drop hätte keine Wirkung. Für ein bereits
        // gelaufenes Workout gilt dasselbe. pinnedCommitments muss hier mit hinein: ein Workout,
        // das der Nutzer über den Horizont hinaus gezogen hat, gälte sonst wieder als frei
        // planbar und bekäme im Horizont eine zweite Einheit.
        Set<Long> pinnedWorkoutIds = concat(input.getPinnedCommitments(), input.getFixedEvents(),
                                            input.getFrozenEvents()).stream()
                .filter(e -> e.getRelatedWorkout() != null)
                .map(e -> e.getRelatedWorkout().getId())
                .collect(Collectors.toSet());

        List<WorkoutSession> inRange = workoutSessionRepository.findByUserIdAndStartTimeBetween(userId, start, end);
        List<WorkoutSession> flexible = workoutSessionRepository.findByUserIdAndIsFlexibleTrue(userId).stream()
                .filter(w -> !pinnedWorkoutIds.contains(w.getId()))
                // Übersprungene Einheiten bleiben als Zeile stehen, damit der Plan sie weiter auf
                // sein Wochenpensum zählt und keine neue Einheit als Ersatz entsteht — geplant
                // werden sie aber nicht mehr.
                .filter(w -> !Boolean.TRUE.equals(w.getIsSkipped()))
                .filter(w -> isWorkoutRelevantToRange(w, startDate, endDate))
                .collect(Collectors.toList());
        Set<Long> flexibleIds = flexible.stream().map(WorkoutSession::getId).collect(Collectors.toSet());

        input.setFixedWorkouts(inRange.stream()
                .filter(w -> !flexibleIds.contains(w.getId()))
                .collect(Collectors.toList()));
        input.setFlexibleWorkouts(flexible);

        log.debug("Input: {} Tasks, {} fixe Events, {} Habits, {} fixe Workouts, {} flexible Workouts",
                input.getTasks().size(), input.getFixedEvents().size(), input.getHabits().size(),
                input.getFixedWorkouts().size(), input.getFlexibleWorkouts().size());
        return input;
    }

    private boolean isWorkoutRelevantToRange(WorkoutSession w, LocalDate startDate, LocalDate endDate) {
        if (w.getTargetWeekStart() != null) {
            LocalDate weekEnd = w.getTargetWeekStart().plusDays(6);
            return !w.getTargetWeekStart().isAfter(endDate) && !weekEnd.isBefore(startDate);
        }
        if (w.getStartTime() != null) {
            LocalDate d = w.getStartTime().toLocalDate();
            return !d.isBefore(startDate) && !d.isAfter(endDate);
        }
        return false;
    }

    // =========================================================================
    // PERSISTENZ
    // =========================================================================

    private void saveScheduleToDatabase(Long userId, List<ScheduledItem> scheduled) {
        User user = userService.findById(userId);

        // Bewusst save() je Block statt saveAll: CalendarEvent.id ist GenerationType.IDENTITY,
        // und damit schaltet Hibernate das JDBC-Batching für Inserts komplett ab — es muss jedes
        // INSERT einzeln ausführen, um den generierten Schlüssel zu lesen. saveAll wäre hier
        // dieselbe Schleife unter anderem Namen.
        for (ScheduledItem item : scheduled) {
            CalendarEvent ev = buildEvent(user, item);
            if (ev != null) calendarEventRepository.save(ev);
        }
    }

    /**
     * Gleicht den vorhandenen Bestand gegen den neuen Plan ab, statt alles zu löschen und neu zu
     * schreiben.
     *
     * Der alte Weg war ein voller Austausch: erst jeden generierten Block im Fenster weg, dann
     * alle neu einfügen. Bei einem Lauf, an dem sich gar nichts geändert hat — und das ist der
     * Regelfall, seit der Stabilitätsanker greift — kostete das mehrere hundert Anweisungen, um
     * denselben Kalender noch einmal hinzuschreiben.
     *
     * Der zweite, wichtigere Grund ist die IDENTITÄT: beim Austausch bekam jeder Block bei jedem
     * Lauf eine neue ID. Für das Frontend war damit nach jeder Neuplanung jeder Termin ein
     * fremdes Objekt — es konnte gar nicht erkennen, dass sich nichts geändert hatte.
     *
     * Zugeordnet wird über eine Gruppe (welches Item, welche Woche) und darin über die
     * chronologische Position. Ein Schlüssel über die Startzeit wäre falsch: gerade der
     * verschobene Block soll ja wiedererkannt werden.
     */
    private void reconcileScheduledEvents(Long userId, LocalDateTime cutoff, LocalDateTime bis,
                                          List<ScheduledItem> scheduled) {
        List<CalendarEvent> vorhanden = nz(calendarEventRepository
                .findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                        userId,
                        List.of(EventType.TASK, EventType.HABIT, EventType.WORKOUT, EventType.PROJECT),
                        false, cutoff, bis))
                .stream()
                // Erledigte und übersprungene Blöcke sind kein Planungsstoff — sie bleiben
                // unangetastet stehen, genau wie beim Aufräumen.
                .filter(e -> e.getCompletedAt() == null && e.getSkippedAt() == null)
                .collect(Collectors.toList());

        Map<String, List<CalendarEvent>> altNachGruppe = vorhanden.stream()
                .collect(Collectors.groupingBy(this::gruppenSchluessel, LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, List<ScheduledItem>> neuNachGruppe = scheduled.stream()
                .filter(i -> i.getType() != ScheduledItemType.CLASS)
                .collect(Collectors.groupingBy(this::gruppenSchluessel, LinkedHashMap::new,
                        Collectors.toList()));

        User user = userService.findById(userId);
        List<Long> zuLoeschen = new ArrayList<>();
        List<CalendarEvent> zuAendern = new ArrayList<>();

        Set<String> alleGruppen = new LinkedHashSet<>();
        alleGruppen.addAll(altNachGruppe.keySet());
        alleGruppen.addAll(neuNachGruppe.keySet());

        for (String gruppe : alleGruppen) {
            List<CalendarEvent> alt = altNachGruppe.getOrDefault(gruppe, List.of());
            List<ScheduledItem> neu = neuNachGruppe.getOrDefault(gruppe, List.of());
            alt = new ArrayList<>(alt);
            neu = new ArrayList<>(neu);
            alt.sort(Comparator.comparing(CalendarEvent::getStartTime));
            neu.sort(Comparator.comparing(ScheduledItem::getStartTime));

            for (int i = 0; i < Math.max(alt.size(), neu.size()); i++) {
                if (i >= neu.size()) {
                    zuLoeschen.add(alt.get(i).getId());
                } else if (i >= alt.size()) {
                    CalendarEvent ev = buildEvent(user, neu.get(i));
                    if (ev != null) calendarEventRepository.save(ev);
                } else if (uebernimmAenderungen(alt.get(i), neu.get(i))) {
                    zuAendern.add(alt.get(i));
                }
            }
        }

        if (!zuAendern.isEmpty())  calendarEventRepository.saveAll(zuAendern);
        if (!zuLoeschen.isEmpty()) calendarEventRepository.deleteAllByIdInBatch(zuLoeschen);
    }

    /**
     * Was einen Block über Läufe hinweg identifiziert — bewusst OHNE die Uhrzeit.
     *
     * Bei Gewohnheiten und Projektzeit gehört die Zielwoche bzw. der Zieltag dazu: derselbe Habit
     * hat pro Woche mehrere Ausführungen, und die dürfen nicht miteinander verwechselt werden.
     */
    private String gruppenSchluessel(CalendarEvent e) {
        return switch (e.getEventType()) {
            case TASK    -> "task:" + id(e.getRelatedTask() != null ? e.getRelatedTask().getId() : null);
            case HABIT   -> "habit:" + id(e.getRelatedHabit() != null ? e.getRelatedHabit().getId() : null)
                    + ":" + e.getTargetWeekStart() + ":" + e.getTargetDate();
            case WORKOUT -> "workout:" + id(e.getRelatedWorkout() != null ? e.getRelatedWorkout().getId() : null);
            case PROJECT -> "project:" + id(e.getRelatedProject() != null ? e.getRelatedProject().getId() : null)
                    + ":" + e.getTargetWeekStart();
            default      -> "sonst:" + e.getEventType();
        };
    }

    private String gruppenSchluessel(ScheduledItem i) {
        return switch (i.getType()) {
            case TASK    -> "task:" + id(i.getTask() != null ? i.getTask().getId() : null);
            case HABIT   -> "habit:" + id(i.getHabit() != null ? i.getHabit().getId() : null)
                    + ":" + i.getTargetWeekStart() + ":" + i.getTargetDate();
            case WORKOUT -> "workout:" + id(i.getWorkoutSession() != null ? i.getWorkoutSession().getId() : null);
            case PROJECT -> "project:" + id(i.getProject() != null ? i.getProject().getId() : null)
                    + ":" + i.getTargetWeekStart();
            default      -> "sonst:" + i.getType();
        };
    }

    private String id(Long value) {
        return value == null ? "-" : value.toString();
    }

    /**
     * Überträgt Zeiten und Titel auf den bestehenden Block. Liefert true, wenn sich wirklich etwas
     * geändert hat — nur dann muss gespeichert werden, und nur so schreibt ein Lauf ohne Änderung
     * tatsächlich nichts.
     */
    private boolean uebernimmAenderungen(CalendarEvent alt, ScheduledItem neu) {
        String titel = titelFuer(neu);
        boolean geaendert = !Objects.equals(alt.getStartTime(), neu.getStartTime())
                || !Objects.equals(alt.getEndTime(), neu.getEndTime())
                || !Objects.equals(alt.getTitle(), titel);
        if (!geaendert) return false;

        alt.setStartTime(neu.getStartTime());
        alt.setEndTime(neu.getEndTime());
        alt.setTitle(titel);
        return true;
    }

    private String titelFuer(ScheduledItem item) {
        return switch (item.getType()) {
            case TASK    -> chunkTitle(item);
            case HABIT   -> item.getHabit().getName();
            case WORKOUT -> item.getWorkoutSession().getName();
            case PROJECT -> item.getProject().getName();
            default      -> null;
        };
    }

    /** Baut den Kalendereintrag zu einem geplanten Item. Null, wenn der Typ nichts schreibt. */
    private CalendarEvent buildEvent(User user, ScheduledItem item) {
        {
            CalendarEvent ev = new CalendarEvent();
            ev.setUser(user);
            ev.setStartTime(item.getStartTime());
            ev.setEndTime(item.getEndTime());
            ev.setIsFixed(false);

            switch (item.getType()) {
                case TASK -> {
                    ev.setTitle(chunkTitle(item));
                    ev.setDescription(item.getTask().getDescription());
                    ev.setEventType(EventType.TASK);
                    ev.setRelatedTask(item.getTask());
                    ev.setColor(getColorForTask(item.getTask()));
                }
                case HABIT -> {
                    ev.setTitle(item.getHabit().getName());
                    ev.setDescription(item.getHabit().getDescription());
                    ev.setEventType(EventType.HABIT);
                    ev.setRelatedHabit(item.getHabit());
                    ev.setColor("#4CAF50");
                    // Wie beim Projektblock: bleibt auf seine Woche gebucht, auch wenn der
                    // Nutzer ihn später in eine andere zieht. Bei Wochentags-Gewohnheiten gibt
                    // es keine Woche, dort trägt der Ursprungstag dieselbe Rolle.
                    ev.setTargetWeekStart(item.getTargetWeekStart());
                    ev.setTargetDate(item.getTargetDate());
                }
                case WORKOUT -> {
                    ev.setTitle(item.getWorkoutSession().getName());
                    ev.setDescription(item.getWorkoutSession().getDescription());
                    ev.setEventType(EventType.WORKOUT);
                    ev.setRelatedWorkout(item.getWorkoutSession());
                    ev.setColor("#FF5722");
                }
                case PROJECT -> {
                    ev.setTitle(item.getProject().getName());
                    ev.setDescription(item.getProject().getDescription());
                    ev.setEventType(EventType.PROJECT);
                    ev.setRelatedProject(item.getProject());
                    ev.setColor(getColorForSpaceType(SpaceType.PROJECTS));
                    // Die Woche, deren Pensum dieser Block abdeckt — bleibt auch dann stehen,
                    // wenn der Nutzer ihn später in eine andere Woche zieht.
                    ev.setTargetWeekStart(item.getTargetWeekStart());
                }
                case CLASS -> {
                    // Abgeleitet aus dem Stundenplan, deshalb kein relatedXy: die Identität ergibt
                    // sich aus Typ, isFixed und Startzeit — genau das, worauf clearClassEvents filtert.
                    Course course = item.getCourseSchedule().getCourse();
                    ev.setTitle(course != null && course.getName() != null ? course.getName() : "Vorlesung");
                    ev.setDescription(course != null ? course.getInstructor() : null);
                    ev.setLocation(item.getCourseSchedule().getLocation());
                    ev.setEventType(EventType.CLASS);
                    ev.setColor(getColorForCourse(course));
                }
                default -> { return null; }
            }

            return ev;
        }
    }

    private String chunkTitle(ScheduledItem item) {
        String base = item.getTask().getTitle();
        if (item.getChunkCount() != null && item.getChunkCount() > 1) {
            return base + " (" + item.getChunkIndex() + "/" + item.getChunkCount() + ")";
        }
        return base;
    }

    /**
     * Schreibt die Gesamtspanne pro Task zurück. Der alte Code rief scheduleTask innerhalb der
     * Item-Schleife auf — mit mehreren Chunks blieben dort die Zeiten des LETZTEN Blocks stehen.
     * Tasks, die gar nicht platziert werden konnten, bekommen ihre Zeiten gelöscht, sonst bleiben
     * Geisterwerte aus einem früheren Lauf hängen.
     *
     * Mitgezählt werden auch gepinnte und eingefrorene Blöcke. Ein Task, dessen Zeit vollständig
     * dadurch abgedeckt ist, erzeugt keine neuen Chunks mehr — ohne diese Quelle stünde er in der
     * Task-Liste als "ungeplant", obwohl sein Block sichtbar im Kalender liegt.
     */
    private void writeBackTaskSpans(SolveOutcome outcome, List<Task> allTasks,
                                    List<CalendarEvent> committed) {
        Map<Long, LocalDateTime[]> spans = new LinkedHashMap<>();
        for (ScheduledItem i : outcome.getItems()) {
            if (i.getTask() == null) continue;
            mergeSpan(spans, i.getTask().getId(), i.getStartTime(), i.getEndTime());
        }

        Set<Long> known = nz(allTasks).stream().map(Task::getId).collect(Collectors.toSet());
        for (CalendarEvent ev : committedTaskEvents(committed)) {
            // Nur Tasks aus diesem Lauf: für abgeschlossene Tasks mit altem Block wäre ein
            // zurückgeschriebener Termin bestenfalls verwirrend.
            if (known.contains(ev.getRelatedTask().getId())) {
                mergeSpan(spans, ev.getRelatedTask().getId(), ev.getStartTime(), ev.getEndTime());
            }
        }

        spans.forEach((id, span) -> taskService.scheduleTask(id, span[0], span[1]));

        for (Task t : nz(allTasks)) {
            if (!spans.containsKey(t.getId())
                    && (t.getScheduledStartTime() != null || t.getScheduledEndTime() != null)) {
                taskService.clearSchedule(t.getId());
            }
        }
    }

    private void mergeSpan(Map<Long, LocalDateTime[]> spans, Long taskId,
                           LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return;
        spans.merge(taskId, new LocalDateTime[]{ start, end }, (a, b) -> new LocalDateTime[]{
                a[0].isBefore(b[0]) ? a[0] : b[0],
                a[1].isAfter(b[1])  ? a[1] : b[1] });
    }

    /** Gepinnte und eingefrorene Blöcke, die an einem Task hängen. */
    private List<CalendarEvent> committedTaskEvents(List<CalendarEvent> committed) {
        return nz(committed).stream()
                .filter(e -> e.getRelatedTask() != null && e.getRelatedTask().getId() != null)
                .filter(e -> e.getStartTime() != null && e.getEndTime() != null)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // GEWICHTE & HELFER
    // =========================================================================

    /**
     * Wie nah die Deadline ist, als Stufe 0 (fern oder keine) bis 4 (heute oder überfällig).
     *
     * Eine Stelle für beide Verwendungen — das Drop-Gewicht in Phase 1 und die Reihenfolge in
     * Phase 2 —, damit "dringend" nicht an zwei Orten unterschiedlich definiert ist.
     *
     * @param today Bezugstag des Laufs (startDate), nicht LocalDate.now(): sonst hinge die Stufe
     *              an einer anderen Uhr als das übrige Modell und wäre nicht testbar.
     */
    private int deadlineBucket(Task task, LocalDate today) {
        if (task.getDeadline() == null) return 0;
        long days = ChronoUnit.DAYS.between(today, task.getDeadline().toLocalDate());
        if (days <= 0) return 4;
        if (days == 1) return 3;
        if (days <= 3) return 2;
        if (days <= 7) return 1;
        return 0;
    }

    /** Faktor auf das Drop-Gewicht je Deadline-Stufe. */
    private static final long[] DROP_URGENCY = { 1, 2, 4, 6, 8 };

    /**
     * Faktor auf die Dringlichkeit ("früher ist besser") je Deadline-Stufe.
     *
     * Viel flacher als {@link #DROP_URGENCY}, und das mit Absicht: hier geht es nur noch um die
     * Reihenfolge unter Aufgaben, die ihre Deadline ohnehin alle halten. Ohne den Faktor war das
     * ein glatter Gleichstand — zwei gleich wichtige Aufgaben, eine morgen und eine nächsten
     * Monat fällig, landeten in beliebiger Reihenfolge.
     *
     * Die Obergrenze ist bewusst 3: das Leistungshoch darf einen Block weiterhin nicht auf einen
     * anderen Tag ziehen (Abweichung höchstens 2*prio*36 = 72*prio gegen einen Tagessprung von
     * mindestens 1*prio*96), und die Deadline-Strafe W_LATE = 40 bleibt um Größenordnungen
     * stärker als jede Dringlichkeit.
     */
    private static final long[] PLACEMENT_URGENCY = { 1, 1, 2, 2, 3 };

    /**
     * Wie wichtig es ist, diesen Task überhaupt unterzubringen (Phase-1-Gewicht).
     *
     * Die Nähe der Deadline ist ein FAKTOR, kein Summand. Addiert kippte die Rangfolge: ein
     * P1-Task von heute kam auf 100+1000 = 1100 und schlug damit einen P5-Task von morgen mit
     * 500+500 = 1000 — eine Nebensächlichkeit verdrängte etwas Wichtiges, nur weil sie einen Tag
     * früher fällig war. Multiplikativ ist die Ordnung garantiert inversionsfrei: bei gleicher
     * Deadline gewinnt immer die höhere Priorität, bei gleicher Priorität immer die nähere
     * Deadline.
     */
    private long calculateTaskWeight(Task task, LocalDate today) {
        int priority = Math.min(5, Math.max(1, nz(task.getPriority(), 3)));
        // Der Sockel hebt das ganze Aufgabenband über das der wiederkehrenden Items — siehe die
        // Invariante an W_DROP_PROJECT. Er ist ein Summand und kein Faktor, damit die
        // inversionsfreie Ordnung INNERHALB der Aufgaben unverändert bleibt.
        return W_DROP_TASK_BASE + priority * 100L * DROP_URGENCY[deadlineBucket(task, today)];
    }

    private long calculateHabitWeight(Habit habit) {
        return (long) nz(habit.getPriority(), 3) * W_DROP_HABIT_PRIO;
    }

    /**
     * Ungeplant ist ein Task nur, wenn für ihn nirgends ein Block im Kalender steht — weder ein
     * neu geplanter noch ein gepinnter oder bereits gelaufener.
     */
    private List<Task> findUnscheduledTasks(List<Task> all, List<ScheduledItem> scheduled,
                                            List<CalendarEvent> committed) {
        Set<Long> ids = scheduled.stream()
                .filter(i -> i.getTask() != null)
                .map(i -> i.getTask().getId())
                .collect(Collectors.toSet());
        committedTaskEvents(committed).forEach(e -> ids.add(e.getRelatedTask().getId()));
        return nz(all).stream().filter(t -> !ids.contains(t.getId())).collect(Collectors.toList());
    }

    @SafeVarargs
    private double calculateTotalHours(List<ScheduledItem>... lists) {
        long total = 0;
        for (List<ScheduledItem> list : lists)
            for (ScheduledItem item : list)
                total += ChronoUnit.MINUTES.between(item.getStartTime(), item.getEndTime());
        return total / 60.0;
    }

    /**
     * Das Leistungshoch als Minutenfenster, oder null wenn nicht eingestellt.
     * Die Grenzen entsprechen den gleichnamigen {@link HabitWindow}-Bereichen, damit
     * "Vormittag" für Habits und für Tasks dasselbe bedeutet.
     */
    private int[] peakWindow(UserPreferences p) {
        ProductivityPeakTime peak = p.getPeakProductivityTime();
        if (peak == null) return null;
        HabitWindow w = switch (peak) {
            case MORNING   -> HabitWindow.MORNING;
            case AFTERNOON -> HabitWindow.AFTERNOON;
            case EVENING   -> HabitWindow.EVENING;
        };
        return new int[]{ minuteOfDay(w.defaultStart()), minuteOfDay(w.defaultEnd()) };
    }

    private LocalTime workStart(UserPreferences p) {
        return p.getWorkdayStart() != null ? p.getWorkdayStart() : LocalTime.of(8, 0);
    }

    private LocalTime workEnd(UserPreferences p) {
        return p.getWorkdayEnd() != null ? p.getWorkdayEnd() : LocalTime.of(22, 0);
    }

    /**
     * Ende der Kernzeit — ab hier wird eine Aufgabe als "Abend" bestraft (siehe {@link #W_EVENING}).
     *
     * Nicht dasselbe wie das Arbeitsende: das Arbeitsende ist eine harte Grenze ("danach wird gar
     * nichts mehr geplant"), die Kernzeit eine weiche ("danach nur noch, wenn es sein muss").
     */
    private LocalTime coreHoursEnd(UserPreferences p) {
        return p.getCoreHoursEnd() != null ? p.getCoreHoursEnd() : DEFAULT_CORE_HOURS_END;
    }

    private static int minuteOfDay(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    /**
     * Minuten in Slots, AUFgerundet.
     *
     * Vorher stand an beiden Aufrufstellen eine gewöhnliche Ganzzahldivision durch {@link #GRID}.
     * Damit wurde jeder Wert von 1 bis 14 Minuten stillschweigend zu null: wer zehn Minuten Puffer
     * um seine Termine einstellte, bekam gar keinen, und die Blöcke klebten weiter aneinander.
     * Von außen sah das aus, als ignoriere der Scheduler die Einstellung — was er auch tat.
     *
     * Aufrunden statt abrunden, weil beides Mindestabstände sind: zehn Minuten Puffer werden so zu
     * einer Viertelstunde, also mindestens dem, was der Nutzer verlangt hat. Abzurunden hieße, ihm
     * weniger zu geben, als er eingestellt hat — und genau das war der Fehler.
     */
    private static int slotsAufgerundet(int minuten) {
        if (minuten <= 0) return 0;
        return (minuten + GRID - 1) / GRID;
    }

    private static int nz(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private static <T> List<T> nz(List<T> list) {
        return list != null ? list : List.of();
    }

    @SafeVarargs
    private static <T> List<T> concat(List<T>... lists) {
        List<T> out = new ArrayList<>();
        for (List<T> l : lists) out.addAll(nz(l));
        return out;
    }

    /**
     * Entfernt Doppelte nach Id, Reihenfolge bleibt erhalten.
     *
     * Nötig, seit die Buchhaltung aus mehreren überlappenden Quellen gespeist wird: ein Block kann
     * gleichzeitig in {@code pinnedCommitments} und (über die Skip-Liste) in {@code counted}
     * landen. Doppelt gezählt schrumpfte ein Task um die Minuten eines einzigen Blocks zweimal
     * und die Wochenquote einer Gewohnheit um zwei statt eine Ausführung.
     *
     * Events ohne Id (in Tests konstruiert) bleiben unangetastet, sonst fielen sie alle bis auf
     * eines weg.
     */
    private static List<CalendarEvent> dedupById(List<CalendarEvent> events) {
        List<CalendarEvent> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (CalendarEvent e : nz(events)) {
            if (e.getId() == null || seen.add(e.getId())) out.add(e);
        }
        return out;
    }

    /**
     * Ab wann neu geplant wird. Alles davor ist Geschichte und bleibt unangetastet; liegt der
     * Horizont komplett in der Zukunft, gibt es nichts einzufrieren und der Schnitt ist sein Anfang.
     */
    private LocalDateTime replanCutoff(LocalDate startDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime horizonStart = startDate.atStartOfDay();
        return now.isAfter(horizonStart) ? now : horizonStart;
    }

    /**
     * Die Modulfarbe. Rückfall ist das Study-Lila der Oberfläche, nicht
     * {@code getColorForSpaceType(STUDY)} — das liefert noch das alte Blau und passt nicht mehr
     * zur Palette.
     */
    private String getColorForCourse(Course course) {
        if (course != null && course.getColor() != null && !course.getColor().isBlank()) {
            return course.getColor();
        }
        return "#C2C1FF";
    }

    private String getColorForTask(Task task) {
        if (task.getPriority() != null && task.getPriority() >= 4)
            return switch (task.getPriority()) {
                case 5  -> "#F44336";
                case 4  -> "#FF9800";
                default -> "#2196F3";
            };
        if (task.getSpaceType() != null) return getColorForSpaceType(task.getSpaceType());
        return "#2196F3";
    }

    private String getColorForSpaceType(SpaceType spaceType) {
        if (spaceType == null) return "#2196F3";
        return switch (spaceType) {
            case SPORTS   -> "#9C27B0";
            case STUDY    -> "#2196F3";
            // Gleiches Pink wie die Projekte-Kachel im Spaces-Grid des Frontends — Kalender und
            // Space sollen dieselbe Farbe sprechen.
            case PROJECTS -> "#EC4899";
            case TASKS    -> "#FF5722";
            case RECIPES  -> "#4CAF50";
            default       -> "#2196F3";
        };
    }
}
