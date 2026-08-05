package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final UserService                userService;
    private final CalendarEventService       calendarEventService;
    private final TaskService                taskService;
    private final WorkoutPlanService         workoutPlanService;

    /** Minuten pro Slot. 5 teilt 15/30/45/60/90/120, alle üblichen Dauern bleiben exakt. */
    private static final int GRID          = 5;
    private static final int SLOTS_PER_DAY = 1440 / GRID;

    private static final int DEFAULT_HABIT_DURATION_MIN    = 30;
    private static final int DEFAULT_WORKOUT_DURATION_MIN  = 45;
    private static final int MAX_CHUNKS_PER_TASK           = 12;
    private static final int FALLBACK_MIN_CHUNK_MIN        = 30;
    private static final int FALLBACK_MAX_CHUNK_MIN        = 120;
    private static final int FALLBACK_MAX_TASK_MIN_PER_DAY = 480;

    // Phase-2-Gewichte. Alle Terme sind in Slots, damit sie vergleichbar sind.
    // Eine Deadline um einen Tag zu reißen kostet 40*prio*288; einen Tag später zu liegen 1*prio*288.
    // Der Solver sortiert also ~40 Items um, um eine Deadline zu retten.
    private static final long W_LATE      = 40;
    private static final long W_URGENCY   = 1;
    private static final long W_HABIT_DEV = 2;
    private static final long W_MOVE      = 3;
    private static final long W_PEAK      = 2;

    // Feld-Initialisierung, damit Mockito-Tests ohne Spring-Kontext einen sinnvollen Wert haben.
    @Value("${scheduler.solver-time-limit-seconds:10.0}")
    private double solverTimeLimitSeconds = 10.0;

    /**
     * Wie weit im Voraus TASKS Blöcke bekommen — gemessen in Tagen ab startDate, unabhängig vom
     * Gesamthorizont. Habits und Workouts laufen bewusst über den vollen Horizont: sie sind
     * wiederkehrend und sollen in JEDER Woche im Kalender stehen. Ein Task-Block drei Monate im
     * Voraus wäre dagegen wertlos (bis dahin hat sich die Aufgabenlage längst geändert) und
     * teuer: jeder Task-Chunk bekommt eine Tages-Boolean pro Horizont-Tag, ein Habit-Slot nur
     * für die sieben Tage seiner eigenen Woche. Ohne den Zuschnitt wächst allein der Task-Teil
     * des Modells linear mit dem Horizont.
     */
    @Value("${scheduler.task-horizon-days:14}")
    private int taskHorizonDays = 14;

    /** Gesamthorizont, wenn der Aufrufer kein Enddatum mitgibt. */
    @Value("${scheduler.horizon-days:84}")
    private int horizonDays = 84;

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
        Object lock = schedulingLocks.computeIfAbsent(userId, k -> new Object());
        synchronized (lock) {
            return doGenerateOptimalSchedule(userId, startDate, endDate);
        }
    }

    /** Standard-Enddatum des Horizonts, damit Aufrufer den Wert nicht doppelt konfigurieren müssen. */
    public LocalDate defaultHorizonEnd(LocalDate startDate) {
        return startDate.plusDays(Math.max(1, horizonDays));
    }

    private ScheduleResult doGenerateOptimalSchedule(Long userId, LocalDate startDate, LocalDate endDate) {
        log.info("Generiere CP-SAT Schedule für User {} | {} – {}", userId, startDate, endDate);

        UserPreferences prefs = userService.getOrCreatePreferences(userId);
        generateWorkoutPlaceholders(userId, startDate, endDate);

        LocalDateTime cutoff = replanCutoff(startDate);
        ScheduleInput input = collectScheduleInput(userId, startDate, endDate, cutoff);

        int totalDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        Axis axis = new Axis(startDate, totalDays);

        // Letzter Tag, an dem noch Task-Blöcke liegen dürfen (siehe taskHorizonDays).
        int taskLastDay = Math.min(totalDays - 1, Math.max(0, taskHorizonDays));

        // Gepinnte und eingefrorene Blöcke sind für die Zerlegung dasselbe: beide belegen Zeit,
        // die weder neu geplant noch doppelt verplant werden darf.
        List<CalendarEvent> committed = concat(input.getFixedEvents(), input.getFrozenEvents());

        // Verpasste Blöcke überfälliger Tasks sind davon ausgenommen: sie sperren ihre Zeit in der
        // Vergangenheit zwar weiter, dürfen dem Task aber weder Minuten gutschreiben noch ihm einen
        // Termin zurückschreiben (siehe isMissedBlock).
        List<CalendarEvent> credited = committed.stream()
                .filter(e -> !isMissedBlock(e, cutoff))
                .collect(Collectors.toList());

        Map<Long, Integer> pinnedMinutes = pinnedMinutesPerTask(credited);
        List<TaskChunk> chunks     = decomposeTasks(input.getTasks(), prefs, pinnedMinutes);
        List<HabitSlot> habitSlots = expandHabitSlots(input.getHabits(), prefs, startDate, endDate,
                pinnedDatesPerHabit(committed));

        SolveOutcome outcome = solveWithCpSat(chunks, habitSlots, input, axis, prefs, startDate, endDate,
                taskLastDay);

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

        // Erst ab dem Umplanzeitpunkt löschen: was bereits begonnen hat, ist Geschichte und
        // bleibt im Kalender stehen.
        calendarEventService.clearScheduledEvents(userId, cutoff, endDate.atTime(23, 59, 59));
        saveScheduleToDatabase(userId, outcome.getItems());
        log.info("Gespeichert: {} geplante Blöcke", outcome.getItems().size());
        writeBackTaskSpans(outcome, input.getTasks(), credited);

        // Die Vorlesungen laufen getrennt und landen NICHT in outcome.getItems(): ScheduleResult
        // zählt alles, was kein TASK ist, als "Habits/Workouts" und summiert es in
        // totalHoursScheduled. Eingemischt bliese das die Kennzahl mit Stunden auf, die der
        // Solver nie geplant hat.
        syncClassEvents(userId, startDate, endDate);

        List<ScheduledItem> scheduledTasks = outcome.getItems().stream()
                .filter(i -> i.getType() == ScheduledItemType.TASK)
                .collect(Collectors.toList());
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

        log.info("Schedule fertig: {} Task-Blöcke, {} Habits/Workouts, {} at risk",
                scheduledTasks.size(), scheduledRest.size(), outcome.getAtRisk().size());
        return result;
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
    private IntVar gated(CpModel model, Placeable p, String name, LinearArgument expr, int max) {
        IntVar v = model.newIntVar(0, max, name);
        model.addEquality(v, expr).onlyEnforceIf(p.present);
        model.addEquality(v, 0).onlyEnforceIf(p.present.not());
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
    private Map<Long, Set<LocalDate>> pinnedDatesPerHabit(List<CalendarEvent> fixedEvents) {
        return nz(fixedEvents).stream()
                .filter(e -> e.getRelatedHabit() != null && e.getStartTime() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getRelatedHabit().getId(),
                        Collectors.mapping(e -> e.getStartTime().toLocalDate(), Collectors.toSet())));
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
                                             Map<Long, Set<LocalDate>> pinnedDates) {
        List<HabitSlot> slots = new ArrayList<>();

        for (Habit habit : nz(habits)) {
            LocalDate rangeStart = (habit.getStartDate() != null && habit.getStartDate().isAfter(startDate))
                    ? habit.getStartDate() : startDate;
            LocalDate rangeEnd = (habit.getEndDate() != null && habit.getEndDate().isBefore(endDate))
                    ? habit.getEndDate() : endDate;
            if (rangeStart.isAfter(rangeEnd)) continue;

            Set<LocalDate> completed = habitCompletionRepository
                    .findByHabitIdAndCompletionDateBetween(habit.getId(), rangeStart, rangeEnd)
                    .stream()
                    .map(HabitCompletion::getCompletionDate)
                    .collect(Collectors.toCollection(HashSet::new));

            // Ein Tag, an dem bereits ein gepinnter Termin dieser Habit liegt, zählt wie ein
            // erledigter: der Tag ist belegt und die Wochenquote ist um eins reduziert. Ohne
            // das legt der Solver nach einem Drag-and-Drop eine ZWEITE Ausführung am selben
            // Tag an — der verschobene Block bliebe stehen, aber doppelt.
            completed.addAll(pinnedDates.getOrDefault(habit.getId(), Set.of()));

            int duration = nz(habit.getDurationMinutes(), DEFAULT_HABIT_DURATION_MIN);
            int[] window = windowMinutes(habit, prefs);

            if (isFlexible(habit)) {
                slots.addAll(flexibleSlots(habit, duration, window, completed, startDate, rangeStart, rangeEnd));
            } else {
                for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
                    if (!isHabitScheduledOn(habit, d)) continue;
                    if (completed.contains(d)) continue;

                    HabitSlot s = new HabitSlot();
                    s.habit           = habit;
                    s.durationMinutes = duration;
                    s.windowStartMin  = window[0];
                    s.windowEndMin    = window[1];
                    s.legacyDate      = d;
                    s.allowedDays.add((int) ChronoUnit.DAYS.between(startDate, d));
                    slots.add(s);
                }
            }
        }
        return slots;
    }

    private boolean isFlexible(Habit h) {
        return h.getTimesPerWeek() != null && h.getTimesPerWeek() > 0;
    }

    private List<HabitSlot> flexibleSlots(Habit habit, int duration, int[] window,
                                          Set<LocalDate> completed, LocalDate horizonStart,
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
                if (completed.contains(d)) continue;
                days.add((int) ChronoUnit.DAYS.between(horizonStart, d));
            }

            if (!days.isEmpty()) {
                int target = habit.getTimesPerWeek();
                // Angebrochene Woche am Rand des Horizonts anteilig planen.
                if (daysInRange < 7) target = (int) Math.ceil(target * daysInRange / 7.0);
                // Bereits erledigte Ausführungen dieser Woche abziehen.
                long done = completed.stream()
                        .filter(d -> !d.isBefore(weekCursor) && !d.isAfter(weekEnd))
                        .count();
                int k = Math.min(days.size(), (int) Math.max(0, target - done));

                String group = "h" + habit.getId() + "w" + weekCursor;
                for (int i = 0; i < k; i++) {
                    HabitSlot s = new HabitSlot();
                    s.habit           = habit;
                    s.durationMinutes = duration;
                    s.windowStartMin  = window[0];
                    s.windowEndMin    = window[1];
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
    // CP-SAT
    // =========================================================================

    private SolveOutcome solveWithCpSat(List<TaskChunk> chunks, List<HabitSlot> habitSlots,
                                        ScheduleInput input, Axis axis, UserPreferences prefs,
                                        LocalDate startDate, LocalDate endDate, int taskLastDay) {

        List<WorkoutSession> flexibleWorkouts = nz(input.getFlexibleWorkouts());
        if (chunks.isEmpty() && habitSlots.isEmpty() && flexibleWorkouts.isEmpty()) {
            return SolveOutcome.empty();
        }

        CpModel model = new CpModel();

        int workStartSlot = minuteOfDay(workStart(prefs)) / GRID;
        int workEndSlot   = minuteOfDay(workEnd(prefs)) / GRID;
        if (workEndSlot <= workStartSlot) workEndSlot = SLOTS_PER_DAY;   // defensiv gegen Fehlkonfiguration

        int bufferSlots = Math.max(0, nz(prefs.getBufferMinutes(), 0) / GRID);
        int nowSlot     = Math.max(0, axis.ceilSlot(LocalDateTime.now()));
        int[] peakWindow = peakWindow(prefs);

        // Mindestpause zwischen zwei automatisch geplanten Blöcken. Ohne sie stapelt der Solver
        // acht Stunden Arbeit lückenlos aufeinander — technisch optimal, menschlich unbrauchbar.
        int gapSlots = Math.max(0, nz(prefs.getBreakDurationMinutes(), 0) / GRID);

        // Platzhalter-Zuordnung für flexible Workouts, bewusst lokal: ein Feld würde
        // zwischen zwei Läufen (auch verschiedener User) Zustand verschleppen.
        Map<Long, Placeable> workoutPlaceables = new HashMap<>();

        List<int[]> blocked = collectBlockedSlots(axis, input, startDate, endDate, bufferSlots, gapSlots);

        List<IntervalVar> allIntervals = new ArrayList<>();
        for (int i = 0; i < blocked.size(); i++) {
            int[] b = blocked.get(i);
            allIntervals.add(model.newFixedInterval(b[0], b[1] - b[0], "blocked_" + i));
        }

        Map<String, Integer> previousStarts = previousStartSlots(input, axis);

        // Phase-1-Ziel: gewichtete Summe der VERWORFENEN Items. Der konstante Anteil bleibt im
        // Ausdruck, weil er für die spätere Schranke addLessOrEqual(dropCost, bestDrop) zählt.
        LinearExprBuilder dropB = LinearExpr.newBuilder();
        long dropConst = 0;

        // Phase-2-Ziel: Platzierungsqualität.
        List<IntVar> qVars = new ArrayList<>();
        List<Long>   qWeights = new ArrayList<>();

        List<Placeable> allPlaceables = new ArrayList<>();

        // --- Task-Chunks ---
        Map<Long, List<TaskChunk>> chunksByTask = chunks.stream()
                .collect(Collectors.groupingBy(c -> c.task.getId(), LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<Long, List<TaskChunk>> e : chunksByTask.entrySet()) {
            List<TaskChunk> group = e.getValue();
            Task task = group.get(0).task;
            long weight = calculateTaskWeight(task);

            int earliest = nowSlot;
            if (task.getNotBefore() != null) earliest = Math.max(earliest, axis.ceilSlot(task.getNotBefore()));

            Integer deadlineSlot = task.getDeadline() != null ? axis.floorSlot(task.getDeadline()) : null;

            for (int ci = 0; ci < group.size(); ci++) {
                TaskChunk c = group.get(ci);
                int sizeSlots = Axis.slotsFor(c.durationMinutes);
                String name = "task" + task.getId() + "_c" + ci;

                List<DayWindow> windows = dayWindows(axis, workStartSlot, workEndSlot, sizeSlots, earliest,
                        null, taskLastDay);
                Placeable p = makePlaceable(model, name, sizeSlots, c.durationMinutes, windows, gapSlots);
                c.placeable = p;
                allPlaceables.add(p);
                allIntervals.add(p.interval);

                dropB.addTerm(p.present, -weight);
                dropConst += weight;

                int prio = Math.max(1, nz(task.getPriority(), 3));

                // Dringlichkeit: früher ist besser.
                qVars.add(gated(model, p, "urg_" + name, p.start, axis.horizonSlots));
                qWeights.add(W_URGENCY * prio);

                // Leistungshoch: die Einstellung war bislang reine Dekoration. Das Gewicht liegt
                // über der Dringlichkeit, damit ein Block ins Hoch wandert, solange dort Platz
                // ist — aber weit unter der Deadline-Strafe, damit nichts dafür zu spät wird.
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
            IntVar dev = windowDeviation(model, p, sizeSlots, s.windowStartMin, s.windowEndMin,
                    axis, "dev_" + name);
            qVars.add(dev);
            qWeights.add(W_HABIT_DEV * Math.max(1, nz(s.habit.getPriority(), 3)));

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

            for (int day : days) {
                List<Literal> sameDay = new ArrayList<>();
                for (HabitSlot s : group) {
                    BoolVar b = s.placeable.inDay.get(day);
                    if (b != null) sameDay.add(b);
                }
                if (sameDay.size() > 1) model.addAtMostOne(sameDay);
            }

            for (int i = 1; i < group.size(); i++) {
                Placeable a = group.get(i - 1).placeable;
                Placeable b = group.get(i).placeable;
                model.addLessThan(a.start, b.start).onlyEnforceIf(new Literal[]{ a.present, b.present });
                model.addImplication(b.present, a.present);   // Slots der Reihe nach füllen
            }
        }

        // --- Flexible Workouts ---
        for (WorkoutSession w : flexibleWorkouts) {
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
                days.add((int) ChronoUnit.DAYS.between(startDate, d));
            }

            List<DayWindow> windows = dayWindows(axis, workStartSlot, workEndSlot, sizeSlots, nowSlot,
                    days, axis.totalDays - 1);
            Placeable p = makePlaceable(model, name, sizeSlots, duration, windows, gapSlots);
            allPlaceables.add(p);
            allIntervals.add(p.interval);
            workoutPlaceables.put(w.getId(), p);

            dropB.addTerm(p.present, -300L);
            dropConst += 300L;

            qVars.add(gated(model, p, "urg_" + name, p.start, axis.horizonSlots));
            qWeights.add(W_URGENCY * 3);

            addStabilityTerm(model, p, previousStarts.get("workout:" + w.getId()), axis, qVars, qWeights, name);
        }

        // --- Kernconstraint: nichts überlappt ---
        model.addNoOverlap(allIntervals.toArray(new IntervalVar[0]));

        // --- Tageslimit für Task-Zeit ---
        // addCumulative wäre hier falsch: es begrenzt die MOMENTANE Auslastung, nicht das Integral
        // über einen Tag. Die inDay-Booleans drücken genau das aus, was gemeint ist.
        int capSlots = Axis.slotsFor(nz(prefs.getMaxTaskMinutesPerDay(), FALLBACK_MAX_TASK_MIN_PER_DAY));
        for (int d = 0; d <= taskLastDay; d++) {
            LinearExprBuilder load = LinearExpr.newBuilder();
            boolean any = false;
            for (TaskChunk c : chunks) {
                BoolVar b = c.placeable != null ? c.placeable.inDay.get(d) : null;
                if (b != null) { load.addTerm(b, Axis.slotsFor(c.durationMinutes)); any = true; }
            }
            if (any) model.addLessOrEqual(load.build(), capSlots);
        }

        LinearExpr dropCost = dropB.add(dropConst).build();

        CpSolver solver = new CpSolver();
        solver.getParameters().setNumSearchWorkers(4);
        solver.getParameters().setLogSearchProgress(false);

        // ---- Phase 1: möglichst viel (gewichtet) überhaupt unterbringen ----
        model.minimize(dropCost);
        solver.getParameters().setMaxTimeInSeconds(Math.max(0.05, solverTimeLimitSeconds * 0.35));
        CpSolverStatus s1 = solver.solve(model);
        if (s1 != CpSolverStatus.OPTIMAL && s1 != CpSolverStatus.FEASIBLE) {
            log.warn("CP-SAT Phase 1 ohne Lösung: {}", s1);
            return SolveOutcome.unusable(s1);
        }
        long bestDrop = Math.round(solver.objectiveValue());

        // ---- Phase 2: Qualität, ohne Phase 1 zu verschlechtern ----
        model.clearHints();
        for (Placeable p : allPlaceables) model.addHint(p.start, solver.value(p.start));
        model.addLessOrEqual(dropCost, bestDrop);
        model.minimize(LinearExpr.weightedSum(
                qVars.toArray(new LinearArgument[0]),
                qWeights.stream().mapToLong(Long::longValue).toArray()));
        solver.getParameters().setMaxTimeInSeconds(Math.max(0.05, solverTimeLimitSeconds * 0.65));
        CpSolverStatus s2 = solver.solve(model);

        CpSolverStatus effective = (s2 == CpSolverStatus.OPTIMAL || s2 == CpSolverStatus.FEASIBLE) ? s2 : s1;
        if (s2 != CpSolverStatus.OPTIMAL && s2 != CpSolverStatus.FEASIBLE) {
            // Phase 2 hat das Zeitbudget gerissen; Phase 1 hatte aber eine gültige Lösung.
            // Die ist zwar schlechter platziert, aber vollständig gültig — also erneut lösen,
            // damit der Solver-Zustand wieder zur Phase-1-Lösung passt.
            log.warn("CP-SAT Phase 2 ohne Lösung ({}), nutze Phase-1-Platzierung.", s2);
            model.minimize(dropCost);
            solver.getParameters().setMaxTimeInSeconds(Math.max(0.05, solverTimeLimitSeconds * 0.35));
            CpSolverStatus retry = solver.solve(model);
            if (retry != CpSolverStatus.OPTIMAL && retry != CpSolverStatus.FEASIBLE) {
                return SolveOutcome.unusable(retry);
            }
        }

        log.info("CP-SAT {} | Intervalle: {} | Chunks: {} Habits: {} Workouts: {} | drop={}",
                effective, allIntervals.size(), chunks.size(), habitSlots.size(),
                flexibleWorkouts.size(), bestDrop);

        return extract(solver, effective, chunks, habitSlots, flexibleWorkouts, workoutPlaceables, axis);
    }

    /**
     * Pro Tag ein erlaubtes Startfenster, bereits um "jetzt" und die Arbeitszeit beschnitten.
     * {@code lastDay} begrenzt zusätzlich, wie weit in den Horizont hinein das Item überhaupt
     * darf — für Tasks der Task-Horizont, für Habits und Workouts der volle Horizont.
     */
    private List<DayWindow> dayWindows(Axis axis, int workStartSlot, int workEndSlot, int sizeSlots,
                                       int earliestSlot, List<Integer> restrictToDays, int lastDay) {
        List<DayWindow> out = new ArrayList<>();
        int upper = Math.min(lastDay, axis.totalDays - 1);
        for (int d = 0; d <= upper; d++) {
            if (restrictToDays != null && !restrictToDays.contains(d)) continue;
            int base = d * SLOTS_PER_DAY;
            int lo = Math.max(base + workStartSlot, earliestSlot);
            int hi = base + workEndSlot - sizeSlots;
            if (lo <= hi) out.add(new DayWindow(d, lo, hi));
        }
        return out;
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

    private void addStabilityTerm(CpModel model, Placeable p, Integer previousSlot, Axis axis,
                                  List<IntVar> qVars, List<Long> qWeights, String name) {
        if (previousSlot == null) return;
        // Ohne diesen Term springt bei jeder Änderung der komplette restliche Kalender —
        // das liest sich für den Nutzer wie ein Bug. abs() verträgt kein Enforcement-Literal,
        // deshalb wird die Differenz vorher gegated.
        IntVar diff = model.newIntVar(-axis.horizonSlots, axis.horizonSlots, "diff_" + name);
        model.addEquality(diff, LinearExpr.newBuilder().addTerm(p.start, 1).add(-previousSlot).build())
             .onlyEnforceIf(p.present);
        model.addEquality(diff, 0).onlyEnforceIf(p.present.not());

        IntVar move = model.newIntVar(0, axis.horizonSlots, "move_" + name);
        model.addAbsEquality(move, diff);
        qVars.add(move);
        qWeights.add(W_MOVE);
    }

    /** Bisherige Platzierungen, damit der Stabilitätsterm etwas hat, woran er sich festhalten kann. */
    private Map<String, Integer> previousStartSlots(ScheduleInput input, Axis axis) {
        Map<String, Integer> out = new HashMap<>();
        Map<Long, List<CalendarEvent>> byTask  = new HashMap<>();
        Map<String, List<CalendarEvent>> byHabitWeek = new HashMap<>();

        for (CalendarEvent ev : nz(input.getPreviousScheduledEvents())) {
            if (ev.getStartTime() == null) continue;
            if (ev.getRelatedTask() != null) {
                byTask.computeIfAbsent(ev.getRelatedTask().getId(), k -> new ArrayList<>()).add(ev);
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
        return out;
    }

    // =========================================================================
    // LÖSUNG AUSLESEN
    // =========================================================================

    private SolveOutcome extract(CpSolver solver, CpSolverStatus status, List<TaskChunk> chunks,
                                 List<HabitSlot> habitSlots, List<WorkoutSession> flexibleWorkouts,
                                 Map<Long, Placeable> workoutPlaceables, Axis axis) {
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
            if (missingMinutes > 0) {
                AtRiskReason reason = task.getDeadline() != null && task.getDeadline().isBefore(LocalDateTime.now())
                        ? AtRiskReason.PAST_DEADLINE : AtRiskReason.NO_ROOM;
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

                if (task.getDeadline() != null && end.isAfter(task.getDeadline())) {
                    atRisk.add(AtRiskItem.forTask(task.getId(), task.getTitle(),
                            placed.get(i)[1], AtRiskReason.WOULD_MISS_DEADLINE));
                }
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
        input.setFixedEvents(calendarEventService.getFixedEvents(userId, start, end));
        input.setHabits(habitRepository.findHabitsActiveInRange(userId, startDate, endDate));
        input.setCourseSchedules(courseScheduleRepository.findByUserId(userId));

        // Vor dem Löschen einsammeln: der Stabilitätsterm braucht die bisherigen Platzierungen.
        List<CalendarEvent> previous = nz(calendarEventRepository
                .findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                        userId, List.of(EventType.TASK, EventType.HABIT, EventType.WORKOUT),
                        false, start, end));

        // Bereits begonnene Blöcke werden eingefroren statt neu geplant. Sie taugen deshalb auch
        // nicht als Stabilitätsanker — ein Anker in der Vergangenheit würde den zugehörigen neuen
        // Block gegen die Jetzt-Grenze ziehen.
        // Erledigte Blöcke gelten unabhängig vom Umplanzeitpunkt als eingefroren: sie sperren
        // ihre Zeit weiter, taugen aber nicht als Stabilitätsanker für einen neuen Block.
        Map<Boolean, List<CalendarEvent>> split = previous.stream()
                .filter(e -> e.getStartTime() != null)
                .collect(Collectors.partitioningBy(
                        e -> e.getCompletedAt() != null || e.getStartTime().isBefore(cutoff)));
        input.setFrozenEvents(split.get(true));
        input.setPreviousScheduledEvents(split.get(false));

        // Ein manuell verschobenes Workout hat ein gepinntes Kalender-Event. Es bleibt in der
        // Datenbank zwar "flexibel", darf aber nicht mehr umgeplant werden — sonst zieht der
        // Solver es sofort wieder weg und der Drag-and-Drop hätte keine Wirkung. Für ein bereits
        // gelaufenes Workout gilt dasselbe.
        Set<Long> pinnedWorkoutIds = concat(input.getFixedEvents(), input.getFrozenEvents()).stream()
                .filter(e -> e.getRelatedWorkout() != null)
                .map(e -> e.getRelatedWorkout().getId())
                .collect(Collectors.toSet());

        List<WorkoutSession> inRange = workoutSessionRepository.findByUserIdAndStartTimeBetween(userId, start, end);
        List<WorkoutSession> flexible = workoutSessionRepository.findByUserIdAndIsFlexibleTrue(userId).stream()
                .filter(w -> !pinnedWorkoutIds.contains(w.getId()))
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

        for (ScheduledItem item : scheduled) {
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
                }
                case WORKOUT -> {
                    ev.setTitle(item.getWorkoutSession().getName());
                    ev.setDescription(item.getWorkoutSession().getDescription());
                    ev.setEventType(EventType.WORKOUT);
                    ev.setRelatedWorkout(item.getWorkoutSession());
                    ev.setColor("#FF5722");
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
                default -> { continue; }
            }

            calendarEventRepository.save(ev);
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

    /** Wie wichtig es ist, diesen Task überhaupt unterzubringen (Phase-1-Gewicht). */
    private long calculateTaskWeight(Task task) {
        long w = 0;
        int priority = nz(task.getPriority(), 3);
        w += (long) priority * 100;

        if (task.getDeadline() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), task.getDeadline().toLocalDate());
            if      (days <= 0) w += 1000;
            else if (days == 1) w += 500;
            else if (days <= 3) w += 300;
            else if (days <= 7) w += 150;
            else                w += 50;
        }
        return w;
    }

    private long calculateHabitWeight(Habit habit) {
        return (long) nz(habit.getPriority(), 3) * 100;
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

    private static int minuteOfDay(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
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
            case PROJECTS -> "#00BCD4";
            case TASKS    -> "#FF5722";
            case RECIPES  -> "#4CAF50";
            default       -> "#2196F3";
        };
    }
}
