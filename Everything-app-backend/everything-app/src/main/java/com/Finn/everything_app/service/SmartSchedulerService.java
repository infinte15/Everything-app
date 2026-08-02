package com.Finn.everything_app.service;

import com.Finn.everything_app.event.ScheduleChangedEvent;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Smart Scheduler using Google OR-Tools CP-SAT solver.
 *
 * Mental model (Motion / Reclaim style):
 *  - Time axis   : integer line of minutes from startDate (minute 0) to endDate (minute horizon).
 *  - Fixed blocks: sleep windows + calendar events + course schedules + pinned workouts → unmovable IntervalVars.
 *  - Tasks       : IntervalVars with fixed duration, variable start, bounded by [now, deadline].
 *  - Habits      : one IntervalVar per required occurrence (day where the weekday flag is set),
 *                  bounded to that single day only, softly pulled toward preferredTime.
 *  - Workouts    : flexible placeholder sessions get one IntervalVar spanning their target week.
 *  - Engine      : a single addNoOverlap() across all of the above ensures nothing collides.
 *  - Objective   : minimize Σ weight_i × start_i (tasks/workouts: earlier is better) plus
 *                  Σ weight_i × deviation_i (habits: closer to their own preferred time is better).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmartSchedulerService {


    private final CalendarEventRepository    calendarEventRepository;
    private final HabitRepository            habitRepository;
    private final HabitCompletionRepository  habitCompletionRepository;
    private final WorkoutSessionRepository   workoutSessionRepository;
    private final CourseScheduleRepository   courseScheduleRepository;
    private final UserService                userService;
    private final CalendarEventService       calendarEventService;
    private final TaskService                taskService;
    private final WorkoutPlanService         workoutPlanService;

    private static final double SOLVER_TIME_LIMIT_SECONDS   = 10.0;
    private static final int    DEFAULT_HABIT_DURATION_MIN  = 30;
    private static final int    DEFAULT_WORKOUT_DURATION_MIN = 45;

    // Load OR-Tools JNI libraries once on class initialisation
    static {
        Loader.loadNativeLibraries();
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduleChanged(ScheduleChangedEvent event) {
        log.info("Asynchrones Schedule-Update für User {}", event.getUserId());
        generateOptimalSchedule(event.getUserId(), LocalDate.now(), LocalDate.now().plusDays(14));
    }

    // Automatic regeneration now fires on every Task/Habit/CalendarEvent/WorkoutPlan/WorkoutSession
    // change, so two triggers in quick succession (e.g. "create plan" immediately followed by
    // "activate plan") are common. Without serialization, two overlapping runs can both read
    // "0 existing placeholders" before either commits and each create their own set — duplicating
    // every workout event. One lock per user keeps concurrent regenerations for different users
    // independent while fully serializing a single user's runs.
    private final java.util.concurrent.ConcurrentHashMap<Long, Object> schedulingLocks = new java.util.concurrent.ConcurrentHashMap<>();

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
        calendarEventService.clearScheduledEvents(userId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

        generateWorkoutPlaceholders(userId, startDate, endDate);

        ScheduleInput input = collectScheduleInput(userId, startDate, endDate);
        List<HabitOccurrence> habitOccurrences = expandHabitOccurrences(input.getHabits(), startDate, endDate);

        List<ScheduledItem> scheduled = solveWithCpSat(
                input.getTasks(),
                habitOccurrences,
                input.getFlexibleWorkouts(),
                input.getFixedEvents(),
                input.getCourseSchedules(),
                input.getFixedWorkouts(),
                startDate, endDate, prefs);

        saveScheduleToDatabase(userId, scheduled);

        List<ScheduledItem> scheduledTasks = scheduled.stream()
                .filter(i -> i.getType() == ScheduledItemType.TASK)
                .collect(Collectors.toList());
        List<ScheduledItem> scheduledRest = scheduled.stream()
                .filter(i -> i.getType() != ScheduledItemType.TASK)
                .collect(Collectors.toList());

        ScheduleResult result = new ScheduleResult();
        result.setScheduledTasks(scheduledTasks);
        result.setScheduledHabits(scheduledRest);
        result.setUnscheduledTasks(findUnscheduledTasks(input.getTasks(), scheduledTasks));
        result.setTotalTasksScheduled(scheduledTasks.size());
        result.setTotalHoursScheduled(calculateTotalHours(scheduledTasks, scheduledRest));

        log.info("Schedule fertig: {} Tasks, {} Habits/Workouts, {} unscheduled",
                scheduledTasks.size(), scheduledRest.size(), result.getUnscheduledTasks().size());
        return result;
    }

    /** Tops up flexible WorkoutSession placeholders for every ISO week overlapping the horizon. */
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
    // HABIT OCCURRENCE EXPANSION
    // =========================================================================

    private List<HabitOccurrence> expandHabitOccurrences(List<Habit> habits, LocalDate startDate, LocalDate endDate) {
        List<HabitOccurrence> occurrences = new ArrayList<>();

        for (Habit habit : habits) {
            LocalDate rangeStart = (habit.getStartDate() != null && habit.getStartDate().isAfter(startDate))
                    ? habit.getStartDate() : startDate;
            LocalDate rangeEnd = (habit.getEndDate() != null && habit.getEndDate().isBefore(endDate))
                    ? habit.getEndDate() : endDate;
            if (rangeStart.isAfter(rangeEnd)) continue;

            Set<LocalDate> completedDates = habitCompletionRepository
                    .findByHabitIdAndCompletionDateBetween(habit.getId(), rangeStart, rangeEnd)
                    .stream()
                    .map(HabitCompletion::getCompletionDate)
                    .collect(Collectors.toSet());

            for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
                if (!isHabitScheduledOn(habit, d)) continue;
                if (completedDates.contains(d)) continue;

                HabitOccurrence occ = new HabitOccurrence();
                occ.setHabit(habit);
                occ.setDate(d);
                occ.setDurationMinutes(habit.getDurationMinutes() != null ? habit.getDurationMinutes() : DEFAULT_HABIT_DURATION_MIN);
                occ.setPreferredTime(habit.getPreferredTime() != null ? habit.getPreferredTime() : LocalTime.of(9, 0));
                occurrences.add(occ);
            }
        }
        return occurrences;
    }

    /** Mirrors the frontend's Habit.isScheduledToday() weekday-flag check. */
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
    // CP-SAT SOLVER — joint model for Tasks + Habit occurrences + flexible Workouts
    // =========================================================================

    private List<ScheduledItem> solveWithCpSat(
            List<Task> tasks,
            List<HabitOccurrence> habitOccurrences,
            List<WorkoutSession> flexibleWorkouts,
            List<CalendarEvent> fixedEvents,
            List<CourseSchedule> courseSchedules,
            List<WorkoutSession> fixedWorkouts,
            LocalDate startDate,
            LocalDate endDate,
            UserPreferences prefs) {

        List<Task> validTasks = tasks.stream()
                .filter(t -> t.getEstimatedDurationMinutes() != null && t.getEstimatedDurationMinutes() > 0)
                .collect(Collectors.toList());

        if (validTasks.isEmpty() && habitOccurrences.isEmpty() && flexibleWorkouts.isEmpty()) {
            return Collections.emptyList();
        }

        CpModel model = new CpModel();

        // --- Time axis ---
        LocalDateTime horizonStart = startDate.atStartOfDay();
        int totalDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        int horizon   = totalDays * 1440;

        LocalTime workStart  = prefs.getWorkdayStart() != null ? prefs.getWorkdayStart() : LocalTime.of(8, 0);
        LocalTime workEnd    = prefs.getWorkdayEnd()   != null ? prefs.getWorkdayEnd()   : LocalTime.of(22, 0);
        int workStartMin     = workStart.getHour() * 60 + workStart.getMinute();
        int workEndMin       = workEnd.getHour()   * 60 + workEnd.getMinute();

        // Earliest start = right now (nothing can be placed in the past)
        int nowOffset = (int) Math.max(0, ChronoUnit.MINUTES.between(horizonStart, LocalDateTime.now()));

        List<IntervalVar> allIntervals = new ArrayList<>();

        // --- 1. Block non-working hours (sleep) for every day ---
        for (int day = 0; day < totalDays; day++) {
            int off = day * 1440;
            if (workStartMin > 0)
                allIntervals.add(fixedBlock(model, off, workStartMin, "sleep_am_d" + day));
            if (workEndMin < 1440)
                allIntervals.add(fixedBlock(model, off + workEndMin, 1440 - workEndMin, "sleep_pm_d" + day));
        }

        // --- 2. Block fixed calendar events (meetings, pinned tasks) ---
        for (CalendarEvent ev : fixedEvents) {
            long evS = ChronoUnit.MINUTES.between(horizonStart, ev.getStartTime());
            long evE = ChronoUnit.MINUTES.between(horizonStart, ev.getEndTime());
            if (evE > evS && evS >= 0 && evE <= horizon)
                allIntervals.add(fixedBlock(model, (int) evS, (int) (evE - evS), "ev_" + ev.getId()));
        }

        // --- 2b. Block CourseSchedules ---
        if (courseSchedules != null) {
            for (CourseSchedule cs : courseSchedules) {
                for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                    if (d.getDayOfWeek() == cs.getDayOfWeek()) {
                        LocalDateTime csStart = d.atTime(cs.getStartTime());
                        LocalDateTime csEnd = d.atTime(cs.getEndTime());
                        long evS = ChronoUnit.MINUTES.between(horizonStart, csStart);
                        long evE = ChronoUnit.MINUTES.between(horizonStart, csEnd);
                        if (evE > evS && evS >= 0 && evE <= horizon)
                            allIntervals.add(fixedBlock(model, (int) evS, (int) (evE - evS), "cs_" + cs.getId() + "_" + d.toEpochDay()));
                    }
                }
            }
        }

        // --- 2c. Block already-pinned (non-flexible) workout sessions ---
        for (WorkoutSession w : fixedWorkouts) {
            if (w.getStartTime() == null) continue;
            LocalDateTime wEnd = w.getEndTime() != null ? w.getEndTime()
                    : w.getStartTime().plusMinutes(w.getDurationMinutes() != null ? w.getDurationMinutes() : DEFAULT_WORKOUT_DURATION_MIN);
            long evS = ChronoUnit.MINUTES.between(horizonStart, w.getStartTime());
            long evE = ChronoUnit.MINUTES.between(horizonStart, wEnd);
            if (evE > evS && evS >= 0 && evE <= horizon)
                allIntervals.add(fixedBlock(model, (int) evS, (int) (evE - evS), "wo_fixed_" + w.getId()));
        }

        // --- Objective accumulators (mixed: task/workout start-time terms + habit deviation terms) ---
        List<IntVar> objVars = new ArrayList<>();
        List<Long> objWeights = new ArrayList<>();

        // --- 3a. Task interval variables ---
        IntVar[] taskStartVars = new IntVar[validTasks.size()];
        for (int i = 0; i < validTasks.size(); i++) {
            Task task     = validTasks.get(i);
            int  duration = task.getEstimatedDurationMinutes();

            int latestStart = horizon - duration;
            if (task.getDeadline() != null) {
                long deadlineMin = ChronoUnit.MINUTES.between(horizonStart, task.getDeadline());
                latestStart = (int) Math.min(latestStart, deadlineMin - duration);
            }
            if (latestStart < nowOffset) {
                log.warn("Task '{}' hat überschrittene Deadline – Best-Effort-Placement.", task.getTitle());
                latestStart = horizon - duration;
            }

            int lb = Math.max(0, nowOffset);
            int ub = Math.max(lb, latestStart);

            IntVar startVar = model.newIntVar(lb, ub, "s_task_" + task.getId());
            IntervalVar iv  = model.newFixedSizeIntervalVar(startVar, duration, "iv_task_" + task.getId());
            allIntervals.add(iv);
            taskStartVars[i] = startVar;

            objVars.add(startVar);
            objWeights.add(calculateTaskWeight(task));
        }

        // --- 3b. Habit occurrence variables: bounded to their own day, soft pull toward preferredTime ---
        IntVar[] habitStartVars = new IntVar[habitOccurrences.size()];
        for (int i = 0; i < habitOccurrences.size(); i++) {
            HabitOccurrence occ = habitOccurrences.get(i);
            int dayOffset = (int) ChronoUnit.DAYS.between(startDate, occ.getDate()) * 1440;
            int duration  = occ.getDurationMinutes();

            int lb = dayOffset + workStartMin;
            int ub = dayOffset + workEndMin - duration;
            if (occ.getDate().equals(LocalDate.now())) lb = Math.max(lb, nowOffset);
            if (ub < lb) continue; // no room left that day — occurrence can't be placed, skip it

            IntVar startVar = model.newIntVar(lb, ub, "s_hab_" + occ.getHabit().getId() + "_" + occ.getDate());
            IntervalVar iv  = model.newFixedSizeIntervalVar(startVar, duration, "iv_hab_" + occ.getHabit().getId() + "_" + occ.getDate());
            allIntervals.add(iv);
            habitStartVars[i] = startVar;

            int preferredMin = dayOffset + occ.getPreferredTime().getHour() * 60 + occ.getPreferredTime().getMinute();
            IntVar deviation = model.newIntVar(0, 1440, "dev_hab_" + occ.getHabit().getId() + "_" + occ.getDate());
            // deviation >= start - preferred
            model.addLessOrEqual(
                    LinearExpr.newBuilder().addTerm(startVar, 1).addTerm(deviation, -1).build(),
                    preferredMin);
            // deviation >= preferred - start
            model.addGreaterOrEqual(
                    LinearExpr.newBuilder().addTerm(startVar, 1).addTerm(deviation, 1).build(),
                    preferredMin);

            objVars.add(deviation);
            objWeights.add(calculateHabitWeight(occ.getHabit()));
        }

        // --- 3c. Flexible workout placeholders: domain spans their target week ---
        IntVar[] workoutStartVars = new IntVar[flexibleWorkouts.size()];
        for (int i = 0; i < flexibleWorkouts.size(); i++) {
            WorkoutSession w = flexibleWorkouts.get(i);
            int duration = w.getDurationMinutes() != null ? w.getDurationMinutes() : DEFAULT_WORKOUT_DURATION_MIN;

            LocalDate weekStart = w.getTargetWeekStart() != null ? w.getTargetWeekStart() : startDate;
            LocalDate weekEnd   = weekStart.plusDays(6);
            LocalDate clampedStart = weekStart.isBefore(startDate) ? startDate : weekStart;
            LocalDate clampedEnd   = weekEnd.isAfter(endDate) ? endDate : weekEnd;
            if (clampedStart.isAfter(clampedEnd)) continue; // placeholder's week is outside this horizon

            int lb = (int) ChronoUnit.DAYS.between(startDate, clampedStart) * 1440;
            int ub = (int) ChronoUnit.DAYS.between(startDate, clampedEnd) * 1440 + 1440 - duration;
            lb = Math.max(lb, nowOffset);
            if (ub < lb) continue;

            IntVar startVar = model.newIntVar(lb, ub, "s_wo_" + w.getId());
            IntervalVar iv  = model.newFixedSizeIntervalVar(startVar, duration, "iv_wo_" + w.getId());
            allIntervals.add(iv);
            workoutStartVars[i] = startVar;

            objVars.add(startVar);
            objWeights.add(300L); // flat mid-priority weight — WorkoutSession has no priority field today
        }

        // --- 4. Core constraint: nothing overlaps ---
        model.addNoOverlap(allIntervals.toArray(new IntervalVar[0]));

        // --- 5. Objective: minimize combined weighted sum (task/workout start-times + habit deviations) ---
        long[] weightsArr = objWeights.stream().mapToLong(Long::longValue).toArray();
        model.minimize(LinearExpr.weightedSum(objVars.toArray(new IntVar[0]), weightsArr));

        // --- 6. Solve ---
        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(SOLVER_TIME_LIMIT_SECONDS);
        solver.getParameters().setNumSearchWorkers(4);
        solver.getParameters().setLogSearchProgress(false);

        CpSolverStatus status = solver.solve(model);
        log.info("CP-SAT Status: {} | Objective: {} | Intervals: {} | Tasks: {} Habits: {} Workouts: {}",
                status, solver.objectiveValue(), allIntervals.size(),
                validTasks.size(), habitOccurrences.size(), flexibleWorkouts.size());

        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            log.warn("CP-SAT: kein gültiger Schedule gefunden ({}). Schedule bleibt leer.", status);
            return Collections.emptyList();
        }

        // --- 7. Extract solution ---
        List<ScheduledItem> result = new ArrayList<>();

        for (int i = 0; i < validTasks.size(); i++) {
            Task task = validTasks.get(i);
            long startMin = solver.value(taskStartVars[i]);
            LocalDateTime taskStart = horizonStart.plusMinutes(startMin);
            LocalDateTime taskEnd   = taskStart.plusMinutes(task.getEstimatedDurationMinutes());

            ScheduledItem item = new ScheduledItem();
            item.setTask(task);
            item.setStartTime(taskStart);
            item.setEndTime(taskEnd);
            item.setType(ScheduledItemType.TASK);
            result.add(item);

            taskService.scheduleTask(task.getId(), taskStart, taskEnd);
        }

        for (int i = 0; i < habitOccurrences.size(); i++) {
            if (habitStartVars[i] == null) continue;
            HabitOccurrence occ = habitOccurrences.get(i);
            long startMin = solver.value(habitStartVars[i]);
            LocalDateTime habStart = horizonStart.plusMinutes(startMin);
            LocalDateTime habEnd   = habStart.plusMinutes(occ.getDurationMinutes());

            ScheduledItem item = new ScheduledItem();
            item.setHabit(occ.getHabit());
            item.setStartTime(habStart);
            item.setEndTime(habEnd);
            item.setType(ScheduledItemType.HABIT);
            result.add(item);
        }

        for (int i = 0; i < flexibleWorkouts.size(); i++) {
            if (workoutStartVars[i] == null) continue;
            WorkoutSession w = flexibleWorkouts.get(i);
            long startMin = solver.value(workoutStartVars[i]);
            LocalDateTime woStart = horizonStart.plusMinutes(startMin);
            LocalDateTime woEnd   = woStart.plusMinutes(w.getDurationMinutes() != null ? w.getDurationMinutes() : DEFAULT_WORKOUT_DURATION_MIN);

            w.setStartTime(woStart);
            w.setEndTime(woEnd);
            workoutSessionRepository.save(w);

            ScheduledItem item = new ScheduledItem();
            item.setWorkoutSession(w);
            item.setStartTime(woStart);
            item.setEndTime(woEnd);
            item.setType(ScheduledItemType.WORKOUT);
            result.add(item);
        }

        // Pinned workouts pass through unchanged so they also get a CalendarEvent row
        for (WorkoutSession w : fixedWorkouts) {
            if (w.getStartTime() == null) continue;
            ScheduledItem item = new ScheduledItem();
            item.setWorkoutSession(w);
            item.setStartTime(w.getStartTime());
            item.setEndTime(w.getEndTime() != null
                    ? w.getEndTime()
                    : w.getStartTime().plusMinutes(w.getDurationMinutes() != null ? w.getDurationMinutes() : DEFAULT_WORKOUT_DURATION_MIN));
            item.setType(ScheduledItemType.WORKOUT);
            result.add(item);
        }

        return result;
    }

    /** Creates an unmovable blocking interval (e.g. sleep window or meeting). */
    private IntervalVar fixedBlock(CpModel model, int startMin, int duration, String name) {
        IntVar s = model.newIntVar(startMin, startMin, name + "_s");
        return model.newFixedSizeIntervalVar(s, duration, name);
    }

    /**
     * Urgency weight for the CP-SAT objective.
     * Higher value → solver places the task at a lower (earlier) start minute.
     */
    private long calculateTaskWeight(Task task) {
        long w = 0;
        int priority = task.getPriority() != null ? task.getPriority() : 3;
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

    /** Habits have no deadline, only a priority — scales how strongly the solver honors preferredTime. */
    private long calculateHabitWeight(Habit habit) {
        int priority = habit.getPriority() != null ? habit.getPriority() : 3;
        return (long) priority * 100;
    }

    // =========================================================================
    // INPUT COLLECTION
    // =========================================================================

    private ScheduleInput collectScheduleInput(Long userId, LocalDate startDate, LocalDate endDate) {
        ScheduleInput input = new ScheduleInput();
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end   = endDate.atTime(23, 59, 59);

        input.setTasks(taskService.getUnscheduledTasks(userId));
        input.setFixedEvents(calendarEventService.getFixedEvents(userId, start, end));
        input.setHabits(habitRepository.findHabitsActiveInRange(userId, startDate, endDate));
        input.setCourseSchedules(courseScheduleRepository.findByUserId(userId));

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
    // PERSISTENCE
    // =========================================================================

    @Transactional
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
                    ev.setTitle(item.getTask().getTitle());
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
                default -> { continue; } // CLASS is never produced here
            }

            calendarEventRepository.save(ev);
        }

        log.info("Gespeichert: {} Events", scheduled.size());
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private List<Task> findUnscheduledTasks(List<Task> all, List<ScheduledItem> scheduled) {
        Set<Long> ids = scheduled.stream()
                .filter(i -> i.getTask() != null)
                .map(i -> i.getTask().getId())
                .collect(Collectors.toSet());
        return all.stream().filter(t -> !ids.contains(t.getId())).collect(Collectors.toList());
    }

    @SafeVarargs
    private final double calculateTotalHours(List<ScheduledItem>... lists) {
        int total = 0;
        for (List<ScheduledItem> list : lists)
            for (ScheduledItem item : list)
                total += ChronoUnit.MINUTES.between(item.getStartTime(), item.getEndTime());
        return total / 60.0;
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
