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
    private final RoutineExerciseRepository  routineExerciseRepository;
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
    // Eine Deadline um einen Tag zu reißen kostet 40*prio*96; einen Tag später zu liegen im
    // Regelfall 288 (siehe urgencyRank). Der Solver sortiert also viele Items um, um eine
    // Deadline zu retten.
    private static final long W_LATE      = 40;
    /**
     * Grundgewicht des "früher ist besser" für Trainings und Projektzeit.
     *
     * Für AUFGABEN steht hier nichts mehr: deren Dringlichkeit kommt seit dem Umbau auf strikte
     * Priorität aus {@link #urgencyRank} bzw. {@link #dayOrderRank}, weil ein einzelner Faktor die
     * geforderte Rangordnung zwischen Priorität und Deadline nicht ausdrücken kann.
     */
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
    private static final long W_MOVE_FIXED = 1200;

    private static final long W_PEAK      = 2;

    /**
     * Fester Preis dafür, zwei gleich dringende Aufgaben am selben Tag in der falschen Reihenfolge
     * zu haben (siehe {@link #addTaskOrderPreference}).
     *
     * Bewusst ein PAUSCHALPREIS und kein Gewicht pro Slot: nur so konkurriert der Term nicht mit
     * dem Leistungshoch und der Abendstrafe, die beide über die Uhrzeit entscheiden. Er sagt nichts
     * darüber, WANN die Blöcke liegen, nur in welcher Reihenfolge.
     *
     * Die Höhe ist gegen zwei Nachbarn gerechnet:
     * <ul>
     *   <li>Beide Aufgaben einer Gruppe haben dieselbe Priorität, also auch dasselbe
     *       {@code W_PEAK * prio}. Zwei gleich große Blöcke zu tauschen ändert am Leistungshoch
     *       exakt nichts — 200 reicht deshalb bequem, um jede Rest-Asymmetrie zu überbieten.</li>
     *   <li>{@code 200 < W_MOVE_FIXED = 1200}: die Reihenfolge darf einen bereits liegenden Block
     *       nicht mehr anfassen. Ein fertiger Plan bleibt stehen, auch wenn seine Reihenfolge
     *       nicht mehr ideal ist — dieselbe Abwägung wie überall sonst.</li>
     * </ul>
     */
    private static final long W_ORDER     = 200;

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

    /**
     * Angestrebter Abstand zwischen zwei Trainings, in Tagen.
     *
     * <p>Frueher eine feste 2 fuer jedes Paar. Das ging an der Sache vorbei: der Ruhetag gilt
     * dem Muskel, nicht dem Kalender. Push nach Push beansprucht dieselbe Brust zweimal und
     * braucht laenger als Push nach Beinen, wo sich die beiden Einheiten kaum begegnen.
     *
     * <p>Der tatsaechliche Wunschabstand liegt zwischen diesen beiden Grenzen und haengt an der
     * Ueberschneidung der beanspruchten Muskeln - siehe {@link #restDaysBetween}. Bei halber
     * Ueberschneidung kommt wieder 2 heraus, das bisherige Verhalten ist also der Mittelfall.
     */
    private static final int REST_DAYS_MIN = 1;
    private static final int REST_DAYS_MAX = 3;

    /** Abstand, solange ueber die Muskeln nichts bekannt ist (freie Einheit ohne Routine). */
    private static final int REST_DAYS_DEFAULT = 2;

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
     * Nachhol-Fenster für einen bereits überfälligen Task, in Tagen ab jetzt.
     *
     * Eine harte Obergrenze gibt es hier nicht mehr zu erben — die Deadline ist vorbei, jede Lage
     * ist zu spät. Trotzdem darf der Block nicht irgendwo im Monat landen: "überfällig" heißt
     * "jetzt", nicht "in drei Wochen". Drei Tage lassen dem Solver genug Luft, um einen vollen
     * Tag zu umgehen, halten die Domäne der Startvariablen aber klein.
     */
    private static final int  CATCHUP_DAYS = 3;

    /** Steht an jedem Block, den erst der Quetsch-Nachlauf untergebracht hat. */
    private static final String NOTE_SQUEEZED = "Eng geplant, um die Deadline zu halten.";

    /** Steht an jedem Block, der in den Sicherheitspuffer vor der Deadline gerutscht ist. */
    private static final String NOTE_NO_BUFFER = "Ohne Puffer vor der Deadline geplant.";

    /**
     * Sicherheitspuffer vor der Deadline, wenn der Nutzer nichts eingestellt hat.
     *
     * Ein Tag: knapp genug, dass er im Alltag fast nie stört, und weit genug, dass "fertig" nicht
     * heißt "fertig in der Minute, in der es fällig ist". Der Puffer ist ein ZIEL des Hauptlaufs,
     * keine zweite harte Grenze — der Nachlauf CATCH_UP rechnet weiterhin mit der echten Deadline
     * und darf hineinplanen, wenn es sonst nicht passt. Deshalb erzeugt er für sich genommen auch
     * nie eine Warnung.
     */
    private static final int DEFAULT_DEADLINE_BUFFER_HOURS = 24;

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
     *   Gewohnheit    prio*60                        =   60 ..  300
     *   Training      W_DROP_WORKOUT                 =         240
     *   Projektzeit   W_DROP_PROJECT                 =         200
     *   Aufgabe       siehe calculateTaskWeight      = 1500 .. 6200
     * </pre>
     * {@code min(Aufgabe) = 1500} liegt strikt über {@code max(Wiederkehrendes) = 300}. Innerhalb
     * jedes Bandes bleibt die Ordnung monoton erhalten; es kippt ausschließlich das
     * Verhältnis Aufgabe-zu-Wiederkehrendem, und genau das ist der Zweck.
     *
     * Projektzeit bleibt das Verzichtbarste: deadlinefrei, quotenbasiert und nächste Woche wieder da.
     */
    private static final long W_DROP_PROJECT   = 200;
    private static final long W_DROP_WORKOUT   = 240;
    private static final long W_DROP_HABIT_PRIO = 60;
    private static final long W_DROP_TASK_BASE = 400;

    /**
     * Abstand zwischen zwei Prioritätsstufen beim Verdrängen — der Grund, warum Priorität STRIKT
     * ist. Muss größer sein als die größte Spanne, die die Deadline innerhalb einer Stufe erzeugen
     * kann ({@code (max(DROP_URGENCY) - min(DROP_URGENCY)) * 100 = 700}), sonst könnte eine nähere
     * Deadline die niedrigere Priorität wieder nach oben ziehen. Siehe {@link #calculateTaskWeight}.
     */
    private static final long W_DROP_PRIO_STEP = 1000;

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

    /**
     * Zeitbudget je Nachlauf; siehe {@link #solveReliefPass}.
     *
     * Kommt ZUSÄTZLICH zu {@code solver-time-limit-seconds} — im Regelfall aber gar nicht zum
     * Tragen: die Nachläufe starten nur, wenn der Hauptlauf eine Aufgabe mit Deadline nicht
     * unterbringen konnte, und ihr Modell umfasst dann eine Handvoll Chunks statt zweihundert
     * Intervalle. Eine Sekunde reicht dafür weit; gemessen wird die Wanduhr, nicht das Modell.
     */
    @Value("${scheduler.relief-time-limit-seconds:1.0}")
    private double reliefTimeLimitSeconds = 1.0;

    /**
     * Das gelockerte Tagesfenster des zweiten Nachlaufs ("Reinquetschen").
     *
     * Bewusst NICHT die Arbeitszeit des Nutzers: dieser Pass läuft erst, wenn eine Deadline sonst
     * reißt, und dann ist ein Block um halb neun abends das kleinere Übel. Nach oben bleibt es
     * trotzdem begrenzt — die Nacht ist keine Reserve, und ohne Deckel würde der Löser jede
     * Deadline mit einem Block um vier Uhr morgens "retten".
     */
    @Value("${scheduler.relief-day-start:07:00}")
    private String reliefDayStart = "07:00";

    @Value("${scheduler.relief-day-end:22:00}")
    private String reliefDayEnd = "22:00";

    /**
     * Wie lange Phase 2 ohne nennenswerten Fortschritt weitersuchen darf, bevor abgebrochen wird.
     *
     * Phase 2 beweist nie Optimalität und lief deshalb bisher IMMER bis zur Zeitgrenze — auch dann,
     * wenn längst nichts mehr zu holen war. An einem Bestand von 103 Intervallen sah die Zeitachse
     * der verbessernden Lösungen typischerweise so aus:
     *
     * <pre>
     *   0.123s=33498  0.375s=29230  0.822s=26629  0.861s=26620
     *   1.500s=26618  1.796s=26602  1.825s=26596
     * </pre>
     *
     * Nach 0.861s waren 99.65% der Gesamtverbesserung erreicht; die restliche Sekunde brachte 24
     * von 6902 Einheiten.
     *
     * <p><b>Die Höhe ist gemessen, nicht geschätzt.</b> Kürzere Fenster sehen verlockend aus,
     * schneiden aber mitten in den Abstieg: CP-SAT verbessert sich stoßweise, mit Lücken bis
     * ~170ms zwischen zwei echten Fortschritten. Vier Läufe je Einstellung über denselben
     * Kaltstart-Bestand (Zielwert kleiner = besser):
     *
     * <pre>
     *   Fenster    Laufzeit        Zielwert
     *   aus        2018..2021      25522..25811   (Referenz)
     *   300ms       669..1510      26444..30038   — bis 16% schlechter
     *   500ms       910..1789      24902..28687
     *   700ms      1576..1798      25048..26614   — deckungsgleich mit der Referenz
     * </pre>
     *
     * 700ms ist damit die kleinste Einstellung, deren Zielwerte sich noch mit der Referenz
     * überlappen. Der Gewinn ist bewusst der bescheidenere: ~350ms auf dem Kaltstart plus eine
     * Deckelung der Ausreißer — Qualität wird hier nicht gegen Tempo eingetauscht.
     *
     * <p>Im Regelfall wiegt der Abbruch schwerer, als diese Zahlen vermuten lassen: bei einem
     * Wiederholungslauf mit Stabilitätsanker (der Normalfall, siehe {@link #addStabilityTerm})
     * ist die Suche nach wenigen hundert Millisekunden durch, und genau dort greift das Fenster.
     */
    @Value("${scheduler.solver-stall-ms:700}")
    private long solverStallMs = 700;

    /**
     * Mindestlaufzeit von Phase 2, bevor der Stillstandsabbruch überhaupt greifen darf.
     *
     * Schützt den langsamen Start: die erste Lösung kam oben erst nach 123ms, und bis dahin steht
     * die Stillstandsuhr bereits. Ohne diese Untergrenze bräche ein Lauf, dessen erste Lösung
     * länger auf sich warten lässt, ab, bevor er überhaupt eine hat.
     */
    @Value("${scheduler.solver-min-ms:500}")
    private long solverMinMs = 500;

    /**
     * Ab welcher RELATIVEN Verbesserung eine neue Lösung als Fortschritt zählt.
     *
     * Nötig, weil die letzten Lösungen oben um 0.008% besser waren (26618 → 26616). Würde das als
     * Fortschritt gelten, hielte ein Tropfen solcher Rauschlösungen die Stillstandsuhr endlos am
     * Laufen und der Abbruch käme nie. Gemeint ist "es geht noch spürbar voran", nicht "es hat
     * sich irgendeine Ziffer bewegt".
     */
    @Value("${scheduler.solver-improvement-epsilon:0.005}")
    private double solverImprovementEpsilon = 0.005;

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

    /**
     * Bekannte Grenze: der Löser läuft INNERHALB der Transaktion des Aufrufers.
     *
     * Einlesen, Rechnen und Schreiben hängen an derselben {@code @Transactional}-Klammer, die
     * Datenbankverbindung bleibt also über den kompletten Lauf belegt — beim Kaltstart bis zu zwei
     * Sekunden. Bei Einzelnutzung ist das folgenlos; erst mit mehreren gleichzeitig rechnenden
     * Nutzern (zwei Rechen-Threads im {@link ScheduleRegenerationCoordinator} plus der nächtliche
     * Rundlauf) wird der Verbindungspool zum Nadelöhr.
     *
     * Die Auftrennung wäre: einlesen (Tx) → rechnen (ohne Tx) → schreiben (Tx). Sie ist bewusst
     * NICHT gemacht — der Löser arbeitet auf geladenen Entitäten ({@code Task}, {@code Habit},
     * {@code CalendarEvent}), die außerhalb der Transaktion detached wären, und ein übersehener
     * Lazy-Zugriff fiele erst im Betrieb auf. Das gehört hinter einen Integrationstest gegen eine
     * echte Datenbank, nicht hinter die Mockito-Tests dieser Klasse.
     */
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
        int geaenderteBloecke = reconcileScheduledEvents(userId, cutoff,
                cleanupHorizonEnd(startDate).atTime(23, 59, 59), outcome.getItems());
        log.info("Gespeichert: {} geplante Blöcke", outcome.getItems().size());
        writeBackTaskSpans(outcome, input.getTasks(), credited);

        // Die Vorlesungen laufen getrennt und landen NICHT in outcome.getItems(): ScheduleResult
        // zählt alles, was kein TASK ist, als "Habits/Workouts" und summiert es in
        // totalHoursScheduled. Eingemischt bliese das die Kennzahl mit Stunden auf, die der
        // Solver nie geplant hat.
        // Zählt mit in changedBlocks: der Stundenplan schreibt an reconcileScheduledEvents vorbei,
        // und ein Lauf, der NUR eine Vorlesung geändert hat, müsste sonst 0 melden — das Frontend
        // spart sich dann den Abruf und zeigt den alten Kalender.
        //
        // ACHTUNG, bekannte Grobheit: syncClassEvents löscht und schreibt jedes Mal ALLES neu
        // (clearClassEvents + saveScheduleToDatabase), statt wie reconcileScheduledEvents
        // abzugleichen. Die Zahl ist damit "wie viele Vorlesungen es gibt", nicht "wie viele sich
        // geändert haben" — und weil jeder Lauf neue IDs vergibt, ist das für das Frontend sogar
        // korrekt: es MUSS nachladen. Folge: die Abkürzung "changedBlocks == 0, also kein
        // Monatsabruf" greift nur für Nutzer ohne Vorlesungen im Horizont. Wer sie auch für
        // Stundenplan-Nutzer will, muss zuerst syncClassEvents auf denselben Abgleich umstellen.
        geaenderteBloecke += syncClassEvents(userId, startDate, classEndDate);
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
                scheduledTasks.size() + scheduledRest.size(), geaenderteBloecke);

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
                        + "status={} p2Retry={} overdue={} relief={}+{} verdraengt={} "
                        + "statements={} atRisk={}",
                userId, ChronoUnit.DAYS.between(startDate, endDate) + 1, totalMs, collectMs,
                solveMs, outcome.getPhase1Ms(), outcome.getPhase2Ms(), persistMs,
                outcome.getIntervals(), outcome.getPlaceables(), taskBlocks, restBlocks,
                outcome.getDrop(), Math.round(outcome.getPlacementObjective()),
                outcome.getStatus(), outcome.isPhase2Retried(), outcome.getOverduePlaced(),
                outcome.getReliefCatchUp(), outcome.getReliefSqueeze(), outcome.getDisplaced(),
                statements, outcome.getAtRisk().size());
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
        /**
         * Bei Trainings die primaer beanspruchten Muskeln der Routine, sonst leer.
         *
         * <p>Bestimmt zusammen mit dem Nachbarn den gewuenschten Abstand - siehe
         * {@link SmartSchedulerService#restDaysBetween}.
         */
        Set<MuscleGroup> muscles = Set.of();
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
     *
     * <p><b>Was der Vorlauf schon belegt hat, zählt mit.</b> Ein überfälliger Block darf den Deckel
     * für SICH überschreiten — er ist der eine Fall, für den der Deckel nie gedacht war. Er hat
     * aber im Hauptmodell kein Placeable mehr, und ohne diese Verrechnung hielte der Löser den Tag
     * für unberührt und legte die volle Tagesration NOCH EINMAL obendrauf. Aus "der Deckel gilt
     * für Überfälliges nicht" würde so "an einem Nachholtag gilt der Deckel überhaupt nicht mehr".
     */
    private void addDailyLoadLimits(CpModel model, UserPreferences prefs, Axis axis,
                                    List<TaskChunk> chunks, List<Placeable> allPlaceables) {
        Set<Placeable> taskPlaceables = chunks.stream()
                .map(c -> c.placeable)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        // Was der Vorlauf an einem Tag bereits verplant hat, in Slots und Blöcken.
        Map<Integer, Integer> vergebenSlots  = new HashMap<>();
        Map<Integer, Integer> vergebenBloecke = new HashMap<>();
        for (TaskChunk c : chunks) {
            if (c.placedStartSlot == null) continue;
            int tag = c.placedStartSlot / SLOTS_PER_DAY;
            vergebenSlots.merge(tag, Axis.slotsFor(c.durationMinutes), Integer::sum);
            vergebenBloecke.merge(tag, 1, Integer::sum);
        }

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

            int belegt  = vergebenSlots.getOrDefault(e.getKey(), 0);
            int bloecke = vergebenBloecke.getOrDefault(e.getKey(), 0);

            if (anyTask) {
                model.addLessOrEqual(taskLoad.build(), Math.max(0, taskCapSlots - belegt));
                if (maxTasksPerDay != null && maxTasksPerDay > 0) {
                    model.addLessOrEqual(taskCount.build(), Math.max(0, maxTasksPerDay - bloecke));
                }
            }
            model.addLessOrEqual(totalLoad.build(), Math.max(0, totalCapSlots - belegt));
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
        /**
         * Wo dieser Chunk gelandet ist, oder {@code null}, wenn ihn kein Pass unterbringen konnte.
         *
         * Getrennt vom {@link Placeable}, weil ihn nicht nur der Hauptlauf füllt: die Nachläufe
         * ({@link #solveReliefPass}) arbeiten mit einem eigenen Modell und schreiben ihr Ergebnis
         * in dasselbe Feld. Erst danach steht fest, was wirklich fehlt — deshalb entscheidet auch
         * {@link #classifyAtRisk} erst am Ende und nicht mehr beim Auslesen des Hauptlaufs.
         */
        Integer placedStartSlot;
        /** 0 = Hauptlauf, 1 = nachgerückt, 2 = in die gelockerten Zeiten gequetscht. */
        int reliefLevel;
    }

    /**
     * Ein Block gilt als VERPASST, wenn er vor dem Umplanzeitpunkt lag, nicht abgehakt wurde und zu
     * einem Task mit abgelaufener Deadline gehört. Die Deadline ist verstrichen und der Task steht
     * immer noch offen — die Zeit ist offensichtlich nicht genutzt worden.
     *
     * Seine Minuten dürfen deshalb nicht als geleistet zählen: sonst bleibt in {@link #chunkSizes}
     * nichts mehr übrig ({@code remaining <= 0}), der Task erzeugt keinen einzigen Chunk, erreicht
     * den Solver nie und {@link #writeBackTaskSpans} schreibt ihm seinen alten Termin von gestern
     * zurück — er verharrt für immer im offenen Status, mit einem Block in der Vergangenheit.
     *
     * Bewusst NUR bei abgelaufener Deadline: solange sie in der Zukunft liegt, bleibt es bei der
     * bisherigen Annahme "vergangener Block = gelaufene Zeit". Der Task-Status muss nicht geprüft
     * werden, findSchedulableTasks liefert ohnehin nur offene Aufgaben.
     *
     * <p><b>Maßgeblich ist das ENDE, nicht der Start.</b> Ein Block, der gerade läuft, ist nicht
     * verpasst — er läuft. Über den Start gemessen war das ein Selbstläufer, seit der Vorlauf
     * überfällige Aufgaben auf "jetzt" legt: der Block beginnt um 15:15, der nächste Lauf ein paar
     * Minuten später hält ihn für vertan, schreibt seine Minuten nicht gut und bucht einen ZWEITEN
     * Nachholtermin — und der übernächste Lauf einen dritten. Am Ende stand für eine
     * Sechzig-Minuten-Aufgabe der halbe Nachmittag im Kalender.
     */
    private boolean isMissedBlock(CalendarEvent e, LocalDateTime cutoff) {
        if (e.getRelatedTask() == null || e.getCompletedAt() != null) return false;
        if (e.getEndTime() == null || !e.getEndTime().isBefore(cutoff)) return false;
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

        // Rahmen für Gewohnheiten und Trainings. Aufgaben und Projektzeit bleiben auf der
        // Arbeitszeit — siehe privateDayBounds.
        int[] privat = privateDayBounds(prefs);
        int privateStartSlot = privat[0];
        int privateEndSlot   = privat[1];

        int bufferSlots = slotsAufgerundet(nz(prefs.getBufferMinutes(), 0));
        // Derselbe Zeitpunkt, ab dem auch gelöscht und neu geschrieben wird — nicht ein zweites,
        // minimal späteres LocalDateTime.now(). Sonst entscheidet das Modell "überfällig" nach
        // einer anderen Uhr als die Meldung in extract.
        int nowSlot     = Math.max(0, axis.ceilSlot(cutoff));
        int[] peakWindow = peakWindow(prefs);

        // Mindestpause zwischen zwei automatisch geplanten Blöcken. Ohne sie stapelt der Solver
        // acht Stunden Arbeit lückenlos aufeinander — technisch optimal, menschlich unbrauchbar.
        int gapSlots = slotsAufgerundet(nz(prefs.getBreakDurationMinutes(), 0));

        // Wie weit vor der Deadline der Hauptlauf fertig sein will. Bewusst NICHT über
        // Axis.slotsFor: das rundet auf mindestens einen Slot auf, und ein eingestellter Puffer
        // von 0 soll auch 0 sein.
        int deadlineBufferSlots = Math.max(0,
                nz(prefs.getDeadlineBufferHours(), DEFAULT_DEADLINE_BUFFER_HOURS) * 60 / GRID);

        // Platzhalter-Zuordnung für flexible Workouts, bewusst lokal: ein Feld würde
        // zwischen zwei Läufen (auch verschiedener User) Zustand verschleppen.
        Map<Long, Placeable> workoutPlaceables = new HashMap<>();

        List<int[]> blocked = collectBlockedSlots(axis, input, startDate, endDate, bufferSlots, gapSlots);

        Map<String, Integer> previousStarts = previousStartSlots(input, axis, chunks);

        // ---- Vorlauf: Überfälliges zuerst ----
        //
        // Läuft VOR dem Hauptmodell und hängt seine Blöcke an blocked an. Damit dreht sich das
        // Verhältnis um: nicht "Überfälliges kämpft mit allem anderen um Reste", sondern "alles
        // andere wird um das Überfällige herum geplant". Siehe solveOverduePass.
        int overduePlatziert = solveOverduePass(chunks, blocked, axis, prefs, nowSlot, gapSlots,
                previousStarts);

        List<IntervalVar> allIntervals = new ArrayList<>();
        for (int i = 0; i < blocked.size(); i++) {
            int[] b = blocked.get(i);
            allIntervals.add(model.newFixedInterval(b[0], b[1] - b[0], "blocked_" + i));
        }

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

            // Überfälliges hat der Vorlauf bereits platziert; seine Zeit steckt schon als festes
            // Intervall in blocked. Ein zweites Placeable hier wäre nicht nur überflüssig, es
            // würde den Block doppelt buchen. Die Prüfung gilt für die ganze Gruppe, weil
            // istUeberfaellig nur an der Deadline des TASKS hängt — es kann keine gemischte
            // Gruppe geben.
            if (istUeberfaellig(group.get(0), axis, nowSlot)) continue;

            long weight = calculateTaskWeight(task, startDate);

            int earliest = nowSlot;
            if (task.getNotBefore() != null) earliest = Math.max(earliest, axis.ceilSlot(task.getNotBefore()));

            Integer deadlineSlot = task.getDeadline() != null ? axis.floorSlot(task.getDeadline()) : null;

            // Deadline schneidet das Fenster hart zu (siehe taskBounds). taskLastDay ist damit nur
            // noch der Nahbereich für Tasks OHNE Deadline.
            // Restdauer der ganzen Aufgabe, damit der Puffer sie nicht aus ihrem eigenen Fenster
            // drängt (siehe taskBounds).
            int restSlots = group.stream().mapToInt(c -> Axis.slotsFor(c.durationMinutes)).sum();
            TaskBounds bounds = taskBounds(task, axis, taskLastDay, nowSlot, earliest,
                    deadlineBufferSlots, restSlots);

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
                //
                // Der Rang ist seit dem Umbau auf strikte Priorität nicht mehr prio*Stufe, sondern
                // prio*4 + Stufe: die Deadline ordnet nur noch INNERHALB einer Prioritätsstufe.
                // Der Faktor SLOTS_PER_DAY/4 hält den Regelfall exakt auf dem alten Wert —
                // P3 ohne Deadline ergibt 12*24 = 288, genau wie vorher 1*3*1*96. Alle
                // dokumentierten Abwägungen im Modell sind gegen diese Zahl formuliert.
                qVars.add(gatedDayIndex(model, p, "urg_" + name, axis));
                qWeights.add((long) urgencyRank(task, startDate) * (SLOTS_PER_DAY / 4));

                // Und ein schwacher Gleichstandsbrecher INNERHALB des Tages: liegen zwei Aufgaben
                // am selben Tag, soll die früher fällige zuerst drankommen. Ohne ihn wäre der Tag
                // nach innen völlig flach und die Reihenfolge beliebig — das war eine echte
                // Verschlechterung, kein Testartefakt.
                //
                // Der Rang ist bewusst schmal (2..11 pro Slot): über einen vollen Tag kostet die
                // Reihenfolge damit höchstens 11*96 = 1056 und bleibt unter der Totzone des
                // Stabilitätsankers (1200) — Ordnung ja, aber niemals um den Preis, dass ein
                // liegender Block noch einmal verschoben wird. Bis zum Umbau auf strikte Priorität
                // stand hier NUR die Deadline-Stufe: lagen zwei Aufgaben am selben Tag, war ihre
                // Reihenfolge von der Priorität völlig unabhängig.
                qVars.add(gated(model, p, "urgday_" + name, p.start, axis.horizonSlots));
                qWeights.add((long) dayOrderRank(task, startDate));

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

        addTaskOrderPreference(model, chunksByTask, startDate, qVars, qWeights);

        // --- Habit-Slots ---
        for (int i = 0; i < habitSlots.size(); i++) {
            HabitSlot s = habitSlots.get(i);
            int sizeSlots = Axis.slotsFor(s.durationMinutes);
            String name = "habit" + s.habit.getId() + "_" + i;

            // Privatzeiten statt Arbeitszeit: eine Gewohnheit ist kein Termin des Arbeitstags.
            // Vorher war ihr Wunschfenster bei einem Arbeitstag von 08:00–17:00 unerreichbar —
            // EVENING (17:00–22:00) lag vollständig dahinter, MORNING beginnt um 06:00.
            List<DayWindow> windows = dayWindows(axis, privateStartSlot, privateEndSlot, sizeSlots,
                    nowSlot, s.allowedDays, axis.totalDays - 1);
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
        // Einheiten OHNE Wunschtag. Nur sie sind untereinander austauschbar und nur sie
        // unterliegen der automatischen Ruhetagsverteilung - wer einer Routine einen Wochentag
        // gibt, hat den Rhythmus damit selbst festgelegt.
        Map<LocalDate, List<Placeable>> floatingWeeks = new LinkedHashMap<>();

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

            // Wunschtag der Routine: die Tagesauswahl schrumpft auf diesen einen Tag, der Solver
            // sucht dort nur noch die Uhrzeit. Bewusst hart - ein Wochentag, der regelmäßig nicht
            // eingehalten wird, wäre als Einstellung wertlos.
            //
            // Der Rückfall auf die ganze Woche greift, wenn der Wunschtag im Horizont gar nicht
            // vorkommt oder dort schon ein anderes Training liegt. Dann ist die Alternative nicht
            // "Wunschtag", sondern "kein Training" - und ein Tag daneben ist besser als keins.
            boolean pinned = false;
            Integer preferredWeekday = w.getRoutine() != null
                    ? w.getRoutine().getPreferredWeekday() : null;
            if (preferredWeekday != null) {
                List<Integer> onPreferred = days.stream()
                        .filter(d -> startDate.plusDays(d).getDayOfWeek().getValue() == preferredWeekday)
                        .toList();
                if (!onPreferred.isEmpty()) {
                    days = new ArrayList<>(onPreferred);
                    pinned = true;
                }
            }

            // Wie bei den Gewohnheiten: Training ist Privatzeit, nicht Arbeitszeit.
            List<DayWindow> windows = dayWindows(axis, privateStartSlot, privateEndSlot, sizeSlots,
                    nowSlot, days, axis.totalDays - 1);
            Placeable p = makePlaceable(model, name, sizeSlots, duration, windows, gapSlots);
            if (w.getRoutine() != null) {
                p.muscles = input.getRoutineMuscles()
                        .getOrDefault(w.getRoutine().getId(), Set.of());
            }
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
            if (!pinned) {
                floatingWeeks.computeIfAbsent(weekStart, k -> new ArrayList<>()).add(p);
            }
        }

        // --- Gruppen-Constraints für flexible Workouts (je Zielwoche) ---
        // Derselbe Helfer wie bei Habits und Projekten: höchstens eine Einheit pro Tag (hart) plus
        // chronologische Ordnung, die zugleich die Permutationssymmetrie der austauschbaren
        // Platzhalter bricht und damit den Solve beschleunigt.
        for (Map.Entry<LocalDate, List<Placeable>> e : workoutWeeks.entrySet()) {
            Set<Integer> days = workoutWeekDays.get(e.getKey());

            // "Höchstens ein Training pro Tag" gilt über ALLE Einheiten der Woche - eine mit
            // Wunschtag und eine frei verschiebbare dürfen nicht auf denselben Tag fallen.
            addAtMostOnePerDay(model, e.getValue(), days);

            // Ordnung und Ruhetagsverteilung nur über die frei verschiebbaren: die anderen haben
            // ihren Tag vom Nutzer, und eine Ordnung über feste Tage wäre schnell unerfüllbar.
            List<Placeable> floating = floatingWeeks.getOrDefault(e.getKey(), List.of());
            if (floating.size() > 1) {
                addChronologicalOrder(model, floating);
                addRestDayRule(model, floating, days, "wow" + e.getKey(), qVars, qWeights);
            }
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

        // Phase 1 hat eine gültige Lösung. Sie wird JETZT festgehalten, bevor Phase 2 das Modell
        // anfasst: scheitert Phase 2, steht im Löser keine gültige Belegung mehr, und ohne diesen
        // Schnappschuss wäre die bereits bewiesene Lösung verloren. Siehe unten.
        Platzierung phase1Platzierung = schnappschuss(solver, allPlaceables);

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
        // nachweislich zulässige Lösung — Phase 2 startet also nicht bei null, sondern hat vom
        // ersten Moment an etwas, das sie nur noch verbessern muss.
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
        //
        // Stattdessen wird auf STILLSTAND abgebrochen (siehe StallProbe): nicht "nah genug am
        // Optimum", sondern "seit einer Weile nichts Nennenswertes mehr gefunden". Das ist der
        // Unterschied, an dem der Gap-Limit-Versuch gescheitert ist — die Feinarbeit findet
        // weiterhin statt, nur das Warten danach entfällt.
        long p2Start = System.nanoTime();
        CpSolverStatus s2 = solveMitStillstandsabbruch(solver, model);
        long phase2Ms = (System.nanoTime() - p2Start) / 1_000_000;
        // Der Zielwert der Platzierung, fürs Log. Er ist die einzige Möglichkeit, die Wirkung des
        // Zeitbudgets zu beurteilen: der Status bleibt bei realistischem Bestand immer FEASIBLE,
        // aber der Zielwert zeigt, ab wann mehr Zeit nichts mehr bringt.
        double placementObjective = (s2 == CpSolverStatus.OPTIMAL || s2 == CpSolverStatus.FEASIBLE)
                ? solver.objectiveValue() : Double.NaN;

        boolean phase2Brauchbar = s2 == CpSolverStatus.OPTIMAL || s2 == CpSolverStatus.FEASIBLE;
        CpSolverStatus effective = phase2Brauchbar ? s2 : s1;

        // Phase 2 gescheitert? Dann gilt die Lösung aus Phase 1 - schlechter platziert, aber
        // vollständig gültig und bereits bewiesen.
        //
        // Früher wurde sie dafür NEU GELÖST (eine Sekunde, Hint aus Phase 1), weil extract() aus
        // dem Löser liest. Das hielt nur, solange der Hint sofort wieder durchging: bei rund 75
        // offenen Aufgaben schaffte der Wiederholungslauf es nicht mehr, der ganze Lauf endete
        // auf UNKNOWN und der Kalender blieb stehen - ohne dass etwas als gefährdet gemeldet
        // wurde. Ein größeres Zeitbudget half nicht, weil nicht die Suche das Problem war,
        // sondern das Wegwerfen einer Lösung, die man schon hatte.
        //
        // Der Schnappschuss braucht weder Zeit noch Glück: er liest dieselben zwei Werte je
        // Placeable, die extract() ohnehin abfragt.
        boolean phase2Retried = !phase2Brauchbar;
        if (!phase2Brauchbar) {
            log.warn("CP-SAT Phase 2 ohne Lösung ({}), nutze die Platzierung aus Phase 1.", s2);
        }
        Platzierung platzierung = phase2Brauchbar ? ausLoeser(solver) : phase1Platzierung;

        log.info("CP-SAT {} | Intervalle: {} | Chunks: {} Habits: {} Workouts: {} Projekte: {} | drop={} obj={}",
                effective, allIntervals.size(), chunks.size(), habitSlots.size(),
                flexibleWorkouts.size(), projectSlots.size(), bestDrop,
                Math.round(placementObjective));

        SolveOutcome outcome = extract(platzierung, effective, chunks, habitSlots, flexibleWorkouts,
                workoutPlaceables, projectSlots, axis);

        // ---- Nachläufe: was der Hauptlauf liegen gelassen hat, doch noch vor die Deadline ----
        //
        // Der Hauptlauf hat die Zeit verteilt; hier wird nur noch aufgefüllt. Alles, was schon
        // liegt, geht als festes Intervall in ein eigenes, winziges Modell — die Nachläufe können
        // deshalb nichts umsortieren, sondern ausschließlich ergänzen.
        // blocked bringt seinen Puffer schon mit (collectBlockedSlots); bei den frisch geplanten
        // Blöcken steckt die Pause dagegen im Intervall des Placeables und geht hier verloren.
        // Deshalb wird sie hier angehängt: auch ein Nachlauf soll sich nicht direkt an einen
        // gerade erst geplanten Block klemmen. Der gerettete Block bringt seine eigene Pause mit
        // (bzw. beim Quetschen bewusst keine).
        List<int[]> belegt = new ArrayList<>(blocked);
        for (ScheduledItem i : outcome.getItems()) {
            int s = axis.floorSlot(i.getStartTime());
            belegt.add(new int[]{ s, s + Axis.slotsFor(
                    (int) ChronoUnit.MINUTES.between(i.getStartTime(), i.getEndTime())) + gapSlots });
        }
        for (TaskChunk c : chunks) {
            if (c.placedStartSlot == null) continue;
            belegt.add(new int[]{ c.placedStartSlot,
                    c.placedStartSlot + Axis.slotsFor(c.durationMinutes) + gapSlots });
        }

        int relief1 = solveReliefPass(ReliefMode.CATCH_UP, chunks, belegt, axis, prefs, nowSlot, gapSlots);
        int relief2 = solveReliefPass(ReliefMode.SQUEEZE, chunks, belegt, axis, prefs, nowSlot, gapSlots);

        // ---- Letzte Stufe: verdrängen statt aufgeben ----
        //
        // Bis hierher wurde nur aufgefüllt. Was jetzt noch fehlt, fehlt nicht aus Platzmangel im
        // Kalender, sondern weil die Zeit an etwas Unwichtigeres vergeben ist. Genau dort greift
        // dieser Pass ein — und nur dort: er läuft ausschließlich für Aufgaben, deren Deadline
        // sonst reißt.
        int verdraengt = solveDeadlineRescuePass(chunks, outcome, blocked, axis, prefs, nowSlot);

        outcome.getItems().addAll(buildTaskItems(chunks, axis, deadlineBufferSlots));
        outcome.getAtRisk().addAll(classifyAtRisk(chunks, axis, cutoff));
        outcome.setReliefCatchUp(relief1);
        outcome.setReliefSqueeze(relief2);
        outcome.setOverduePlaced(overduePlatziert);
        outcome.setDisplaced(verdraengt);
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
     * Löst Phase 2 und bricht ab, sobald nichts Nennenswertes mehr gefunden wird.
     *
     * Der Abbruch braucht ZWEI Teile, und beide sind nötig:
     * <ul>
     *   <li>{@link StallProbe} sieht jede verbessernde Lösung und merkt sich, wann zuletzt eine
     *       kam, die die Schwelle {@link #solverImprovementEpsilon} überschritten hat.</li>
     *   <li>Der Wachhund unten zieht daraus die Konsequenz. Aus dem Callback heraus ginge das
     *       nicht: im Plateau-Fall — genau dem, um den es geht — kommt überhaupt keine Lösung
     *       mehr, der Callback feuert also nie wieder und könnte nie abbrechen.</li>
     * </ul>
     *
     * Abgebrochen wird erst, wenn es überhaupt schon eine Lösung gibt. "Stillstand" heißt
     * <em>keine Verbesserung mehr</em>, nicht <em>noch nichts gefunden</em> — die beiden zu
     * verwechseln kostet bei großem Bestand den ganzen Lauf.
     *
     * {@code setMaxTimeInSeconds} bleibt daneben als harte Decke bestehen. Der Stillstandsabbruch
     * senkt den Regelfall, er ersetzt die Obergrenze nicht.
     *
     * {@link CpSolver#stopSearch()} ist {@code synchronized} und ausdrücklich dafür da, von einem
     * anderen Thread gerufen zu werden.
     */
    private CpSolverStatus solveMitStillstandsabbruch(CpSolver solver, CpModel model) {
        StallProbe probe = new StallProbe(solverImprovementEpsilon);
        long start = System.nanoTime();

        Thread wachhund = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(WACHHUND_TAKT_MS);
                    long jetzt = System.nanoTime();
                    if ((jetzt - start) / 1_000_000 < solverMinMs) continue;
                    // Vor der ersten Lösung gibt es keinen Stillstand, sondern nur Suche.
                    //
                    // Ohne diese Zeile fiel lastImprovementNanos auf den Startzeitpunkt zurück,
                    // der Wachhund maß also "Zeit seit Beginn" und hielt den Löser nach
                    // solverMinMs + solverStallMs an - auch wenn der noch gar keine Lösung
                    // gefunden HATTE. Bei rund 75 offenen Aufgaben braucht Phase 2 länger als
                    // diese knappe Sekunde bis zur ersten Lösung; sie wurde abgewürgt, der
                    // Wiederholungslauf konnte die Phase-1-Lösung in seiner einen Sekunde
                    // ebenfalls nicht wiederherstellen, und der ganze Lauf endete auf UNKNOWN -
                    // der Kalender blieb stehen, ohne dass irgendetwas als gefährdet gemeldet
                    // wurde. Mehr Budget half nicht, weil der Abbruch nicht am Budget hing.
                    //
                    // Die harte Decke aus setMaxTimeInSeconds begrenzt den Fall weiterhin.
                    if (!probe.hatLoesung()) continue;
                    if ((jetzt - probe.lastImprovementNanos(start)) / 1_000_000 > solverStallMs) {
                        solver.stopSearch();
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "schedule-solver-wachhund");
        wachhund.setDaemon(true);
        wachhund.start();

        try {
            return solver.solve(model, probe);
        } finally {
            wachhund.interrupt();
        }
    }

    /** Taktrate des Wachhunds — fein genug gegenüber {@link #solverStallMs}, ohne zu pollen. */
    private static final long WACHHUND_TAKT_MS = 25;

    /**
     * Merkt sich, wann Phase 2 zuletzt spürbar besser wurde.
     *
     * Bewusst nur BEOBACHTEND: das Abbrechen macht der Wachhund (siehe
     * {@link #solveMitStillstandsabbruch}). Die Felder werden aus dem Solver-Thread geschrieben
     * und aus dem Wachhund-Thread gelesen, sind deshalb {@code volatile}.
     */
    private static final class StallProbe extends CpSolverSolutionCallback {
        private final double epsilon;
        /**
         * Zielwert, gegen den die Schwelle gemessen wird — der letzte, der sie gerissen hat.
         *
         * Bewusst NICHT der laufend beste. Gegen den besten gemessen zählt jede einzelne Lösung
         * für sich, und ein Rinnsal aus Schritten knapp unterhalb der Schwelle setzt die Uhr dann
         * nie zurück: der Abbruch schlug mitten im steilen Abstieg zu und der Zielwert wurde
         * schlechter statt gleich (gemessen 30038 statt 26620). Gegen den letzten signifikanten
         * Wert SUMMIEREN sich die kleinen Schritte dagegen auf, bis sie die Schwelle gemeinsam
         * überschreiten — dann ist es echter Fortschritt und die Uhr läuft neu.
         */
        private volatile double referenz = Double.MAX_VALUE;
        private volatile long   lastImprovementNanos;

        /** Ob überhaupt schon eine Lösung kam - erst danach kann es Stillstand geben. */
        private volatile boolean hatLoesung;

        StallProbe(double epsilon) {
            this.epsilon = epsilon;
        }

        boolean hatLoesung() {
            return hatLoesung;
        }

        @Override
        public void onSolutionCallback() {
            hatLoesung = true;
            double obj = objectiveValue();
            // Die erste Lösung zählt immer als Fortschritt. Bei einem Zielwert von 0 fällt der
            // Nenner weg — dann ist ohnehin nichts mehr zu holen und alles Weitere ist Stillstand.
            boolean fortschritt = referenz == Double.MAX_VALUE
                    || (referenz - obj) > Math.abs(referenz) * epsilon;
            if (fortschritt) {
                referenz = obj;
                lastImprovementNanos = System.nanoTime();
            }
        }

        /** Zeitpunkt des letzten Fortschritts, oder {@code fallback}, solange es keinen gab. */
        long lastImprovementNanos(long fallback) {
            long t = lastImprovementNanos;
            return t == 0L ? fallback : t;
        }
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
        addAtMostOnePerDay(model, group, days);
        addChronologicalOrder(model, group);
    }

    /**
     * Höchstens eine dieser Einheiten pro Tag.
     *
     * <p>Getrennt von der Ordnung, weil beide für unterschiedliche Teilmengen gelten können: bei
     * Trainings muss "einer pro Tag" über <em>alle</em> Einheiten der Woche greifen, die
     * Symmetriebrechung dagegen nur über die untereinander austauschbaren.
     */
    private void addAtMostOnePerDay(CpModel model, List<Placeable> group, Collection<Integer> days) {
        for (int day : days) {
            List<Literal> sameDay = new ArrayList<>();
            for (Placeable p : group) {
                BoolVar b = p.inDay.get(day);
                if (b != null) sameDay.add(b);
            }
            if (sameDay.size() > 1) model.addAtMostOne(sameDay);
        }
    }

    /**
     * Chronologische Ordnung als Symmetriebrechung.
     *
     * <p>Nur für Slots, die untereinander <b>austauschbar</b> sind. Auf Einheiten mit festem Tag
     * angewandt wäre sie ein Widerspruch statt einer Hilfe: liegt der Wunschtag der ersten
     * Einheit hinter dem der zweiten, ist die Ordnung unerfüllbar und der Solver verwirft beide,
     * statt sie einfach an ihre Tage zu legen.
     */
    private void addChronologicalOrder(CpModel model, List<Placeable> group) {
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

        // gaps[i] ist der Wunschabstand zwischen Einheit i-1 und i, in Tagen.
        int[] gaps = new int[group.size()];
        for (int i = 1; i < group.size(); i++) {
            gaps[i] = restDaysBetween(group.get(i - 1), group.get(i));
        }

        if (restDaysFit(sorted, gaps)) {
            for (int i = 1; i < group.size(); i++) {
                Placeable a = group.get(i - 1);
                Placeable b = group.get(i);
                // "a am Tag d" schließt "b" auf den folgenden gaps[i]-1 Tagen aus. Denselben Tag
                // verbietet bereits das addAtMostOne aus addWeekGroupConstraints, die Reihenfolge
                // die dortige Ordnung.
                for (int d : sorted) {
                    BoolVar ad = a.inDay.get(d);
                    if (ad == null) continue;
                    for (int offset = 1; offset < gaps[i]; offset++) {
                        BoolVar bd = b.inDay.get(d + offset);
                        if (bd != null) model.addImplication(ad, bd.not());
                    }
                }
            }
            return;
        }

        for (int i = 1; i < group.size(); i++) {
            Placeable a = group.get(i - 1);
            Placeable b = group.get(i);
            int wanted = gaps[i] * SLOTS_PER_DAY;

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
     * Lassen sich die erlaubten Tage so belegen, dass jedes Paar seinen Wunschabstand bekommt?
     *
     * <p>{@code gaps[i]} ist der geforderte Abstand zwischen Einheit i-1 und i; {@code gaps[0]}
     * bleibt ungenutzt, die erste Einheit hat keinen Vorgänger.
     *
     * <p>Gierig von vorn: der früheste zulässige Tag ist immer eine optimale Wahl, weil er die
     * meisten Möglichkeiten für die restlichen offen lässt. Damit ist die Antwort exakt und die
     * harte Variante oben beweisbar erfüllbar — sie kann also keine Einheit kosten.
     */
    private boolean restDaysFit(List<Integer> sortedDays, int[] gaps) {
        int k = gaps.length;
        if (k <= 1) return true;

        int used = 0;
        Integer last = null;
        for (int d : sortedDays) {
            if (last == null || d - last >= gaps[used]) {
                used++;
                last = d;
                if (used >= k) return true;
            }
        }
        return false;
    }

    /**
     * Wie viele Tage sollen zwischen diesen beiden Einheiten liegen?
     *
     * <p>Nach der Ueberschneidung der primaer beanspruchten Muskeln (Jaccard): zwei Einheiten,
     * die dieselben Muskeln treffen, bekommen {@link #REST_DAYS_MAX}, zwei ohne gemeinsamen
     * Muskel {@link #REST_DAYS_MIN}. Bei halber Ueberschneidung kommt {@link #REST_DAYS_DEFAULT}
     * heraus - der Wert, der frueher fuer alle Paare galt.
     *
     * <p>Weiss der Planer ueber eine der beiden nichts (freie Einheit ohne Routine, oder eine
     * Routine ohne Uebungen), bleibt es beim bisherigen Abstand. Geraten wird nicht: eine
     * Einheit ohne bekannte Muskeln als "ueberschneidungsfrei" zu behandeln haette zwei
     * Trainings an aufeinanderfolgenden Tagen zur Folge, nur weil eine Zuordnung fehlt.
     */
    private int restDaysBetween(Placeable a, Placeable b) {
        return restDaysBetween(a.muscles, b.muscles);
    }

    /** @see #restDaysBetween(Placeable, Placeable) */
    static int restDaysBetween(Set<MuscleGroup> a, Set<MuscleGroup> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return REST_DAYS_DEFAULT;
        }
        Set<MuscleGroup> shared = new HashSet<>(a);
        shared.retainAll(b);
        if (shared.isEmpty()) {
            return REST_DAYS_MIN;
        }
        Set<MuscleGroup> union = new HashSet<>(a);
        union.addAll(b);

        double overlap = (double) shared.size() / union.size();
        return REST_DAYS_MIN + (int) Math.round(overlap * (REST_DAYS_MAX - REST_DAYS_MIN));
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
                                  int earliestSlot, int deadlineBufferSlots, int restSlots) {
        int maxDay = axis.totalDays - 1;
        int lastDay;
        Integer latestEnd = null;

        if (task.getDeadline() == null) {
            lastDay = defaultLastDay;
        } else {
            int deadlineSlot = axis.floorSlot(task.getDeadline());
            if (deadlineSlot <= nowSlot) {
                // Überfälliges kommt hier normalerweise gar nicht mehr an: es wird vom Vorlauf
                // (solveOverduePass) platziert, und seine Gruppe wird im Hauptmodell übersprungen.
                //
                // Der Zweig bleibt trotzdem stehen, und zwar als Sicherung, nicht als toter Code:
                // die Deadline wäre als Obergrenze leer (sie liegt hinter uns), das Fenster damit
                // leer, und der Task würde LAUTLOS verworfen. Rutscht durch eine spätere Änderung
                // doch einmal etwas Überfälliges hierher, bekommt es wenigstens dasselbe kurze
                // Nachhol-Fenster wie früher, statt still zu verschwinden.
                lastDay = Math.min(maxDay, nowSlot / SLOTS_PER_DAY + CATCHUP_DAYS);
            } else {
                // Der Nahbereich bleibt die Obergrenze, auch wenn die Deadline weiter weg liegt.
                // Ohne diesen Deckel wuchsen Aufgaben mit Deadline in vier oder fünf Wochen
                // (Klausurvorbereitung, Steuererklärung) von 14 auf 31 Tages-Booleans, und Phase 1
                // fand in ihrem Anteil am Zeitbudget keine brauchbare Menge mehr: an einem echten
                // Bestand blieb sie bei Drop 28200 statt 800 stehen und ließ 16 von 17 Aufgaben
                // ungeplant. Für die Deadline kostet der Deckel nichts — eine Aufgabe, die schon
                // in den nächsten 14 Tagen eingeplant wird, reißt einen Termin in vier Wochen nicht.
                //
                // Gezielt wird dabei auf den PUFFER, nicht auf die Deadline: "geplant" und "auf
                // Kante genäht" waren vorher dasselbe.
                //
                // Der Puffer schrumpft aber mit, und zwar nach ZWEI Grenzen — er darf eine Deadline
                // niemals unerreichbar machen:
                //   * höchstens die Hälfte der noch verfügbaren Zeit, und
                //   * niemals so viel, dass die Restdauer der Aufgabe nicht mehr hineinpasst.
                // Die zweite Grenze fehlte zuerst und war teuer: eine Aufgabe über 120 Minuten mit
                // Deadline in zweieinhalb Stunden bekam ein Fenster von 75 Minuten, passte dort
                // nicht und fiel komplett aus dem Hauptlauf — die Nachläufe konnten sie danach nur
                // noch in Lücken legen, statt ihr im Hauptlauf Vorrang zu verschaffen.
                int verfuegbar = deadlineSlot - Math.max(nowSlot, earliestSlot);
                int puffer     = Math.min(deadlineBufferSlots, Math.max(0, verfuegbar / 2));
                puffer         = Math.min(puffer, Math.max(0, verfuegbar - restSlots));
                int zielSlot   = deadlineSlot - puffer;

                lastDay = Math.min(defaultLastDay, zielSlot / SLOTS_PER_DAY);
                // Die scharfe Endzeit nur, wenn das Ziel auch wirklich im Fenster liegt.
                if (zielSlot / SLOTS_PER_DAY <= defaultLastDay) latestEnd = zielSlot;
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
     * Die Reihenfolge gleich dringender Aufgaben innerhalb eines Tages.
     *
     * <p><b>Warum das nicht über ein Gewicht auf {@code p.start} geht.</b> {@link #dayOrderRank}
     * ordnet den Tag, hat für die Deadline aber nur ein Bit — und dieses Bit lässt sich nicht
     * verbreitern: der Rang muss unter {@code W_MOVE_FIXED / SLOTS_PER_DAY} bleiben, und
     * {@code W_MOVE_FIXED} selbst ist nach oben durch die Deadline-Strafe gedeckelt. Ein Versuch
     * mit vier Stufen erdrückte prompt das Leistungshoch: beide Terme wirken PRO SLOT und
     * konkurrieren deshalb direkt um denselben Spielraum, der längst ausgeschöpft ist.
     *
     * <p>Gemessen sah der Fehler so aus: drei gleich wichtige Aufgaben mit Terminen an drei
     * aufeinanderfolgenden Tagen bekamen alle denselben Rang, ihre Reihenfolge am Tag war damit
     * beliebig — und sie landeten in genau umgekehrter Reihenfolge im Kalender.
     *
     * <p><b>Die Lösung ist relativ statt absolut.</b> Bestraft wird nicht mehr "spät liegen",
     * sondern "am selben Tag in der falschen Reihenfolge liegen". Ein solcher Term hat keine
     * Meinung über die Uhrzeit und konkurriert deshalb mit keinem Wunschzeit-Term: das
     * Leistungshoch darf beide Blöcke verschieben, solange ihre Reihenfolge stimmt.
     *
     * <p>Gebaut wird nur, was die Gewichte offenlassen: Aufgaben werden nach ihrem
     * {@code dayOrderRank} gruppiert — gleicher Rang heißt "das Modell kann sie nicht
     * unterscheiden", seit dessen Vereinfachung also nach Priorität —, innerhalb der Gruppe nach
     * Termin sortiert und je zwei BENACHBARTE verknüpft. Nachbarpaare genügen: stimmt jedes Paar,
     * stimmt die ganze Kette. Damit bleibt es bei O(n) statt O(n²), und Gruppen mit einem einzigen
     * Mitglied kosten gar nichts.
     */
    private void addTaskOrderPreference(CpModel model, Map<Long, List<TaskChunk>> chunksByTask,
                                        LocalDate startDate, List<IntVar> qVars,
                                        List<Long> qWeights) {
        // Der erste Chunk vertritt seine Aufgabe: die Chunk-Verkettung hält ihn ohnehin vorn.
        List<TaskChunk> vertreter = chunksByTask.values().stream()
                .filter(g -> !g.isEmpty())
                .map(g -> g.get(0))
                .filter(c -> c.placeable != null)
                .collect(Collectors.toList());
        if (vertreter.size() < 2) return;

        Map<Integer, List<TaskChunk>> gruppen = vertreter.stream()
                .collect(Collectors.groupingBy(c -> dayOrderRank(c.task, startDate),
                        LinkedHashMap::new, Collectors.toList()));

        for (List<TaskChunk> gruppe : gruppen.values()) {
            if (gruppe.size() < 2) continue;
            gruppe.sort(Comparator
                    .comparing((TaskChunk c) -> c.task.getDeadline(),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(c -> c.task.getId()));
            for (int i = 1; i < gruppe.size(); i++) {
                addSameDayOrder(model, gruppe.get(i - 1).placeable, gruppe.get(i).placeable,
                        qVars, qWeights,
                        gruppe.get(i - 1).task.getId() + "_" + gruppe.get(i).task.getId());
            }
        }
    }

    /**
     * Straft, dass {@code spaeter} am selben Tag VOR {@code frueher} beginnt.
     *
     * Die Vertauschung wird einmal reifiziert und dann mit jedem gemeinsamen Tag verknüpft.
     * Bewusst nur auf gemeinsamen Tagen: liegen die beiden an verschiedenen Tagen, ist ihre
     * Reihenfolge Sache der Dringlichkeit, und dort entscheidet sie {@link #urgencyRank}.
     */
    private void addSameDayOrder(CpModel model, Placeable frueher, Placeable spaeter,
                                 List<IntVar> qVars, List<Long> qWeights, String name) {
        BoolVar vertauscht = model.newBoolVar("vt_" + name);
        model.addGreaterOrEqual(LinearExpr.newBuilder()
                .addTerm(frueher.start, 1).addTerm(spaeter.start, -1).build(), 1)
             .onlyEnforceIf(vertauscht);
        model.addLessOrEqual(LinearExpr.newBuilder()
                .addTerm(frueher.start, 1).addTerm(spaeter.start, -1).build(), 0)
             .onlyEnforceIf(vertauscht.not());

        for (Map.Entry<Integer, BoolVar> e : frueher.inDay.entrySet()) {
            BoolVar bTag = spaeter.inDay.get(e.getKey());
            if (bTag == null) continue;

            // verletzt >= aTag + bTag + vertauscht - 2. Eine untere Schranke genügt: minimiert
            // wird, also bleibt verletzt von selbst 0, wo es darf. Über inDay ist zugleich die
            // Präsenz mit abgedeckt — ein verworfener Block hat keine Tages-Boolean auf 1.
            BoolVar verletzt = model.newBoolVar("vl_" + name + "_" + e.getKey());
            model.addLessOrEqual(LinearExpr.newBuilder()
                    .addTerm(e.getValue(), 1).addTerm(bTag, 1).addTerm(vertauscht, 1)
                    .addTerm(verletzt, -1).build(), 2);
            qVars.add(verletzt);
            qWeights.add(W_ORDER);
        }
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
     * übrigen Gewichte gerechnet — seit dem Umbau auf strikte Priorität konservativ gegen eine
     * VOLLE Tagesspanne (96 Slots) statt gegen die Arbeitszeit, damit die Rechnung nicht von den
     * eingestellten Arbeitszeiten abhängt:
     * <ul>
     *   <li>{@code 1200 > dayOrderRank_max * SLOTS_PER_DAY = 5*96 = 480} — "früher am Tag ist
     *       besser" ordnet, wirft aber keinen fertigen Plan um.</li>
     *   <li>{@code 1200 > W_ORDER = 200} — dasselbe für die Reihenfolge-Präferenz.</li>
     *   <li>{@code 1200 > W_PEAK * prio_max * SLOTS_PER_DAY = 2*5*96 = 960} — das Leistungshoch
     *       kann einen liegenden Block nie allein bewegen. Damit ist das Springen weg.</li>
     *   <li>{@code 1200 > urgencyRank_max * (SLOTS_PER_DAY/4) = 22*24 = 528} — "früher ist besser"
     *       wirft einen fertigen Plan nicht mehr um.</li>
     *   <li>{@code 1200 < W_LATE * prio * SLOTS_PER_DAY >= 3840} — ein ganzer Tag Verspätung
     *       erzwingt die Bewegung weiterhin, für jede Priorität.</li>
     * </ul>
     * Der Wert stand lange auf 400 und war gegen die damals viel flacheren Ordnungsterme gerechnet
     * (Deadline-Stufe 1..3 statt eines Prioritätsrangs). Mit den neuen Rängen wäre er zu klein: der
     * Kalender hätte wieder angefangen zu springen. Die Totzone ist also kein freier Parameter,
     * sondern das Maximum aller Terme, die einen Block bewegen dürfen SOLLEN, außer der Deadline.
     *
     * <p><b>Warum nicht höher.</b> Ein Versuch mit 2400 sollte {@link #dayOrderRank} mehr Auflösung
     * für die Deadline geben. Arithmetisch ging das auf, praktisch nicht: der Ordnungsterm überbot
     * damit das Leistungshoch, und die Wunschzeit-Einstellung hatte keine Wirkung mehr. Beide Terme
     * wirken pro Slot und konkurrieren deshalb direkt um denselben Spielraum. Gelöst wurde das
     * nicht durch mehr Spielraum, sondern indem die Reihenfolge aus dem Slot-Gewicht herauswanderte
     * (siehe {@link #addTaskOrderPreference}) — seither ist der Spielraum reichlich.
     *
     * Nebenwirkung, gemessen erwünscht: eine höhere Totzone lässt Phase 2 im Wiederholungslauf
     * früher in den Stillstandsabbruch laufen — der Warmlauf wird davon eher schneller.
     *
     * Die Wegstrecke bleibt zusätzlich im Ziel: muss ein Block weichen, soll er möglichst nah an
     * seiner alten Lage landen und nicht irgendwo.
     */
    private void addStabilityTerm(CpModel model, Placeable p, Integer previousSlot, Axis axis,
                                  List<IntVar> qVars, List<Long> qWeights, String name) {
        addStabilityTerm(model, p, previousSlot, axis, qVars, qWeights, name, W_MOVE_FIXED, W_MOVE);
    }

    /**
     * Wie oben, mit eigenen Gewichten — für den Vorlauf, der eine ANDERE Abwägung braucht.
     *
     * Dort ist die Totzone ausdrücklich 0: ein überfälliger Block soll jeden früheren Slot
     * gewinnen dürfen, der frei geworden ist. Übrig bleibt die Wegstrecke mit Gewicht 1, und die
     * wirkt nur noch als Gleichstandsbrecher zwischen ansonsten gleichwertigen Lagen.
     */
    private void addStabilityTerm(CpModel model, Placeable p, Integer previousSlot, Axis axis,
                                  List<IntVar> qVars, List<Long> qWeights, String name,
                                  long fixedWeight, long perSlotWeight) {
        if (previousSlot == null) return;
        p.previousSlot = previousSlot;

        // abs() verträgt kein Enforcement-Literal, deshalb wird die Differenz vorher gegated.
        IntVar diff = model.newIntVar(-axis.horizonSlots, axis.horizonSlots, "diff_" + name);
        model.addEquality(diff, LinearExpr.newBuilder().addTerm(p.start, 1).add(-previousSlot).build())
             .onlyEnforceIf(p.present);
        model.addEquality(diff, 0).onlyEnforceIf(p.present.not());

        IntVar move = model.newIntVar(0, axis.horizonSlots, "move_" + name);
        model.addAbsEquality(move, diff);

        // Die Totzone braucht eine eigene Boolean; bei Gewicht 0 wäre sie nur totes Modell.
        if (fixedWeight > 0) {
            BoolVar moved = model.newBoolVar("moved_" + name);
            model.addEquality(move, 0).onlyEnforceIf(moved.not());
            model.addGreaterOrEqual(move, 1).onlyEnforceIf(moved);
            qVars.add(moved);
            qWeights.add(fixedWeight);
        }
        qVars.add(move);
        qWeights.add(perSlotWeight);
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

    /**
     * Liest die Lösung des HAUPTLAUFS aus.
     *
     * Die Task-Blöcke entstehen hier bewusst noch nicht: was ein Task-Chunk bekommen hat, kann
     * sich in den Nachläufen ({@link #solveReliefPass}) noch ändern, und erst danach steht fest,
     * was wirklich fehlt. Deshalb landet die Platzierung nur im Chunk selbst; die Items baut
     * {@link #buildTaskItems}, die Meldungen {@link #classifyAtRisk} — beides nach allen Pässen.
     */
    /**
     * Woher die Platzierung eines {@link Placeable} kommt.
     *
     * <p>Im Regelfall direkt aus dem Löser. Scheitert Phase 2, steht dort aber keine gültige
     * Belegung mehr - dann liefert der Schnappschuss aus Phase 1. Beide liefern dieselben zwei
     * Werte, deshalb reicht diese schmale Abstraktion statt zweier Auslesepfade.
     */
    private interface Platzierung {
        boolean vorhanden(Placeable p);

        int startSlot(Placeable p);
    }

    /** Liest direkt aus dem Löser - der Normalfall nach einer erfolgreichen Phase 2. */
    private static Platzierung ausLoeser(CpSolver solver) {
        return new Platzierung() {
            @Override
            public boolean vorhanden(Placeable p) {
                return Boolean.TRUE.equals(solver.booleanValue(p.present));
            }

            @Override
            public int startSlot(Placeable p) {
                return (int) solver.value(p.start);
            }
        };
    }

    /**
     * Friert die aktuelle Lösung ein, damit sie eine spätere Modelländerung überlebt.
     *
     * <p>{@link IdentityHashMap}, weil {@link Placeable} kein {@code equals} hat und auch keins
     * haben soll: zwei Blöcke gleicher Länge am selben Tag sind verschiedene Blöcke.
     */
    private static Platzierung schnappschuss(CpSolver solver, List<Placeable> alle) {
        Map<Placeable, int[]> werte = new IdentityHashMap<>();
        for (Placeable p : alle) {
            werte.put(p, new int[]{
                    Boolean.TRUE.equals(solver.booleanValue(p.present)) ? 1 : 0,
                    (int) solver.value(p.start)});
        }
        return new Platzierung() {
            @Override
            public boolean vorhanden(Placeable p) {
                int[] v = werte.get(p);
                return v != null && v[0] == 1;
            }

            @Override
            public int startSlot(Placeable p) {
                int[] v = werte.get(p);
                return v == null ? 0 : v[1];
            }
        };
    }

    private SolveOutcome extract(Platzierung platzierung, CpSolverStatus status, List<TaskChunk> chunks,
                                 List<HabitSlot> habitSlots, List<WorkoutSession> flexibleWorkouts,
                                 Map<Long, Placeable> workoutPlaceables,
                                 List<ProjectSlot> projectSlots, Axis axis) {
        List<ScheduledItem> items  = new ArrayList<>();
        List<AtRiskItem>    atRisk = new ArrayList<>();

        // --- Tasks: nur festhalten, wo der Hauptlauf sie hingelegt hat ---
        for (TaskChunk c : chunks) {
            if (c.placeable != null && platzierung.vorhanden(c.placeable)) {
                c.placedStartSlot = platzierung.startSlot(c.placeable);
                c.reliefLevel = 0;
            }
        }

        // --- Habits ---
        for (HabitSlot s : habitSlots) {
            if (s.placeable == null || !platzierung.vorhanden(s.placeable)) {
                atRisk.add(AtRiskItem.forHabit(s.habit.getId(), s.habit.getName(),
                        s.durationMinutes, AtRiskReason.NO_ROOM));
                continue;
            }
            LocalDateTime start = axis.timeOf(platzierung.startSlot(s.placeable));
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
            if (p == null || !platzierung.vorhanden(p)) continue;

            LocalDateTime start = axis.timeOf(platzierung.startSlot(p));
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
            if (s.placeable == null || !platzierung.vorhanden(s.placeable)) continue;

            LocalDateTime start = axis.timeOf(platzierung.startSlot(s.placeable));
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

    /**
     * Baut aus den platzierten Chunks die Task-Blöcke — nach allen Pässen, nicht vorher.
     *
     * Blöcke werden bewusst NICHT zusammengefasst, auch wenn sie exakt aneinandergrenzen. Bei
     * leerem Kalender packt der Solver alle Chunks hintereinander; würde man die dann verschmelzen,
     * wäre vom Chunking genau im häufigsten Fall nichts mehr zu sehen.
     */
    private List<ScheduledItem> buildTaskItems(List<TaskChunk> chunks, Axis axis,
                                               int deadlineBufferSlots) {
        List<ScheduledItem> items = new ArrayList<>();
        Map<Long, List<TaskChunk>> byTask = chunks.stream()
                .filter(c -> c.placedStartSlot != null)
                .collect(Collectors.groupingBy(c -> c.task.getId(), LinkedHashMap::new, Collectors.toList()));

        for (List<TaskChunk> group : byTask.values()) {
            group.sort(Comparator.comparingInt(c -> c.placedStartSlot));
            for (int i = 0; i < group.size(); i++) {
                TaskChunk c = group.get(i);
                LocalDateTime start = axis.timeOf(c.placedStartSlot);

                ScheduledItem item = new ScheduledItem();
                item.setTask(c.task);
                item.setStartTime(start);
                item.setEndTime(start.plusMinutes(c.durationMinutes));   // echte Dauer
                item.setType(ScheduledItemType.TASK);
                item.setChunkIndex(i + 1);
                item.setChunkCount(group.size());
                item.setReliefLevel(c.reliefLevel);

                // Ehrlichkeit im Kalender: liegt das Ende hinter dem Pufferziel, ist die Aufgabe
                // zwar rechtzeitig geplant, aber ohne Reserve. Bewusst am tatsächlichen Ende
                // gemessen und nicht am reliefLevel — der sagt nur, WELCHER Pass den Block
                // untergebracht hat, nicht, wie knapp es geworden ist.
                if (deadlineBufferSlots > 0 && c.task.getDeadline() != null) {
                    int endSlot  = c.placedStartSlot + Axis.slotsFor(c.durationMinutes);
                    int zielSlot = axis.floorSlot(c.task.getDeadline()) - deadlineBufferSlots;
                    item.setInsideDeadlineBuffer(endSlot > zielSlot);
                }
                items.add(item);
            }
        }
        return items;
    }

    /**
     * Was der Nutzer über seine Aufgaben erfahren muss — genau ein Eintrag pro Task.
     *
     * Vorher konnte ein Task mit drei Chunks bis zu vier Meldungen erzeugen; in der Oberfläche
     * las sich das wie vier verschiedene Probleme.
     *
     * Die Einstufung läuft erst NACH den Nachläufen und unterscheidet drei Lagen, die vorher in
     * einem Topf lagen und alle als "passt nicht in den Plan" beim Nutzer ankamen:
     * <ul>
     *   <li>{@code PAST_DEADLINE} — die Deadline ist bereits gerissen. Wird auch dann gemeldet,
     *       wenn jeder Block untergebracht ist: der Termin ist vorbei, das bleibt der wichtigere
     *       Befund. Die Blöcke liegen dann im Nachhol-Fenster (siehe {@link #CATCHUP_DAYS}).</li>
     *   <li>{@code WOULD_MISS_DEADLINE} — es fehlen Minuten und es gibt eine Deadline in der
     *       Zukunft. Das ist jetzt eine ECHTE Aussage: bis dorthin war weder in der Arbeitszeit
     *       noch in den gelockerten Zeiten des Quetsch-Nachlaufs noch Platz.</li>
     *   <li>{@code OUTSIDE_HORIZON} — es fehlen Minuten, aber es gibt keine Deadline. Hier ist
     *       gar nichts in Gefahr: die Aufgabe hat im Nahbereich ({@link #taskHorizonDays}) keinen
     *       Platz gefunden und kommt schlicht später dran. Vorher stand hier {@code NO_ROOM} und
     *       damit ein Warnband für eine Aufgabe, die keinen Termin reißen kann.</li>
     * </ul>
     */
    private List<AtRiskItem> classifyAtRisk(List<TaskChunk> chunks, Axis axis, LocalDateTime cutoff) {
        List<AtRiskItem> out = new ArrayList<>();
        Map<Long, List<TaskChunk>> byTask = chunks.stream()
                .collect(Collectors.groupingBy(c -> c.task.getId(), LinkedHashMap::new, Collectors.toList()));

        for (List<TaskChunk> group : byTask.values()) {
            Task task = group.get(0).task;
            int missingMinutes = group.stream()
                    .filter(c -> c.placedStartSlot == null)
                    .mapToInt(c -> c.durationMinutes)
                    .sum();

            AtRiskReason reason = null;
            if (task.getDeadline() != null && task.getDeadline().isBefore(cutoff)) {
                reason = AtRiskReason.PAST_DEADLINE;
            } else if (missingMinutes > 0) {
                reason = task.getDeadline() != null
                        ? AtRiskReason.WOULD_MISS_DEADLINE
                        : AtRiskReason.OUTSIDE_HORIZON;
            }
            if (reason == null) continue;

            // Der früheste Block, den dieser Lauf für die Aufgabe vorgesehen hat. Bei einer
            // überfälligen Aufgabe ist das der Nachholtermin — die eigentliche Antwort auf
            // "und was jetzt?".
            Integer ersterSlot = group.stream()
                    .map(c -> c.placedStartSlot)
                    .filter(Objects::nonNull)
                    .min(Integer::compare)
                    .orElse(null);
            LocalDateTime plannedStart = ersterSlot == null ? null : axis.timeOf(ersterSlot);

            out.add(AtRiskItem.forTask(task.getId(), task.getTitle(), missingMinutes, reason,
                    plannedStart));
        }
        return out;
    }

    // =========================================================================
    // VORLAUF: ÜBERFÄLLIGES
    // =========================================================================

    /**
     * Eskalationsstufen des Nachhol-Fensters, in Tagen ab jetzt.
     *
     * {@link #CATCHUP_DAYS} bleibt der WUNSCH — "überfällig" heißt "jetzt", nicht "irgendwann
     * diesen Monat". Es ist nur keine Decke mehr: vorher war die 3 eine harte Grenze, und war in
     * diesen drei Tagen nichts frei, bekam die Aufgabe GAR KEINEN Nachholtermin, sondern nur eine
     * Meldung. Genau das war die Beschwerde. Jede weitere Stufe läuft nur, wenn die vorige nicht
     * alles untergebracht hat — im Regelfall wird nur die erste je gerechnet.
     */
    private static final int[] OVERDUE_WINDOW_DAYS = { CATCHUP_DAYS, 7, 14 };

    /**
     * Platziert überfällige Aufgaben VOR allem anderen und so früh wie möglich.
     *
     * <p><b>Warum ein Vorlauf und nicht ein stärkeres Gewicht im Hauptlauf.</b> Bis hierher lief
     * Überfälliges im Hauptmodell mit — in der Arbeitszeit, unter {@link #addDailyLoadLimits} und
     * in Konkurrenz zu allem anderen. Fiel es dort heraus, blieben nur die Nachläufe, und die
     * können ausdrücklich <em>nur auffüllen, nie verdrängen</em> (siehe {@link #solveReliefPass}).
     * Waren die nächsten Tage mit Gewohnheiten, Trainings und nicht einmal überfälligen Aufgaben
     * belegt, bekam der gerissene Termin nichts. Ein größeres Gewicht hätte daran nichts geändert:
     * das Problem war die Reihenfolge, nicht die Höhe.
     *
     * <p>Hier ist es umgekehrt. Der Pass sieht nur, was wirklich unverrückbar ist — Vergangenes,
     * Gepinntes, Vorlesungen, feste Trainings ({@code blocked}) —, legt seine Blöcke in die
     * frühesten freien Slots und hängt sie an {@code blocked} an. Das Hauptmodell findet die Zeit
     * anschließend als belegt vor und plant alles andere darum herum. Verdrängen wird damit zur
     * Nebenwirkung der Reihenfolge und braucht keine eigene Regel.
     *
     * <p>Gebrochen werden dabei genau zwei Regeln, beide bewusst:
     * <ul>
     *   <li><b>Die Arbeitszeit</b> — es gilt das gelockerte Fenster {@code relief-day-start/-end}
     *       (07–22). Der Termin ist gerissen; ein Block um halb neun abends ist das kleinere Übel.
     *       Die Nacht bleibt trotzdem tabu, sonst "rettet" der Löser jede Deadline um vier Uhr früh.</li>
     *   <li><b>Die Tagesdeckel</b> — {@code addDailyLoadLimits} läuft hier nicht. Die Begründung
     *       steht schon an {@link #addReliefDayCaps}: die Alternative zum überzogenen Tagesdeckel
     *       ist kein entspannter Tag, sondern ein Nachholtermin, den es gar nicht gibt.</li>
     * </ul>
     * {@code maxChunksPerDay} gilt dagegen weiter. Es ist kein Kapazitätsdeckel, sondern eine
     * Aussage über DIESE Aufgabe ("höchstens zwei Blöcke am Tag"), und sie zu ignorieren würde
     * acht Stunden desselben Tasks auf heute stapeln — das ist kein Nachholen, das ist ein
     * unbrauchbarer Tag.
     *
     * @param blocked wird um die neu belegten Slots ERWEITERT
     * @return wie viele Chunks der Vorlauf untergebracht hat
     */
    private int solveOverduePass(List<TaskChunk> chunks, List<int[]> blocked, Axis axis,
                                 UserPreferences prefs, int nowSlot, int gapSlots,
                                 Map<String, Integer> previousStarts) {
        List<TaskChunk> ueberfaellig = chunks.stream()
                .filter(c -> istUeberfaellig(c, axis, nowSlot))
                .collect(Collectors.toList());
        if (ueberfaellig.isEmpty()) return 0;

        // Index innerhalb der eigenen Aufgabe — der Schlüssel des Stabilitätsankers.
        Map<TaskChunk, Integer> chunkIndex = new HashMap<>();
        Map<Long, Integer> laufend = new HashMap<>();
        for (TaskChunk c : chunks) {
            chunkIndex.put(c, laufend.merge(c.task.getId(), 0, (a, b) -> a + 1));
        }

        int[] fenster = tagesFenster(prefs);
        int platziert = 0;
        for (int fensterTage : OVERDUE_WINDOW_DAYS) {
            List<TaskChunk> offen = ueberfaellig.stream()
                    .filter(c -> c.placedStartSlot == null)
                    .collect(Collectors.toList());
            if (offen.isEmpty()) break;
            platziert += solveOverdueWindow(offen, blocked, axis, nowSlot, gapSlots,
                    fenster[0], fenster[1], fensterTage, chunkIndex, previousStarts);
        }

        if (platziert < ueberfaellig.size()) {
            log.warn("Vorlauf: {} von {} überfälligen Blöcken konnten auch in {} Tagen nicht "
                            + "untergebracht werden", ueberfaellig.size() - platziert,
                    ueberfaellig.size(), OVERDUE_WINDOW_DAYS[OVERDUE_WINDOW_DAYS.length - 1]);
        }
        return platziert;
    }

    /** Das gelockerte Tagesfenster (07–22), gemeinsam genutzt von Vorlauf und Quetsch-Nachlauf. */
    private int[] tagesFenster(UserPreferences prefs) {
        int lo = minuteOfDay(parseZeit(reliefDayStart, LocalTime.of(7, 0))) / GRID;
        int hi = minuteOfDay(parseZeit(reliefDayEnd, LocalTime.of(22, 0))) / GRID;
        if (hi <= lo) {   // defensiv gegen Fehlkonfiguration
            lo = 0;
            hi = SLOTS_PER_DAY;
        }
        return new int[]{ lo, hi };
    }

    /** Eine Eskalationsstufe des Vorlaufs: ein eigenes, winziges Modell über {@code fensterTage}. */
    private int solveOverdueWindow(List<TaskChunk> offen, List<int[]> blocked, Axis axis,
                                   int nowSlot, int gapSlots, int dayStartSlot, int dayEndSlot,
                                   int fensterTage, Map<TaskChunk, Integer> chunkIndex,
                                   Map<String, Integer> previousStarts) {
        int lastDay = Math.min(axis.totalDays - 1, nowSlot / SLOTS_PER_DAY + fensterTage);

        CpModel model = new CpModel();
        List<IntervalVar> intervals = new ArrayList<>();
        for (int i = 0; i < blocked.size(); i++) {
            int[] b = blocked.get(i);
            intervals.add(model.newFixedInterval(b[0], Math.max(1, b[1] - b[0]), "belegt_" + i));
        }

        LinearExprBuilder dropB = LinearExpr.newBuilder();
        long dropConst = 0;
        List<IntVar> qVars    = new ArrayList<>();
        List<Long>   qWeights = new ArrayList<>();
        List<Placeable> placeables = new ArrayList<>();

        for (int i = 0; i < offen.size(); i++) {
            TaskChunk c = offen.get(i);
            int sizeSlots = Axis.slotsFor(c.durationMinutes);

            // notBefore gilt auch hier. Eine überfällige Aufgabe, die vor Montag nicht anfangen
            // darf, kann man am Sonntag nicht nachholen — das ist keine Regel des Kalenders,
            // sondern eine der Sache selbst.
            int earliest = nowSlot;
            if (c.task.getNotBefore() != null) {
                earliest = Math.max(earliest, axis.ceilSlot(c.task.getNotBefore()));
            }

            List<DayWindow> windows = dayWindows(axis, dayStartSlot, dayEndSlot, sizeSlots,
                    earliest, null, lastDay);
            if (windows.isEmpty()) {
                placeables.add(null);
                continue;
            }
            Placeable p = makePlaceable(model, "overdue" + fensterTage + "_" + i, sizeSlots,
                    c.durationMinutes, windows, gapSlots);
            placeables.add(p);
            intervals.add(p.interval);

            // Platzieren: allein die Priorität. Strikt, wie überall seit dem Umbau — konkurrieren
            // zwei überfällige Aufgaben um den letzten Slot, gewinnt die wichtigere, und die Frage
            // "wer ist länger überfällig" entscheidet erst danach über die Reihenfolge.
            int prio = Math.max(1, nz(c.task.getPriority(), 3));
            dropB.addTerm(p.present, -(long) prio);
            dropConst += prio;

            // So früh wie möglich, priorisiert. Der Rang ist so gebaut, dass die Überfälligkeit
            // die Priorität nicht überholen kann: prio*4 spannt 4..20, der Zuschlag 0..3.
            qVars.add(gated(model, p, "ovfrueh" + fensterTage + "_" + i, p.start, axis.horizonSlots));
            qWeights.add((long) prio * 4 + ueberfaelligkeitsRang(c.task, axis, nowSlot));

            // Stabilität nur als Gleichstandsbrecher, mit Gewicht 1 und ausdrücklich OHNE Totzone:
            // ein Nachholtermin soll nicht bei jedem Lauf springen, aber einen frei gewordenen
            // früheren Slot soll er jederzeit gewinnen dürfen. Das Frühseins-Gewicht ist mit
            // mindestens 4 pro Slot immer stärker.
            addStabilityTerm(model, p, previousStarts.get(
                            "task:" + c.task.getId() + ":" + chunkIndex.get(c)),
                    axis, qVars, qWeights, "ov" + fensterTage + "_" + i, 0, 1);
        }
        if (placeables.stream().allMatch(Objects::isNull)) return 0;

        addOverdueChunkOrder(model, offen, placeables);
        model.addNoOverlap(intervals.toArray(new IntervalVar[0]));

        // Lexikografisch über EIN Ziel, exakt wie im Nachlauf: der Faktor ist die berechnete
        // Obergrenze des zweitrangigen Ziels plus eins, also nicht geschätzt. Platzieren schlägt
        // Frühsein damit immer — ein Block am Freitag ist besser als gar keiner am Dienstag.
        long qMax = 0;
        for (long w : qWeights) qMax += w * axis.horizonSlots;
        LinearExprBuilder ziel = LinearExpr.newBuilder();
        ziel.addTerm(dropB.add(dropConst).build(), qMax + 1);
        for (int i = 0; i < qVars.size(); i++) ziel.addTerm(qVars.get(i), qWeights.get(i));
        model.minimize(ziel.build());

        CpSolver solver = new CpSolver();
        solver.getParameters().setLogSearchProgress(false);
        solver.getParameters().setRandomSeed(42);
        solver.getParameters().setNumSearchWorkers(solverWorkersPhase1);
        solver.getParameters().setMaxTimeInSeconds(Math.max(0.05, reliefTimeLimitSeconds));
        CpSolverStatus status = solver.solve(model);
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            log.warn("Vorlauf über {} Tage ohne Lösung: {}", fensterTage, status);
            return 0;
        }

        int platziert = 0;
        for (int i = 0; i < offen.size(); i++) {
            Placeable p = placeables.get(i);
            if (p == null || !Boolean.TRUE.equals(solver.booleanValue(p.present))) continue;

            TaskChunk c = offen.get(i);
            c.placedStartSlot = (int) solver.value(p.start);
            // Kein Notbehelf: für Überfälliges IST das der Regelweg. reliefLevel bleibt 0, damit
            // im Kalender kein "eng geplant"-Hinweis erscheint.
            c.reliefLevel = 0;
            blocked.add(new int[]{ c.placedStartSlot,
                    c.placedStartSlot + Axis.slotsFor(c.durationMinutes) + gapSlots });
            platziert++;
        }
        if (platziert > 0) {
            log.info("Vorlauf über {} Tage: {} von {} überfälligen Blöcken platziert",
                    fensterTage, platziert, offen.size());
        }
        return platziert;
    }

    /**
     * Chronologische Ordnung und {@code maxChunksPerDay} für die Chunks derselben Aufgabe.
     *
     * Die Ordnung ist reine Symmetriebrechung — zwei gleich große Chunks derselben Aufgabe sind
     * austauschbar, und ohne diese Constraint sucht der Löser jede Permutation davon ab.
     */
    private void addOverdueChunkOrder(CpModel model, List<TaskChunk> offen,
                                      List<Placeable> placeables) {
        Map<Long, List<Integer>> proTask = new LinkedHashMap<>();
        for (int i = 0; i < offen.size(); i++) {
            if (placeables.get(i) != null) {
                proTask.computeIfAbsent(offen.get(i).task.getId(), k -> new ArrayList<>()).add(i);
            }
        }

        for (Map.Entry<Long, List<Integer>> e : proTask.entrySet()) {
            List<Integer> idx = e.getValue();
            for (int k = 1; k < idx.size(); k++) {
                Placeable prev = placeables.get(idx.get(k - 1));
                Placeable cur  = placeables.get(idx.get(k));
                model.addLessOrEqual(
                        LinearExpr.newBuilder().addTerm(prev.start, 1).add(prev.sizeSlots).build(),
                        cur.start).onlyEnforceIf(new Literal[]{ prev.present, cur.present });
                model.addImplication(cur.present, prev.present);
            }

            Integer perDay = offen.get(idx.get(0)).task.getMaxChunksPerDay();
            if (perDay == null || perDay <= 0 || idx.size() <= perDay) continue;
            Map<Integer, LinearExprBuilder> proTag = new LinkedHashMap<>();
            for (int i : idx) {
                placeables.get(i).inDay.forEach((tag, b) ->
                        proTag.computeIfAbsent(tag, k -> LinearExpr.newBuilder()).addTerm(b, 1));
            }
            for (LinearExprBuilder b : proTag.values()) model.addLessOrEqual(b.build(), perDay);
        }
    }

    // =========================================================================
    // LETZTE STUFE: VERDRÄNGEN
    // =========================================================================

    /**
     * Bringt eine Aufgabe unter, deren Deadline sonst reißt — notfalls auf Kosten anderer Blöcke.
     *
     * <p><b>Warum es diesen Pass braucht.</b> Der Hauptlauf wägt Aufgaben und wiederkehrende Items
     * gegeneinander ab und lässt jede Aufgabe gewinnen (Drop-Gewicht 1500+ gegen höchstens 300).
     * Das hilft aber nur, solange die Aufgabe im Hauptlauf überhaupt ein zulässiges Fenster hat.
     * Hatte sie keines — Deadline zu nah, Nahbereich voll, Tagesdeckel erreicht —, fiel sie heraus,
     * und ab da konnten die Nachläufe sie nur noch in Lücken legen: sie sehen alles Bestehende als
     * FEST. War die Zeit vor der Deadline an Meditation, Lesen und Projektzeit vergeben, gab es
     * keine Lücke mehr, und die App meldete eine gerissene Deadline, obwohl im Kalender lauter
     * Verzichtbares stand.
     *
     * <p>Hier ist diese Annahme aufgehoben. Im Fenster bis zur Deadline gilt:
     * <ul>
     *   <li><b>Fest</b> bleibt, was der Nutzer selbst gesetzt hat oder was nicht nachholbar ist:
     *       {@code blocked} (gepinnt, Vergangenheit, Vorlesungen) und jede Aufgabe mit gleicher
     *       oder höherer Priorität. Eine Deadline zu retten, indem man eine wichtigere reißt,
     *       ist kein Fortschritt.</li>
     *   <li><b>Verschiebbar oder verzichtbar</b> ist alles Wiederkehrende (Gewohnheit, Training,
     *       Projektzeit) und jede Aufgabe mit STRIKT niedrigerer Priorität. Es darf innerhalb des
     *       Fensters umziehen; erst wenn auch das nicht reicht, fällt es weg.</li>
     * </ul>
     *
     * <p>Die Tagesdeckel gelten hier nicht — aus demselben Grund wie beim Überfälligen: die
     * Alternative zum vollen Tag ist kein entspannter Tag, sondern eine gerissene Deadline.
     *
     * <p>Was wegfällt, verschwindet nicht stillschweigend: es wird als {@link AtRiskItem} mit
     * {@link AtRiskReason#NO_ROOM} gemeldet und taucht damit in derselben Liste auf wie eine
     * Gewohnheit, für die der Hauptlauf keinen Platz fand.
     *
     * @return wie viele bestehende Blöcke verschoben oder verworfen wurden
     */
    private int solveDeadlineRescuePass(List<TaskChunk> chunks, SolveOutcome outcome,
                                        List<int[]> blocked, Axis axis, UserPreferences prefs,
                                        int nowSlot) {
        // Nur Aufgaben, deren Termin in der Zukunft liegt und die noch Minuten schuldig sind.
        // Überfälliges hat der Vorlauf; ohne Deadline ist nichts in Gefahr.
        List<TaskChunk> gefaehrdet = chunks.stream()
                .filter(c -> c.placedStartSlot == null)
                .filter(c -> c.task.getDeadline() != null)
                .filter(c -> !istUeberfaellig(c, axis, nowSlot))
                .collect(Collectors.toList());
        if (gefaehrdet.isEmpty()) return 0;

        // Höchste betroffene Priorität und spätestes Fenster bestimmen den Zuschnitt: ein Pass für
        // alle gefährdeten Aufgaben zusammen, statt einer pro Aufgabe, der dem nächsten die Zeit
        // wieder wegnimmt.
        int hoechstePrio = gefaehrdet.stream().mapToInt(c -> prioClamped(c.task)).max().orElse(3);
        int fensterEnde  = gefaehrdet.stream()
                .mapToInt(c -> axis.floorSlot(c.task.getDeadline())).max().orElse(nowSlot);
        if (fensterEnde <= nowSlot) return 0;

        int[] fenster = tagesFenster(prefs);

        CpModel model = new CpModel();
        List<IntervalVar> intervals = new ArrayList<>();
        for (int i = 0; i < blocked.size(); i++) {
            int[] b = blocked.get(i);
            intervals.add(model.newFixedInterval(b[0], Math.max(1, b[1] - b[0]), "fest_" + i));
        }

        // --- Was im Fenster liegt und weichen darf ---
        List<Verdraengbar> verdraengbar = new ArrayList<>();
        for (ScheduledItem item : outcome.getItems()) {
            // extract liefert hier ausschließlich Wiederkehrendes; Task-Blöcke stecken noch in den
            // Chunks und werden unten getrennt behandelt.
            int start = axis.floorSlot(item.getStartTime());
            if (start < nowSlot || start >= fensterEnde) continue;
            int dauer = (int) ChronoUnit.MINUTES.between(item.getStartTime(), item.getEndTime());
            verdraengbar.add(new Verdraengbar(item, null, start, Axis.slotsFor(dauer),
                    dropGewichtVon(item)));
        }
        for (TaskChunk c : chunks) {
            if (c.placedStartSlot == null) continue;
            if (c.placedStartSlot < nowSlot || c.placedStartSlot >= fensterEnde) continue;
            // Gleiche Priorität reicht NICHT: sonst schiebt sich ein Gleichrangiger gegenseitig aus
            // dem Kalender, je nachdem welcher zufällig zuerst drankommt.
            if (prioClamped(c.task) >= hoechstePrio) continue;
            verdraengbar.add(new Verdraengbar(null, c, c.placedStartSlot,
                    Axis.slotsFor(c.durationMinutes), calculateTaskWeight(c.task, axis.origin.toLocalDate())));
        }

        // Alles Übrige, was schon liegt, ist in diesem Modell fest.
        Set<ScheduledItem> beweglicheItems = identitaetsMenge(verdraengbar, v -> v.item);
        Set<TaskChunk> beweglicheChunks = identitaetsMenge(verdraengbar, v -> v.chunk);
        int festIdx = blocked.size();
        for (ScheduledItem item : outcome.getItems()) {
            if (beweglicheItems.contains(item)) continue;
            int s = axis.floorSlot(item.getStartTime());
            int d = Axis.slotsFor((int) ChronoUnit.MINUTES.between(item.getStartTime(), item.getEndTime()));
            intervals.add(model.newFixedInterval(s, Math.max(1, d), "fest_i" + (festIdx++)));
        }
        for (TaskChunk c : chunks) {
            if (c.placedStartSlot == null || beweglicheChunks.contains(c)) continue;
            intervals.add(model.newFixedInterval(c.placedStartSlot,
                    Math.max(1, Axis.slotsFor(c.durationMinutes)), "fest_c" + (festIdx++)));
        }

        // --- Die gefährdeten Chunks ---
        LinearExprBuilder rettenB = LinearExpr.newBuilder();
        long rettenConst = 0;
        List<Placeable> gefaehrdetP = new ArrayList<>();
        for (int i = 0; i < gefaehrdet.size(); i++) {
            TaskChunk c = gefaehrdet.get(i);
            int sizeSlots    = Axis.slotsFor(c.durationMinutes);
            int deadlineSlot = axis.floorSlot(c.task.getDeadline());
            int earliest     = nowSlot;
            if (c.task.getNotBefore() != null) {
                earliest = Math.max(earliest, axis.ceilSlot(c.task.getNotBefore()));
            }
            List<DayWindow> windows = dayWindows(axis, fenster[0], fenster[1], sizeSlots, earliest,
                    null, Math.min(axis.totalDays - 1, deadlineSlot / SLOTS_PER_DAY), deadlineSlot);
            if (windows.isEmpty()) {
                gefaehrdetP.add(null);
                continue;
            }
            // Gap 0: dieser Pass läuft, wenn es sonst nicht mehr geht.
            Placeable p = makePlaceable(model, "rettung_" + i, sizeSlots, c.durationMinutes, windows, 0);
            gefaehrdetP.add(p);
            intervals.add(p.interval);
            rettenB.addTerm(p.present, -1L);
            rettenConst += 1;
        }
        if (gefaehrdetP.stream().allMatch(Objects::isNull)) return 0;

        // --- Die Verdrängbaren: umziehen oder wegfallen ---
        LinearExprBuilder opferB = LinearExpr.newBuilder();
        long opferConst = 0;
        List<IntVar> qVars    = new ArrayList<>();
        List<Long>   qWeights = new ArrayList<>();
        List<Placeable> opferP = new ArrayList<>();
        for (int i = 0; i < verdraengbar.size(); i++) {
            Verdraengbar v = verdraengbar.get(i);
            List<DayWindow> windows = dayWindows(axis, fenster[0], fenster[1], v.sizeSlots, nowSlot,
                    null, Math.min(axis.totalDays - 1, (fensterEnde - 1) / SLOTS_PER_DAY));
            if (windows.isEmpty()) {
                opferP.add(null);
                continue;
            }
            Placeable p = makePlaceable(model, "opfer_" + i, v.sizeSlots, v.sizeSlots * GRID, windows, 0);
            opferP.add(p);
            intervals.add(p.interval);
            opferB.addTerm(p.present, -v.gewicht);
            opferConst += v.gewicht;
            // Am liebsten bleibt alles, wo es ist: erst umziehen, wenn das Platz schafft, und
            // wegfallen nur, wenn auch das nicht reicht.
            addStabilityTerm(model, p, v.startSlot, axis, qVars, qWeights, "opfer_" + i, 0, 1);
        }

        model.addNoOverlap(intervals.toArray(new IntervalVar[0]));
        addRescueDayCaps(model, prefs, axis, chunks, outcome, gefaehrdet, gefaehrdetP,
                verdraengbar, opferP);

        // Lexikografisch, drei Stufen: die Deadline retten schlägt alles; danach so wenig wie
        // möglich (und so Billiges wie möglich) opfern; ganz zuletzt möglichst wenig verschieben.
        // Die Faktoren sind berechnete Obergrenzen, nicht geschätzte Größenordnungen.
        long qMax = 0;
        for (long w : qWeights) qMax += w * axis.horizonSlots;
        long opferMax = opferConst;
        LinearExprBuilder ziel = LinearExpr.newBuilder();
        ziel.addTerm(rettenB.add(rettenConst).build(), (opferMax + 1) * (qMax + 1));
        ziel.addTerm(opferB.add(opferConst).build(), qMax + 1);
        for (int i = 0; i < qVars.size(); i++) ziel.addTerm(qVars.get(i), qWeights.get(i));
        model.minimize(ziel.build());

        CpSolver solver = new CpSolver();
        solver.getParameters().setLogSearchProgress(false);
        solver.getParameters().setRandomSeed(42);
        solver.getParameters().setNumSearchWorkers(solverWorkersPhase1);
        solver.getParameters().setMaxTimeInSeconds(Math.max(0.05, reliefTimeLimitSeconds));
        CpSolverStatus status = solver.solve(model);
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            log.warn("Verdrängungs-Nachlauf ohne Lösung: {}", status);
            return 0;
        }

        // Nichts anfassen, wenn er nichts gerettet hat: sonst würden Blöcke umziehen, ohne dass es
        // irgendjemandem genützt hätte.
        int gerettet = 0;
        for (int i = 0; i < gefaehrdet.size(); i++) {
            Placeable p = gefaehrdetP.get(i);
            if (p != null && Boolean.TRUE.equals(solver.booleanValue(p.present))) gerettet++;
        }
        if (gerettet == 0) return 0;

        for (int i = 0; i < gefaehrdet.size(); i++) {
            Placeable p = gefaehrdetP.get(i);
            if (p == null || !Boolean.TRUE.equals(solver.booleanValue(p.present))) continue;
            TaskChunk c = gefaehrdet.get(i);
            c.placedStartSlot = (int) solver.value(p.start);
            c.reliefLevel = 2;   // eng geplant — der Hinweis am Block ist hier ehrlich
        }

        int bewegt = 0;
        for (int i = 0; i < verdraengbar.size(); i++) {
            Verdraengbar v = verdraengbar.get(i);
            Placeable p = opferP.get(i);
            boolean bleibt = p != null && Boolean.TRUE.equals(solver.booleanValue(p.present));

            if (!bleibt) {
                if (v.item != null) {
                    // removeIf über Identität, NICHT List.remove: ScheduledItem ist eine
                    // Lombok-@Data-Klasse und vergleicht über seine JPA-Entities weiter, die sich
                    // gegenseitig referenzieren — equals() läuft dort in einen StackOverflowError.
                    ScheduledItem weg = v.item;
                    outcome.getItems().removeIf(x -> x == weg);
                    meldeVerdraengt(outcome, weg);
                } else {
                    v.chunk.placedStartSlot = null;   // classifyAtRisk meldet es anschließend
                }
                bewegt++;
                continue;
            }
            int neu = (int) solver.value(p.start);
            if (neu == v.startSlot) continue;
            if (v.item != null) {
                int dauer = (int) ChronoUnit.MINUTES.between(v.item.getStartTime(), v.item.getEndTime());
                v.item.setStartTime(axis.timeOf(neu));
                v.item.setEndTime(axis.timeOf(neu).plusMinutes(dauer));
            } else {
                v.chunk.placedStartSlot = neu;
            }
            bewegt++;
        }

        log.info("Verdrängungs-Nachlauf: {} von {} gefährdeten Blöcken gerettet, {} bestehende "
                + "Blöcke verschoben oder verworfen", gerettet, gefaehrdet.size(), bewegt);
        return bewegt;
    }

    /**
     * Die Tagesdeckel im Verdrängungs-Modell — sie bleiben, und zwar mit Absicht.
     *
     * Der erste Entwurf ließ sie weg, wie beim Überfälligen. Das war falsch, und zwei Bestandstests
     * haben es sofort gezeigt: der Pass stopfte einen Tag mit vier Blöcken voll, wo zwei erlaubt
     * waren. Der Unterschied zum Überfälligen ist grundsätzlich — dort ist der Termin schon
     * gerissen und es gibt gar keine Alternative mehr, hier gibt es eine, und sie ist genau der
     * Zweck dieses Passes: <b>verdrängen SCHAFFT Kapazität.</b> Fällt die Meditation weg, wird ihre
     * Zeit im Deckel frei. Der Deckel steht der Rettung also gar nicht im Weg; er verhindert nur,
     * dass aus "eine Deadline retten" ein zugestellter Tag wird.
     *
     * Gezählt wird über die inDay-Booleans der beweglichen Items plus einem festen Sockel aus
     * allem, was in diesem Modell nicht mehr verhandelbar ist.
     */
    private void addRescueDayCaps(CpModel model, UserPreferences prefs, Axis axis,
                                  List<TaskChunk> chunks, SolveOutcome outcome,
                                  List<TaskChunk> gefaehrdet, List<Placeable> gefaehrdetP,
                                  List<Verdraengbar> verdraengbar, List<Placeable> opferP) {
        int taskCapSlots = Axis.slotsFor(nz(prefs.getMaxTaskMinutesPerDay(), FALLBACK_MAX_TASK_MIN_PER_DAY));
        int totalCapSlots = Axis.slotsFor(
                nz(prefs.getMaxScheduledMinutesPerDay(), FALLBACK_MAX_SCHEDULED_MIN_PER_DAY));
        Integer maxTasksPerDay = prefs.getMaxTasksPerDay();

        Set<ScheduledItem> beweglicheItems = identitaetsMenge(verdraengbar, v -> v.item);
        Set<TaskChunk> beweglicheChunks = identitaetsMenge(verdraengbar, v -> v.chunk);

        // Sockel: was an einem Tag liegt und in diesem Modell nicht mehr bewegt werden kann.
        Map<Integer, int[]> sockel = new HashMap<>();   // [taskSlots, totalSlots, taskCount]
        for (ScheduledItem item : outcome.getItems()) {
            if (beweglicheItems.contains(item)) continue;
            int start = axis.floorSlot(item.getStartTime());
            int slots = Axis.slotsFor((int) ChronoUnit.MINUTES.between(
                    item.getStartTime(), item.getEndTime()));
            int[] s = sockel.computeIfAbsent(start / SLOTS_PER_DAY, k -> new int[3]);
            s[1] += slots;
        }
        for (TaskChunk c : chunks) {
            if (c.placedStartSlot == null || beweglicheChunks.contains(c)) continue;
            int slots = Axis.slotsFor(c.durationMinutes);
            int[] s = sockel.computeIfAbsent(c.placedStartSlot / SLOTS_PER_DAY, k -> new int[3]);
            s[0] += slots;
            s[1] += slots;
            s[2] += 1;
        }

        // Beweglich: Tag -> Beiträge. Bei den gefährdeten Chunks und den verdrängbaren
        // Aufgaben-Blöcken zählt zusätzlich der Aufgaben-Deckel.
        Map<Integer, LinearExprBuilder> taskLoad  = new LinkedHashMap<>();
        Map<Integer, LinearExprBuilder> totalLoad = new LinkedHashMap<>();
        Map<Integer, LinearExprBuilder> taskCount = new LinkedHashMap<>();

        for (int i = 0; i < gefaehrdet.size(); i++) {
            Placeable p = gefaehrdetP.get(i);
            if (p == null) continue;
            zaehleProTag(p, Axis.slotsFor(gefaehrdet.get(i).durationMinutes), true,
                    taskLoad, totalLoad, taskCount);
        }
        for (int i = 0; i < verdraengbar.size(); i++) {
            Placeable p = opferP.get(i);
            if (p == null) continue;
            zaehleProTag(p, verdraengbar.get(i).sizeSlots, verdraengbar.get(i).chunk != null,
                    taskLoad, totalLoad, taskCount);
        }

        for (Map.Entry<Integer, LinearExprBuilder> e : taskLoad.entrySet()) {
            int rest = taskCapSlots - sockel.getOrDefault(e.getKey(), new int[3])[0];
            model.addLessOrEqual(e.getValue().build(), Math.max(0, rest));
        }
        for (Map.Entry<Integer, LinearExprBuilder> e : totalLoad.entrySet()) {
            int rest = totalCapSlots - sockel.getOrDefault(e.getKey(), new int[3])[1];
            model.addLessOrEqual(e.getValue().build(), Math.max(0, rest));
        }
        if (maxTasksPerDay != null && maxTasksPerDay > 0) {
            for (Map.Entry<Integer, LinearExprBuilder> e : taskCount.entrySet()) {
                int rest = maxTasksPerDay - sockel.getOrDefault(e.getKey(), new int[3])[2];
                model.addLessOrEqual(e.getValue().build(), Math.max(0, rest));
            }
        }
    }

    private void zaehleProTag(Placeable p, int slots, boolean istAufgabe,
                              Map<Integer, LinearExprBuilder> taskLoad,
                              Map<Integer, LinearExprBuilder> totalLoad,
                              Map<Integer, LinearExprBuilder> taskCount) {
        for (Map.Entry<Integer, BoolVar> e : p.inDay.entrySet()) {
            totalLoad.computeIfAbsent(e.getKey(), k -> LinearExpr.newBuilder())
                     .addTerm(e.getValue(), slots);
            if (!istAufgabe) continue;
            taskLoad.computeIfAbsent(e.getKey(), k -> LinearExpr.newBuilder())
                    .addTerm(e.getValue(), slots);
            taskCount.computeIfAbsent(e.getKey(), k -> LinearExpr.newBuilder())
                     .addTerm(e.getValue(), 1);
        }
    }

    /**
     * Eine Menge, die über IDENTITÄT vergleicht — Pflicht bei {@link ScheduledItem}.
     *
     * {@code ScheduledItem} ist eine Lombok-{@code @Data}-Klasse, ihr generiertes
     * {@code equals}/{@code hashCode} läuft über die enthaltenen JPA-Entities, und die haben
     * bidirektionale Beziehungen: Task ↔ Project, Habit ↔ Completions. Ein {@code HashSet} oder
     * ein {@code List.remove} darauf endet in einem {@code StackOverflowError} — und zwar nicht
     * als Test-Fehlschlag, sondern als HTTP 500 mitten im Lauf.
     */
    private static <T> Set<T> identitaetsMenge(List<Verdraengbar> quelle,
                                               java.util.function.Function<Verdraengbar, T> auswahl) {
        Set<T> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Verdraengbar v : quelle) {
            T t = auswahl.apply(v);
            if (t != null) out.add(t);
        }
        return out;
    }

    /** Ein bestehender Block, der einer gerissenen Deadline weichen darf. */
    private static final class Verdraengbar {
        final ScheduledItem item;    // Wiederkehrendes; genau eines von beiden ist gesetzt
        final TaskChunk chunk;       // Aufgabe mit niedrigerer Priorität
        final int startSlot;
        final int sizeSlots;
        final long gewicht;

        Verdraengbar(ScheduledItem item, TaskChunk chunk, int startSlot, int sizeSlots, long gewicht) {
            this.item = item;
            this.chunk = chunk;
            this.startSlot = startSlot;
            this.sizeSlots = sizeSlots;
            this.gewicht = gewicht;
        }
    }

    /** Dieselben Bänder wie im Hauptlauf — was dort billig zu verwerfen war, ist es auch hier. */
    private long dropGewichtVon(ScheduledItem item) {
        return switch (item.getType()) {
            case HABIT   -> item.getHabit() != null ? calculateHabitWeight(item.getHabit()) : W_DROP_HABIT_PRIO;
            case WORKOUT -> W_DROP_WORKOUT;
            case PROJECT -> W_DROP_PROJECT;
            default      -> W_DROP_PROJECT;
        };
    }

    /**
     * Ein weggefallener Block wird gemeldet, nicht verschwiegen.
     *
     * Nur Gewohnheiten bekommen einen Eintrag, und zwar denselben {@code NO_ROOM}, den auch der
     * Hauptlauf vergibt — für den Nutzer ist es dieselbe Aussage. Trainings und Projektzeit sind
     * schon im Hauptlauf stumm, wenn sie ausfallen (Wochenquote, nächste Woche wieder da); hier
     * eine Meldung zu erzeugen, die es sonst nie gibt, wäre inkonsistent.
     */
    private void meldeVerdraengt(SolveOutcome outcome, ScheduledItem item) {
        if (item.getType() != ScheduledItemType.HABIT || item.getHabit() == null) return;
        int dauer = (int) ChronoUnit.MINUTES.between(item.getStartTime(), item.getEndTime());
        outcome.getAtRisk().add(AtRiskItem.forHabit(item.getHabit().getId(),
                item.getHabit().getName(), dauer, AtRiskReason.NO_ROOM));
    }

    /**
     * Wie lange eine Aufgabe schon überfällig ist, als Rang 0..3.
     *
     * Bewusst grob: es geht nur darum, unter gleich wichtigen Aufgaben die länger liegengebliebene
     * zuerst nachzuholen. Der Wertebereich ist so gewählt, dass der Zuschlag den Prioritätsschritt
     * (4) nie überbrücken kann.
     */
    private int ueberfaelligkeitsRang(Task task, Axis axis, int nowSlot) {
        long tage = (nowSlot - axis.floorSlot(task.getDeadline())) / SLOTS_PER_DAY;
        if (tage >= 7) return 3;
        if (tage >= 3) return 2;
        if (tage >= 1) return 1;
        return 0;
    }

    // =========================================================================
    // NACHLÄUFE
    // =========================================================================

    /**
     * Die beiden Stufen, mit denen eine sonst gerissene Deadline noch gerettet wird.
     *
     * @see #solveReliefPass
     */
    private enum ReliefMode {
        /**
         * Hinter den Nahbereich, aber in ganz normale Arbeitszeit. Das behebt den häufigsten
         * Fehlalarm: {@link #taskBounds} deckelt jeden Task auf {@link #taskHorizonDays}, auch
         * wenn seine Deadline weiter draußen liegt — eine Aufgabe mit Termin in drei Wochen
         * konkurrierte deshalb um die nächsten 14 Tage und wurde gemeldet, obwohl Tag 15 bis 21
         * völlig frei waren.
         */
        CATCH_UP,
        /**
         * Gelockerte Tageszeiten und keine Pausen. Der ausdrückliche Wunsch: lieber ein Block um
         * halb neun abends als eine gerissene Deadline.
         *
         * Die Tagesdeckel bleiben trotzdem stehen (siehe {@link #addReliefDayCaps}) — sie sind
         * keine Kalenderregel, sondern eine Aussage darüber, wie viel an einem Tag überhaupt geht.
         */
        SQUEEZE
    }

    /**
     * Bringt Chunks unter, die der Hauptlauf liegen gelassen hat — ohne irgendetwas zu bewegen.
     *
     * Warum ein eigener Pass und nicht einfach ein weiteres Fenster im Hauptmodell: der
     * 14-Tage-Deckel aus {@link #taskBounds} ist der dokumentierte Laufzeit-Hebel. Macht man ihn
     * für ALLE Tasks mit Deadline auf, wächst das Modell um eine Tages-Boolean pro Chunk und
     * Horizonttag, und Phase 1 fand am echten Bestand nichts Brauchbares mehr (Drop 28200 statt
     * 800). Hier bekommt das weite Fenster dagegen nur, wer es nachweislich braucht — meist
     * niemand, dann läuft der Pass gar nicht erst.
     *
     * Alles Bestehende (Blockiertes plus alle bereits platzierten Blöcke) geht als festes
     * Intervall ein. Der Pass kann deshalb nur hinzufügen, nie umsortieren: die Arbeit des
     * Hauptlaufs — Leistungshoch, Abendstrafe, Stabilitätsanker — bleibt unangetastet.
     *
     * @param occupied wird um die neu belegten Slots ERWEITERT, damit die zweite Stufe die erste sieht
     * @return wie viele Chunks dieser Pass untergebracht hat
     */
    private int solveReliefPass(ReliefMode mode, List<TaskChunk> chunks, List<int[]> occupied,
                                Axis axis, UserPreferences prefs, int nowSlot, int gapSlots) {
        // Nur künftige Deadlines. Ohne Deadline ist nichts in Gefahr — die Aufgabe kommt beim
        // nächsten Lauf wieder dran.
        //
        // Überfälliges gehört ausdrücklich NICHT mehr hierher: das erledigt der Vorlauf
        // (solveOverduePass), und zwar gründlicher, als es diese Pässe je könnten. Sie können nur
        // auffüllen; wenn der Vorlauf mit 14 Tagen, gelockerten Zeiten und ohne Tagesdeckel keinen
        // Platz gefunden hat, findet ihn hier niemand mehr. Ein überfälliger Chunk mit leerem
        // Fenster würde unten außerdem eine leere Obergrenze bekommen (die Deadline liegt hinter
        // uns) und lautlos gar kein Fenster erhalten.
        List<TaskChunk> offen = chunks.stream()
                .filter(c -> c.placedStartSlot == null)
                .filter(c -> c.task.getDeadline() != null)
                .filter(c -> !istUeberfaellig(c, axis, nowSlot))
                .collect(Collectors.toList());
        if (offen.isEmpty()) return 0;

        int dayStartSlot;
        int dayEndSlot;
        int gap;
        if (mode == ReliefMode.CATCH_UP) {
            dayStartSlot = minuteOfDay(workStart(prefs)) / GRID;
            dayEndSlot   = minuteOfDay(workEnd(prefs)) / GRID;
            gap          = gapSlots;
        } else {
            int[] fenster = tagesFenster(prefs);
            dayStartSlot = fenster[0];
            dayEndSlot   = fenster[1];
            gap          = 0;
        }
        if (dayEndSlot <= dayStartSlot) {   // defensiv gegen Fehlkonfiguration
            dayStartSlot = 0;
            dayEndSlot   = SLOTS_PER_DAY;
        }

        CpModel model = new CpModel();
        List<IntervalVar> intervals = new ArrayList<>();
        for (int i = 0; i < occupied.size(); i++) {
            int[] b = occupied.get(i);
            intervals.add(model.newFixedInterval(b[0], Math.max(1, b[1] - b[0]), "belegt_" + i));
        }

        LinearExprBuilder dropB = LinearExpr.newBuilder();
        long dropConst = 0;
        // Zweitrangiges Ziel: so früh wie möglich. Skaliert wird unten so, dass es das Platzieren
        // niemals überstimmt — ein Block am Freitag ist besser als gar keiner am Dienstag.
        List<IntVar> qVars    = new ArrayList<>();
        List<Long>   qWeights = new ArrayList<>();
        List<Placeable> placeables = new ArrayList<>();
        for (int i = 0; i < offen.size(); i++) {
            TaskChunk c = offen.get(i);
            int sizeSlots    = Axis.slotsFor(c.durationMinutes);
            int deadlineSlot = axis.floorSlot(c.task.getDeadline());

            // Hier fällt der Deckel: bis zur Deadline, nicht bis zum Nahbereich. Und hier gilt die
            // ECHTE Deadline, nicht der Puffer aus taskBounds — passt es mit Puffer nicht, ist ein
            // Block im Puffer allemal besser als eine Warnung.
            int lastDay     = Math.min(axis.totalDays - 1, deadlineSlot / SLOTS_PER_DAY);
            Integer latestEnd = deadlineSlot;

            int earliest = nowSlot;
            if (c.task.getNotBefore() != null) {
                earliest = Math.max(earliest, axis.ceilSlot(c.task.getNotBefore()));
            }

            List<DayWindow> windows = dayWindows(axis, dayStartSlot, dayEndSlot, sizeSlots,
                    earliest, null, lastDay, latestEnd);
            if (windows.isEmpty()) {
                placeables.add(null);
                continue;
            }
            Placeable p = makePlaceable(model, mode + "_" + i, sizeSlots, c.durationMinutes,
                    windows, gap);
            placeables.add(p);
            intervals.add(p.interval);

            // Priorität als Gewicht: konkurrieren hier zwei Aufgaben um denselben letzten Platz,
            // gewinnt die wichtigere. Die Abwägung gegen Gewohnheiten und Trainings hat der
            // Hauptlauf längst getroffen — hier wird nichts mehr verdrängt, nur aufgefüllt.
            long weight = Math.max(1, nz(c.task.getPriority(), 3));
            dropB.addTerm(p.present, -weight);
            dropConst += weight;

            // So früh wie möglich. Im Hauptlauf besorgt das der late-Term; hier gäbe es sonst
            // keinen Grund, den Nachholtermin auf heute Abend statt auf übermorgen zu legen.
            qVars.add(p.start);
            qWeights.add(weight);
        }
        if (placeables.stream().allMatch(Objects::isNull)) return 0;

        model.addNoOverlap(intervals.toArray(new IntervalVar[0]));
        addReliefDayCaps(model, prefs, chunks, offen, placeables);

        // Lexikografisch über EIN Ziel: Platzieren schlägt Frühsein immer. Der Faktor ist die
        // exakte Obergrenze des Frühseins-Terms plus eins, also nicht geschätzt — ein zweiter
        // Solver-Lauf wäre für ein Modell aus drei Variablen die teurere Lösung.
        long qMax = 0;
        for (long w : qWeights) qMax += w * axis.horizonSlots;
        LinearExprBuilder ziel = LinearExpr.newBuilder();
        ziel.addTerm(dropB.add(dropConst).build(), qMax + 1);
        for (int i = 0; i < qVars.size(); i++) ziel.addTerm(qVars.get(i), qWeights.get(i));
        model.minimize(ziel.build());

        CpSolver solver = new CpSolver();
        solver.getParameters().setLogSearchProgress(false);
        solver.getParameters().setRandomSeed(42);
        solver.getParameters().setNumSearchWorkers(solverWorkersPhase1);
        solver.getParameters().setMaxTimeInSeconds(Math.max(0.05, reliefTimeLimitSeconds));
        CpSolverStatus status = solver.solve(model);
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            log.warn("Nachlauf {} ohne Lösung: {}", mode, status);
            return 0;
        }

        int platziert = 0;
        for (int i = 0; i < offen.size(); i++) {
            Placeable p = placeables.get(i);
            if (p == null || !Boolean.TRUE.equals(solver.booleanValue(p.present))) continue;

            TaskChunk c = offen.get(i);
            int start = (int) solver.value(p.start);
            c.placedStartSlot = start;
            c.reliefLevel = mode == ReliefMode.CATCH_UP ? 1 : 2;
            occupied.add(new int[]{ start, start + Axis.slotsFor(c.durationMinutes) + gap });
            platziert++;
        }
        if (platziert > 0) log.info("Nachlauf {}: {} von {} Blöcken gerettet", mode, platziert, offen.size());
        return platziert;
    }

    /**
     * Die Aufgaben-Tagesdeckel für einen Nachlauf, um das schon Verplante gekürzt.
     *
     * {@link #addDailyLoadLimits} lässt sich hier nicht wiederverwenden: es rechnet über die
     * Placeables des Hauptmodells, und die gibt es in diesem Modell nicht mehr. Was ein Tag
     * bereits an Aufgaben trägt, steht aber exakt in den Chunks — mehr braucht die Grenze nicht.
     *
     * Gilt für BEIDE Nachläufe, auch für den quetschenden. Gelockert werden dort die Tageszeiten
     * und die Pausen — also das, was der Kalender vorgibt. {@code maxTaskMinutesPerDay} und
     * {@code maxTasksPerDay} sind dagegen eine Aussage des Nutzers darüber, wie viel er an einem
     * Tag überhaupt schafft; die zu überschreiten würde die Deadline nicht retten, sondern nur
     * die Überlastung in den Kalender schreiben.
     *
     * <b>Überfälliges taucht hier nicht mehr auf</b> — es ist längst vom Vorlauf platziert und
     * erreicht die Nachläufe gar nicht. Seine Minuten zählen über {@code alleChunks} trotzdem
     * gegen den Deckel: ein Tag, an dem etwas nachgeholt wird, ist voll, und dort soll nicht auch
     * noch nachgerückt werden. Der Deckel gilt also weiterhin für alle, die ihn respektieren
     * müssen, und wird von genau dem einen Fall überschritten, für den er nie gedacht war.
     */
    private void addReliefDayCaps(CpModel model, UserPreferences prefs, List<TaskChunk> alleChunks,
                                  List<TaskChunk> offen, List<Placeable> placeables) {
        int capSlots = Axis.slotsFor(nz(prefs.getMaxTaskMinutesPerDay(), FALLBACK_MAX_TASK_MIN_PER_DAY));
        Integer maxTasksPerDay = prefs.getMaxTasksPerDay();

        Map<Integer, Integer> slotsProTag = new HashMap<>();
        Map<Integer, Integer> bloeckeProTag = new HashMap<>();
        for (TaskChunk c : alleChunks) {
            if (c.placedStartSlot == null) continue;
            int tag = c.placedStartSlot / SLOTS_PER_DAY;
            slotsProTag.merge(tag, Axis.slotsFor(c.durationMinutes), Integer::sum);
            bloeckeProTag.merge(tag, 1, Integer::sum);
        }

        Map<Integer, LinearExprBuilder> lastProTag  = new LinkedHashMap<>();
        Map<Integer, LinearExprBuilder> anzahlProTag = new LinkedHashMap<>();
        for (int i = 0; i < offen.size(); i++) {
            Placeable p = placeables.get(i);
            if (p == null) continue;
            int slots = Axis.slotsFor(offen.get(i).durationMinutes);
            for (Map.Entry<Integer, BoolVar> e : p.inDay.entrySet()) {
                lastProTag.computeIfAbsent(e.getKey(), k -> LinearExpr.newBuilder())
                          .addTerm(e.getValue(), slots);
                anzahlProTag.computeIfAbsent(e.getKey(), k -> LinearExpr.newBuilder())
                            .addTerm(e.getValue(), 1);
            }
        }

        for (Map.Entry<Integer, LinearExprBuilder> e : lastProTag.entrySet()) {
            int rest = capSlots - slotsProTag.getOrDefault(e.getKey(), 0);
            model.addLessOrEqual(e.getValue().build(), Math.max(0, rest));
        }
        if (maxTasksPerDay != null && maxTasksPerDay > 0) {
            for (Map.Entry<Integer, LinearExprBuilder> e : anzahlProTag.entrySet()) {
                int rest = maxTasksPerDay - bloeckeProTag.getOrDefault(e.getKey(), 0);
                model.addLessOrEqual(e.getValue().build(), Math.max(0, rest));
            }
        }
    }

    /**
     * Ist der Termin dieses Chunks bereits verstrichen?
     *
     * Nach derselben Uhr wie {@link #taskBounds} und {@link #classifyAtRisk} — dem Umplanzeitpunkt,
     * nicht einem frisch gelesenen {@code now}. Sonst könnten Fensterwahl und Meldung an der
     * Sekundengrenze auseinanderfallen.
     */
    private boolean istUeberfaellig(TaskChunk c, Axis axis, int nowSlot) {
        return c.task.getDeadline() != null && axis.floorSlot(c.task.getDeadline()) <= nowSlot;
    }

    /** Uhrzeit aus der Konfiguration, mit Rückfallwert statt Ausnahme bei Unsinn. */
    private static LocalTime parseZeit(String wert, LocalTime fallback) {
        if (wert == null || wert.isBlank()) return fallback;
        try {
            return LocalTime.parse(wert.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return fallback;
        }
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
        // Gewohnheiten, die aus einer Gym-Routine stammen, planen wir hier NICHT: ihre Zeit
        // belegt der Workout-Platzhalter derselben Routine (siehe generateWorkoutPlaceholders).
        // Beides einzuplanen legte zwei Termine fuer ein Training in die Woche - einen als
        // Habit-Slot, einen als Einheit. Die Gewohnheit dient allein der Nachhaltung im
        // Habit-Space; siehe RoutineHabitService.
        input.setHabits(habitRepository.findHabitsActiveInRange(userId, startDate, endDate)
                .stream()
                .filter(h -> h.getRoutine() == null)
                .collect(Collectors.toList()));
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
        // Erst hier: das Muskelprofil wird aus den geladenen Einheiten abgeleitet.
        input.setRoutineMuscles(loadRoutineMuscles(flexible));

        log.debug("Input: {} Tasks, {} fixe Events, {} Habits, {} fixe Workouts, {} flexible Workouts",
                input.getTasks().size(), input.getFixedEvents().size(), input.getHabits().size(),
                input.getFixedWorkouts().size(), input.getFlexibleWorkouts().size());
        return input;
    }

    /**
     * Die primaer beanspruchten Muskeln je Routine, ueber die der Planer gleich entscheidet.
     *
     * <p>Eine Abfrage fuer alle Routinen des Horizonts statt eines Lazy-Durchgriffs je Einheit.
     * Ohne Routinen faellt sie ganz weg - die haeufige Lage, wenn niemand im Gym-Space plant.
     */
    private Map<Long, Set<MuscleGroup>> loadRoutineMuscles(List<WorkoutSession> workouts) {
        Set<Long> routineIds = nz(workouts).stream()
                .map(WorkoutSession::getRoutine)
                .filter(Objects::nonNull)
                .map(Routine::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (routineIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Set<MuscleGroup>> byRoutine = new LinkedHashMap<>();
        for (Object[] row : routineExerciseRepository.findPrimaryMusclesByRoutineIds(routineIds)) {
            byRoutine.computeIfAbsent((Long) row[0], k -> new LinkedHashSet<>())
                    .add((MuscleGroup) row[1]);
        }
        return byRoutine;
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
    private int reconcileScheduledEvents(Long userId, LocalDateTime cutoff, LocalDateTime bis,
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

        int angelegt = 0;

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
                    if (ev != null) {
                        calendarEventRepository.save(ev);
                        angelegt++;
                    }
                } else if (uebernimmAenderungen(alt.get(i), neu.get(i))) {
                    zuAendern.add(alt.get(i));
                }
            }
        }

        if (!zuAendern.isEmpty())  calendarEventRepository.saveAll(zuAendern);
        if (!zuLoeschen.isEmpty()) calendarEventRepository.deleteAllByIdInBatch(zuLoeschen);

        // Wie viele Blöcke dieser Lauf wirklich angefasst hat. Ist die Summe 0, hat sich für das
        // Frontend nichts geändert und der Monatsabruf kann entfallen — der häufige Fall, seit der
        // Abgleich einen Lauf ohne Änderung gar nichts mehr schreiben lässt.
        //
        // buildEvent kann null liefern (Item ohne auflösbare Entität); nur wirklich Gespeichertes
        // wird gezählt, sonst meldete ein Lauf Änderungen, die niemand nachladen kann.
        return angelegt + zuAendern.size() + zuLoeschen.size();
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
    /**
     * Die Notiz, die der Scheduler an einen Task-Block schreibt — oder {@code null}.
     *
     * Eine Stelle für beide Aufrufer (Neuanlage und Abgleich): stünde die Regel zweimal da, würde
     * ein Block, dessen Lage sich verbessert hat, seine alte Begründung behalten.
     *
     * <p>Das Quetschen schlägt den fehlenden Puffer, weil es die stärkere Aussage ist: ein Block
     * um halb neun abends erklärt sich nicht damit, dass die Reserve knapp war.
     */
    private String taskNotiz(ScheduledItem item) {
        if (item.getType() != ScheduledItemType.TASK) return null;
        // Ein Block aus dem Quetsch-Nachlauf liegt außerhalb der Arbeitszeit oder ohne die übliche
        // Pause davor. Ohne diesen Satz liest sich das wie ein Fehler.
        if (item.getReliefLevel() >= 2) return NOTE_SQUEEZED;
        if (item.isInsideDeadlineBuffer()) return NOTE_NO_BUFFER;
        return null;
    }

    private boolean uebernimmAenderungen(CalendarEvent alt, ScheduledItem neu) {
        String titel = titelFuer(neu);
        // Die Quetsch-Notiz gehört zum Abgleich: rutscht ein Block beim nächsten Lauf wieder in
        // die normale Arbeitszeit, muss die Begründung mit verschwinden — sonst steht sie für
        // immer an einem völlig unauffälligen Termin. Nur bei TASK, denn nur dort schreibt der
        // Scheduler die Notiz überhaupt; an einem Habit-Block gehört sie dem Nutzer.
        boolean istTask = neu.getType() == ScheduledItemType.TASK;
        String notiz = taskNotiz(neu);
        boolean geaendert = !Objects.equals(alt.getStartTime(), neu.getStartTime())
                || !Objects.equals(alt.getEndTime(), neu.getEndTime())
                || !Objects.equals(alt.getTitle(), titel)
                || (istTask && !Objects.equals(alt.getNotes(), notiz));
        if (!geaendert) return false;

        alt.setStartTime(neu.getStartTime());
        alt.setEndTime(neu.getEndTime());
        alt.setTitle(titel);
        if (istTask) alt.setNotes(notiz);
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
                    ev.setNotes(taskNotiz(item));
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
     * Die Obergrenze ist bewusst 3: sie füllt genau die drei Plätze, die {@link #urgencyRank}
     * zwischen zwei Prioritätsstufen frei lässt ({@code prio*4}). Eine breitere Skala würde die
     * Rangordnung zerstören — dann könnte eine nähere Deadline eine höhere Priorität wieder
     * überholen, und genau das war die Beschwerde. Die Deadline-Strafe {@code W_LATE = 40} bleibt
     * davon unberührt und um Größenordnungen stärker als jede Dringlichkeit.
     */
    private static final long[] PLACEMENT_URGENCY = { 1, 1, 2, 2, 3 };

    /**
     * Wie wichtig es ist, diesen Task überhaupt unterzubringen (Phase-1-Gewicht).
     *
     * <b>Priorität ist ein RANG, keine Größe.</b> Jede Prioritätsstufe bekommt ihr eigenes Band,
     * und die Bänder überlappen nicht:
     *
     * <pre>
     *   P1   1500 .. 2200        P4   4500 .. 5200
     *   P2   2500 .. 3200        P5   5500 .. 6200
     *   P3   3500 .. 4200
     * </pre>
     *
     * Die Deadline-Nähe ordnet also nur noch INNERHALB einer Stufe (Bandbreite 700 < Abstand 1000).
     * Vorher war sie ein Faktor auf die Priorität, und damit konnte sie die Priorität überholen:
     * ein P2-Task, der heute fällig ist, kam auf {@code 400 + 2*100*8 = 2000} und schlug einen
     * P5-Task ohne Deadline ({@code 400 + 5*100*1 = 900}). Wer eine wichtige Aufgabe ohne Termin
     * eintrug, sah sie gegen eine Nebensächlichkeit mit Termin verlieren — genau die Beschwerde
     * "Prioritäten werden ignoriert".
     *
     * Die dokumentierte Invariante an {@link #W_DROP_PROJECT} — jede Aufgabe schlägt jedes
     * wiederkehrende Item — gilt weiter und jetzt mit deutlichem Abstand:
     * {@code min(Aufgabe) = 1500} gegen {@code max(Wiederkehrendes) = 300}.
     */
    private long calculateTaskWeight(Task task, LocalDate today) {
        return W_DROP_TASK_BASE
                + prioClamped(task) * W_DROP_PRIO_STEP
                + DROP_URGENCY[deadlineBucket(task, today)] * 100L;
    }

    /**
     * Rang für die Reihenfolge über TAGE — "welche Aufgabe kommt zuerst dran".
     *
     * Dieselbe Dominanzregel wie beim Verdrängungsgewicht: die Priorität spannt {@code prio*4}
     * (4..20), die Deadline-Stufe füllt die drei Plätze dazwischen. Eine nähere Deadline kann eine
     * höhere Priorität damit nie überholen.
     */
    private int urgencyRank(Task task, LocalDate today) {
        return prioClamped(task) * 4 + (int) (PLACEMENT_URGENCY[deadlineBucket(task, today)] - 1);
    }

    /**
     * Rang für die Reihenfolge INNERHALB eines Tages.
     *
     * Bewusst deutlich schmaler als {@link #urgencyRank} (2..11 statt 4..22): dieser Term wirkt
     * pro Slot und muss über einen ganzen Tag unter der Totzone des Stabilitätsankers bleiben —
     * {@code 11 * 96 = 1056 < W_MOVE_FIXED = 1200}. Ordnung ja, aber niemals um den Preis, dass
     * ein liegender Block noch einmal verschoben wird.
     *
     * Vorher stand hier ausschließlich die Deadline-Stufe: lagen zwei Aufgaben am selben Tag,
     * spielte die Priorität überhaupt keine Rolle.
     *
     * <p><b>Die Deadline steht hier bewusst nicht mehr drin.</b> Sie hatte in diesem Rang nur Platz
     * für ein einziges Bit — der Rang muss unter {@code W_MOVE_FIXED / SLOTS_PER_DAY = 12} bleiben,
     * und die Priorität soll strikt dominieren. Mit einem Bit fielen "morgen fällig", "in zwei
     * Tagen" und "in drei Tagen" alle zusammen, und drei solche Aufgaben landeten gemessen in
     * umgekehrter Reihenfolge im Kalender.
     *
     * <p>Die Reihenfolge macht seit dem Umbau {@link #addTaskOrderPreference} — relativ, mit einem
     * Pauschalpreis pro vertauschtem Paar statt mit einem Gewicht auf der Uhrzeit. Das ist nicht
     * nur genauer (volle Termin-Reihenfolge statt zweier Stufen), es macht diesen Term auch frei:
     * er darf jetzt schwach sein.
     *
     * <p><b>Und das musste er werden.</b> Solange hier {@code prio*2} stand, war er immer mindestens
     * so groß wie das Leistungshoch ({@code W_PEAK * prio = prio*2}) — die Wunschzeit-Einstellung
     * konnte also bestenfalls unentschieden spielen und nie gewinnen. Mit {@code prio} kostet ein
     * Slot später zu liegen halb so viel, wie das Leistungshoch dafür gutschreibt: die Einstellung
     * wirkt endlich, und "früher ist besser" bleibt als schwacher Grundzug erhalten.
     */
    private int dayOrderRank(Task task, LocalDate today) {
        return prioClamped(task);
    }

    /**
     * Die Priorität auf 1..5 begrenzt.
     *
     * Die Grenzen sind keine Kosmetik: sämtliche Rang-Herleitungen (Bandbreite beim Verdrängen,
     * Obergrenze gegen die Totzone) rechnen mit prio ≤ 5. Ein Ausreißer aus alten Daten würde sie
     * still aushebeln. Die API validiert bereits {@code @Min(1) @Max(5)} — hier ist der Löser
     * gegen alles abgesichert, was daran vorbeigekommen ist.
     */
    private int prioClamped(Task task) {
        return Math.min(5, Math.max(1, nz(task.getPriority(), 3)));
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
     * Privatzeiten — der Rahmen für Gewohnheiten und Trainings.
     *
     * Die Rückfallwerte sind bewusst weit (06:00–23:00): sie sollen die Wunschfenster aus
     * {@link HabitWindow} vollständig umschließen, sonst bleibt genau der Fehler bestehen, für den
     * es die Privatzeit gibt.
     */
    private LocalTime personalStart(UserPreferences p) {
        return p.getPersonalHoursStart() != null ? p.getPersonalHoursStart() : LocalTime.of(6, 0);
    }

    private LocalTime personalEnd(UserPreferences p) {
        return p.getPersonalHoursEnd() != null ? p.getPersonalHoursEnd() : LocalTime.of(23, 0);
    }

    /**
     * Der Tagesrahmen für ein wiederkehrendes, privates Item (Gewohnheit, Training), in Slots.
     *
     * Geliefert wird die HÜLLE aus Arbeits- und Privatzeit, nicht deren exakte Vereinigung. Zwei
     * Gründe:
     * <ol>
     *   <li><b>Struktur.</b> {@link Placeable#inDay} ist eine Map von Tag auf Boolean. Zwei
     *       getrennte Fenster am selben Tag würden sich beim {@code put} gegenseitig
     *       überschreiben und damit Tagesdeckel, "höchstens eines pro Tag", Ruhetagregel und
     *       {@link #gatedDayIndex} still beschädigen — ein Fehler, den niemand sieht, bis der
     *       Kalender falsch ist. Die Hülle kommt ohne Strukturänderung aus.</li>
     *   <li><b>Sicherheit.</b> Die Hülle kann das Erlaubte nur erweitern, nie einschränken. Damit
     *       kann keine heute planbare Gewohnheit durch diese Änderung unplanbar werden.</li>
     * </ol>
     * Eine etwaige Lücke zwischen den beiden Fenstern bleibt damit erlaubt. Das ist verschmerzbar:
     * WELCHE Uhrzeit es innerhalb des Rahmens wird, entscheidet ohnehin das Wunschfenster der
     * Gewohnheit über {@link #windowDeviation}, nicht die harte Schranke.
     */
    private int[] privateDayBounds(UserPreferences prefs) {
        int lo = Math.min(minuteOfDay(workStart(prefs)), minuteOfDay(personalStart(prefs))) / GRID;
        int hi = Math.max(minuteOfDay(workEnd(prefs)),   minuteOfDay(personalEnd(prefs)))   / GRID;
        if (hi <= lo) hi = SLOTS_PER_DAY;   // defensiv gegen Fehlkonfiguration, wie bei der Arbeitszeit
        return new int[]{ lo, hi };
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
