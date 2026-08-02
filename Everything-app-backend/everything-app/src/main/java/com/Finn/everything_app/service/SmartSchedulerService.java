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
 *  - Arbeitszeit : NICHT als Schlaf-Blöcke modelliert, sondern als Ober-/Untergrenze pro Tag an
 *                  jedem Item (siehe DayWindow). Das ist billiger und verhindert zusätzlich, dass
 *                  ein Item über Mitternacht läuft.
 *  - Items       : Tasks (in Chunks zerlegt), Habit-Slots und flexible Workouts sind jeweils
 *                  OPTIONALE Intervalle mit Präsenz-Literal. Was nicht passt, wird verworfen und
 *                  als {@link AtRiskItem} gemeldet — der Kalender wird nie leer.
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

    // Feld-Initialisierung, damit Mockito-Tests ohne Spring-Kontext einen sinnvollen Wert haben.
    @Value("${scheduler.solver-time-limit-seconds:10.0}")
    private double solverTimeLimitSeconds = 10.0;

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

    private ScheduleResult doGenerateOptimalSchedule(Long userId, LocalDate startDate, LocalDate endDate) {
        log.info("Generiere CP-SAT Schedule für User {} | {} – {}", userId, startDate, endDate);

        UserPreferences prefs = userService.getOrCreatePreferences(userId);
        generateWorkoutPlaceholders(userId, startDate, endDate);

        ScheduleInput input = collectScheduleInput(userId, startDate, endDate);

        int totalDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        Axis axis = new Axis(startDate, totalDays);

        Map<Long, Integer> pinnedMinutes = pinnedMinutesPerTask(input.getFixedEvents());
        List<TaskChunk> chunks     = decomposeTasks(input.getTasks(), prefs, pinnedMinutes);
        List<HabitSlot> habitSlots = expandHabitSlots(input.getHabits(), prefs, startDate, endDate);

        SolveOutcome outcome = solveWithCpSat(chunks, habitSlots, input, axis, prefs, startDate, endDate);

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

        calendarEventService.clearScheduledEvents(userId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        saveScheduleToDatabase(userId, outcome.getItems());
        writeBackTaskSpans(outcome, input.getTasks());

        List<ScheduledItem> scheduledTasks = outcome.getItems().stream()
                .filter(i -> i.getType() == ScheduledItemType.TASK)
                .collect(Collectors.toList());
        List<ScheduledItem> scheduledRest = outcome.getItems().stream()
                .filter(i -> i.getType() != ScheduledItemType.TASK)
                .collect(Collectors.toList());

        ScheduleResult result = new ScheduleResult();
        result.setScheduledTasks(scheduledTasks);
        result.setScheduledHabits(scheduledRest);
        result.setUnscheduledTasks(findUnscheduledTasks(input.getTasks(), scheduledTasks));
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

        int floorSlot(LocalDateTime t) {
            return (int) Math.floorDiv(ChronoUnit.MINUTES.between(origin, t), GRID);
        }

        /** Aufrunden — für untere Schranken, damit nicht in einen angebrochenen Slot geplant wird. */
        int ceilSlot(LocalDateTime t) {
            long m = ChronoUnit.MINUTES.between(origin, t);
            return (int) -Math.floorDiv(-m, GRID);
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
                                    List<DayWindow> windows) {
        Placeable p = new Placeable();
        p.sizeSlots   = sizeSlots;
        p.realMinutes = realMinutes;

        int lb = windows.stream().mapToInt(DayWindow::lo).min().orElse(0);
        int ub = windows.stream().mapToInt(DayWindow::hi).max().orElse(lb);
        p.present = model.newBoolVar("p_" + name);
        p.start   = model.newIntVar(lb, Math.max(lb, ub), "s_" + name);
        p.interval = model.newOptionalFixedSizeIntervalVar(p.start, sizeSlots, p.present, "iv_" + name);

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
     */
    private List<int[]> collectBlockedSlots(Axis axis, ScheduleInput input,
                                            LocalDate startDate, LocalDate endDate, int bufferSlots) {
        List<int[]> raw = new ArrayList<>();

        for (CalendarEvent ev : nz(input.getFixedEvents())) {
            if (ev.getStartTime() == null || ev.getEndTime() == null) continue;
            addBlock(raw, axis, ev.getStartTime(), ev.getEndTime(), bufferSlots);
        }

        for (CourseSchedule cs : nz(input.getCourseSchedules())) {
            if (cs.getDayOfWeek() == null || cs.getStartTime() == null || cs.getEndTime() == null) continue;
            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                if (d.getDayOfWeek() == cs.getDayOfWeek()) {
                    addBlock(raw, axis, d.atTime(cs.getStartTime()), d.atTime(cs.getEndTime()), bufferSlots);
                }
            }
        }

        for (WorkoutSession w : nz(input.getFixedWorkouts())) {
            if (w.getStartTime() == null) continue;
            LocalDateTime end = w.getEndTime() != null
                    ? w.getEndTime()
                    : w.getStartTime().plusMinutes(nz(w.getDurationMinutes(), DEFAULT_WORKOUT_DURATION_MIN));
            addBlock(raw, axis, w.getStartTime(), end, bufferSlots);
        }

        return mergeBlocked(raw, axis.horizonSlots);
    }

    private void addBlock(List<int[]> out, Axis axis, LocalDateTime start, LocalDateTime end, int bufferSlots) {
        int s = axis.floorSlot(start) - bufferSlots;
        int e = axis.ceilSlot(end) + bufferSlots;
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

    private Map<Long, Integer> pinnedMinutesPerTask(List<CalendarEvent> fixedEvents) {
        return nz(fixedEvents).stream()
                .filter(e -> e.getRelatedTask() != null && e.getStartTime() != null && e.getEndTime() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getRelatedTask().getId(),
                        Collectors.summingInt(e -> (int) ChronoUnit.MINUTES.between(e.getStartTime(), e.getEndTime()))));
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
                                             LocalDate startDate, LocalDate endDate) {
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
                    .collect(Collectors.toSet());

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
                                        LocalDate startDate, LocalDate endDate) {

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

        // Platzhalter-Zuordnung für flexible Workouts, bewusst lokal: ein Feld würde
        // zwischen zwei Läufen (auch verschiedener User) Zustand verschleppen.
        Map<Long, Placeable> workoutPlaceables = new HashMap<>();

        List<int[]> blocked = collectBlockedSlots(axis, input, startDate, endDate, bufferSlots);

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

                List<DayWindow> windows = dayWindows(axis, workStartSlot, workEndSlot, sizeSlots, earliest, null);
                Placeable p = makePlaceable(model, name, sizeSlots, c.durationMinutes, windows);
                c.placeable = p;
                allPlaceables.add(p);
                allIntervals.add(p.interval);

                dropB.addTerm(p.present, -weight);
                dropConst += weight;

                int prio = Math.max(1, nz(task.getPriority(), 3));

                // Dringlichkeit: früher ist besser.
                qVars.add(gated(model, p, "urg_" + name, p.start, axis.horizonSlots));
                qWeights.add(W_URGENCY * prio);

                // Deadline ist jetzt WEICH. Vorher wurde ein überfälliger Task hart ans
                // Horizontende geklemmt; jetzt landet er so früh wie möglich und wird gemeldet.
                if (deadlineSlot != null) {
                    IntVar late = model.newIntVar(0, axis.horizonSlots, "late_" + name);
                    model.addGreaterOrEqual(
                            LinearExpr.newBuilder().addTerm(late, 1).addTerm(p.start, -1).build(),
                            (long) sizeSlots - deadlineSlot).onlyEnforceIf(p.present);
                    model.addEquality(late, 0).onlyEnforceIf(p.present.not());
                    qVars.add(late);
                    qWeights.add(W_LATE * prio);
                }

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
        }

        // --- Habit-Slots ---
        for (int i = 0; i < habitSlots.size(); i++) {
            HabitSlot s = habitSlots.get(i);
            int sizeSlots = Axis.slotsFor(s.durationMinutes);
            String name = "habit" + s.habit.getId() + "_" + i;

            List<DayWindow> windows = dayWindows(axis, workStartSlot, workEndSlot, sizeSlots, nowSlot, s.allowedDays);
            Placeable p = makePlaceable(model, name, sizeSlots, s.durationMinutes, windows);
            s.placeable = p;
            allPlaceables.add(p);
            allIntervals.add(p.interval);

            long weight = calculateHabitWeight(s.habit);
            dropB.addTerm(p.present, -weight);
            dropConst += weight;

            // Hinge-Loss: innerhalb des Fensters kostenfrei, außerhalb linear ansteigend.
            // Die harte Schranke bleibt die Arbeitszeit, nicht das Fenster — ein Habit, das nicht
            // in sein Wunschfenster passt, wird lieber daneben geplant als verworfen.
            IntVar dev = model.newIntVar(0, axis.horizonSlots, "dev_" + name);
            for (Map.Entry<Integer, BoolVar> de : p.inDay.entrySet()) {
                int dayBase = de.getKey() * SLOTS_PER_DAY;
                int wLo = dayBase + s.windowStartMin / GRID;
                // Spätester Start, bei dem der Block noch im Fenster endet. Bei einem
                // Punktwunsch (start == end) fallen wLo und wHi zusammen, womit die Abweichung
                // exakt |start − preferredTime| ist — das alte Verhalten.
                int wHi = s.windowEndMin > s.windowStartMin
                        ? Math.max(wLo, dayBase + s.windowEndMin / GRID - sizeSlots)
                        : wLo;
                model.addGreaterOrEqual(
                        LinearExpr.newBuilder().addTerm(dev, 1).addTerm(p.start, 1).build(), wLo)
                     .onlyEnforceIf(de.getValue());
                model.addGreaterOrEqual(
                        LinearExpr.newBuilder().addTerm(dev, 1).addTerm(p.start, -1).build(), -wHi)
                     .onlyEnforceIf(de.getValue());
            }
            model.addEquality(dev, 0).onlyEnforceIf(p.present.not());
            qVars.add(dev);
            qWeights.add(W_HABIT_DEV * Math.max(1, nz(s.habit.getPriority(), 3)));

            String key = "habit:" + s.habit.getId() + ":" + (s.legacyDate != null ? s.legacyDate : i);
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

            List<DayWindow> windows = dayWindows(axis, workStartSlot, workEndSlot, sizeSlots, nowSlot, days);
            Placeable p = makePlaceable(model, name, sizeSlots, duration, windows);
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
        for (int d = 0; d < axis.totalDays; d++) {
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

    /** Pro Tag ein erlaubtes Startfenster, bereits um "jetzt" und die Arbeitszeit beschnitten. */
    private List<DayWindow> dayWindows(Axis axis, int workStartSlot, int workEndSlot, int sizeSlots,
                                       int earliestSlot, List<Integer> restrictToDays) {
        List<DayWindow> out = new ArrayList<>();
        for (int d = 0; d < axis.totalDays; d++) {
            if (restrictToDays != null && !restrictToDays.contains(d)) continue;
            int base = d * SLOTS_PER_DAY;
            int lo = Math.max(base + workStartSlot, earliestSlot);
            int hi = base + workEndSlot - sizeSlots;
            if (lo <= hi) out.add(new DayWindow(d, lo, hi));
        }
        return out;
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
        Map<Long, List<CalendarEvent>> byTask = new HashMap<>();

        for (CalendarEvent ev : nz(input.getPreviousScheduledEvents())) {
            if (ev.getStartTime() == null) continue;
            if (ev.getRelatedTask() != null) {
                byTask.computeIfAbsent(ev.getRelatedTask().getId(), k -> new ArrayList<>()).add(ev);
            } else if (ev.getRelatedHabit() != null) {
                out.put("habit:" + ev.getRelatedHabit().getId() + ":" + ev.getStartTime().toLocalDate(),
                        axis.floorSlot(ev.getStartTime()));
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

    private ScheduleInput collectScheduleInput(Long userId, LocalDate startDate, LocalDate endDate) {
        ScheduleInput input = new ScheduleInput();
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end   = endDate.atTime(23, 59, 59);

        input.setTasks(taskService.getSchedulableTasks(userId));
        input.setFixedEvents(calendarEventService.getFixedEvents(userId, start, end));
        input.setHabits(habitRepository.findHabitsActiveInRange(userId, startDate, endDate));
        input.setCourseSchedules(courseScheduleRepository.findByUserId(userId));

        // Vor dem Löschen einsammeln: der Stabilitätsterm braucht die bisherigen Platzierungen.
        input.setPreviousScheduledEvents(calendarEventRepository
                .findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                        userId, List.of(EventType.TASK, EventType.HABIT, EventType.WORKOUT),
                        false, start, end));

        List<WorkoutSession> inRange = workoutSessionRepository.findByUserIdAndStartTimeBetween(userId, start, end);
        List<WorkoutSession> flexible = workoutSessionRepository.findByUserIdAndIsFlexibleTrue(userId).stream()
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
                default -> { continue; }
            }

            calendarEventRepository.save(ev);
        }

        log.info("Gespeichert: {} Events", scheduled.size());
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
     */
    private void writeBackTaskSpans(SolveOutcome outcome, List<Task> allTasks) {
        Map<Long, List<ScheduledItem>> byTask = outcome.getItems().stream()
                .filter(i -> i.getTask() != null)
                .collect(Collectors.groupingBy(i -> i.getTask().getId()));

        byTask.forEach((id, items) -> taskService.scheduleTask(id,
                items.stream().map(ScheduledItem::getStartTime).min(Comparator.naturalOrder()).orElseThrow(),
                items.stream().map(ScheduledItem::getEndTime).max(Comparator.naturalOrder()).orElseThrow()));

        for (Task t : nz(allTasks)) {
            if (!byTask.containsKey(t.getId())
                    && (t.getScheduledStartTime() != null || t.getScheduledEndTime() != null)) {
                taskService.clearSchedule(t.getId());
            }
        }
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

    private List<Task> findUnscheduledTasks(List<Task> all, List<ScheduledItem> scheduled) {
        Set<Long> ids = scheduled.stream()
                .filter(i -> i.getTask() != null)
                .map(i -> i.getTask().getId())
                .collect(Collectors.toSet());
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
