package com.Finn.everything_app.service;

import com.Finn.everything_app.event.ScheduleChangedEvent;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Smart Scheduler using Google OR-Tools CP-SAT solver.
 *
 * Mental model (Motion / Reclaim style):
 *  - Time axis  : integer line of minutes from startDate (minute 0) to endDate (minute horizon).
 *  - Fixed blocks: sleep windows + calendar events → unmovable IntervalVars.
 *  - Tasks       : IntervalVars with fixed duration but variable start.
 *  - Engine      : addNoOverlap() ensures nothing collides.
 *  - Objective   : minimize Σ weight_i × start_i so urgent / high-priority tasks
 *                  are pushed to the earliest available slot automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmartSchedulerService {

    private final TaskRepository             taskRepository;
    private final CalendarEventRepository    calendarEventRepository;
    private final HabitRepository            habitRepository;
    private final WorkoutSessionRepository   workoutSessionRepository;
    private final CourseScheduleRepository   courseScheduleRepository;
    private final UserService                userService;
    private final CalendarEventService       calendarEventService;
    private final TaskService                taskService;

    private static final double SOLVER_TIME_LIMIT_SECONDS = 10.0;
    private static final int    MIN_SLOT_MINUTES          = 10;

    // Load OR-Tools JNI libraries once on class initialisation
    static {
        Loader.loadNativeLibraries();
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    @EventListener
    public void onScheduleChanged(ScheduleChangedEvent event) {
        log.info("ScheduleChangedEvent für User {}", event.getUserId());
        generateOptimalSchedule(event.getUserId(), LocalDate.now(), LocalDate.now().plusDays(14));
    }

    @Transactional
    public ScheduleResult generateOptimalSchedule(Long userId, LocalDate startDate, LocalDate endDate) {
        log.info("Generiere CP-SAT Schedule für User {} | {} – {}", userId, startDate, endDate);

        UserPreferences prefs = userService.getUserPreferences(userId);
        calendarEventService.clearScheduledEvents(userId);
        ScheduleInput input = collectScheduleInput(userId, startDate, endDate);

        // ---- Core: CP-SAT task scheduling ----
        List<ScheduledItem> scheduledTasks =
                solveWithCpSat(input.getTasks(), input.getFixedEvents(), startDate, endDate, prefs);

        // ---- Habits / workouts: greedy into remaining free slots ----
        List<TimeSlot> freeSlots =
                buildFreeSlots(scheduledTasks, input.getFixedEvents(), startDate, endDate, prefs);
        List<ScheduledItem> scheduledHabits =
                scheduleRecurringActivities(input.getHabits(), input.getWorkouts(), freeSlots, prefs);

        saveScheduleToDatabase(userId, scheduledTasks, scheduledHabits);

        ScheduleResult result = new ScheduleResult();
        result.setScheduledTasks(scheduledTasks);
        result.setScheduledHabits(scheduledHabits);
        result.setUnscheduledTasks(findUnscheduledTasks(input.getTasks(), scheduledTasks));
        result.setTotalTasksScheduled(scheduledTasks.size());
        result.setTotalHoursScheduled(calculateTotalHours(scheduledTasks, scheduledHabits));

        log.info("Schedule fertig: {} Tasks, {} Habits, {} unscheduled",
                scheduledTasks.size(), scheduledHabits.size(), result.getUnscheduledTasks().size());
        return result;
    }

    // =========================================================================
    // CP-SAT SOLVER
    // =========================================================================

    private List<ScheduledItem> solveWithCpSat(
            List<Task> tasks,
            List<CalendarEvent> fixedEvents,
            LocalDate startDate,
            LocalDate endDate,
            UserPreferences prefs) {

        List<Task> valid = tasks.stream()
                .filter(t -> t.getEstimatedDurationMinutes() != null && t.getEstimatedDurationMinutes() > 0)
                .collect(Collectors.toList());
        if (valid.isEmpty()) return Collections.emptyList();

        CpModel model = new CpModel();

        // --- Time axis ---
        LocalDateTime horizonStart = startDate.atStartOfDay();
        int totalDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        int horizon   = totalDays * 1440;

        LocalTime workStart  = prefs.getWorkdayStart() != null ? prefs.getWorkdayStart() : LocalTime.of(8, 0);
        LocalTime workEnd    = prefs.getWorkdayEnd()   != null ? prefs.getWorkdayEnd()   : LocalTime.of(22, 0);
        int workStartMin     = workStart.getHour() * 60 + workStart.getMinute();
        int workEndMin       = workEnd.getHour()   * 60 + workEnd.getMinute();

        // Earliest start = right now (tasks cannot be placed in the past)
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
                allIntervals.add(fixedBlock(model, (int) evS, (int)(evE - evS), "ev_" + ev.getId()));
        }

        // --- 3. Task interval variables ---
        IntVar[]      startVars = new IntVar[valid.size()];
        IntervalVar[] taskIvs   = new IntervalVar[valid.size()];
        long[]        weights   = new long[valid.size()];

        for (int i = 0; i < valid.size(); i++) {
            Task task     = valid.get(i);
            int  duration = task.getEstimatedDurationMinutes();

            // Upper bound: horizon or deadline, whichever comes first
            int latestStart = horizon - duration;
            if (task.getDeadline() != null) {
                long deadlineMin = ChronoUnit.MINUTES.between(horizonStart, task.getDeadline());
                latestStart = (int) Math.min(latestStart, deadlineMin - duration);
            }
            // Guard: if deadline already passed, schedule best-effort at end of horizon
            if (latestStart < nowOffset) {
                log.warn("Task '{}' hat überschrittene Deadline – Best-Effort-Placement.", task.getTitle());
                latestStart = horizon - duration;
            }

            int lb = Math.max(0, nowOffset);
            int ub = Math.max(lb, latestStart);

            startVars[i] = model.newIntVar(lb, ub, "s_" + task.getId());
            taskIvs[i]   = model.newFixedSizeIntervalVar(startVars[i], duration, "iv_" + task.getId());
            weights[i]   = calculateTaskWeight(task);
            allIntervals.add(taskIvs[i]);
        }

        // --- 4. Core constraint: nothing overlaps ---
        model.addNoOverlap(allIntervals.toArray(new IntervalVar[0]));

        // --- 5. Objective: minimize Σ weight_i × start_i ---
        // High weight tasks (urgent / high priority) are pushed to early slots.
        model.minimize(LinearExpr.weightedSum(startVars, weights));

        // --- 6. Solve ---
        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(SOLVER_TIME_LIMIT_SECONDS);
        solver.getParameters().setNumSearchWorkers(4);
        solver.getParameters().setLogSearchProgress(false);

        CpSolverStatus status = solver.solve(model);
        log.info("CP-SAT Status: {} | Objective: {}", status, solver.objectiveValue());

        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            log.warn("CP-SAT: kein gültiger Schedule gefunden ({}). Schedule bleibt leer.", status);
            return Collections.emptyList();
        }

        // --- 7. Extract solution ---
        List<ScheduledItem> result = new ArrayList<>();
        for (int i = 0; i < valid.size(); i++) {
            long startMin          = solver.value(startVars[i]);
            LocalDateTime taskStart = horizonStart.plusMinutes(startMin);
            LocalDateTime taskEnd   = taskStart.plusMinutes(valid.get(i).getEstimatedDurationMinutes());

            ScheduledItem item = new ScheduledItem();
            item.setTask(valid.get(i));
            item.setStartTime(taskStart);
            item.setEndTime(taskEnd);
            item.setType(ScheduledItemType.TASK);
            result.add(item);

            taskService.scheduleTask(valid.get(i).getId(), taskStart, taskEnd);
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

    // =========================================================================
    // FREE-SLOT GENERATION  (used by habit / workout greedy scheduler)
    // =========================================================================

    /**
     * Generates free TimeSlots within working hours, subtracting both fixed
     * calendar events and the tasks already placed by the CP-SAT solver.
     */
    private List<TimeSlot> buildFreeSlots(
            List<ScheduledItem> scheduled,
            List<CalendarEvent> fixedEvents,
            LocalDate startDate, LocalDate endDate,
            UserPreferences prefs) {

        // Combine all occupied windows
        List<CalendarEvent> occupied = new ArrayList<>(fixedEvents);
        for (ScheduledItem item : scheduled) {
            CalendarEvent pseudo = new CalendarEvent();
            pseudo.setStartTime(item.getStartTime());
            pseudo.setEndTime(item.getEndTime());
            occupied.add(pseudo);
        }

        List<TimeSlot> slots = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1))
            slots.addAll(generateDaySlots(d, prefs, occupied));
        return slots;
    }

    private List<TimeSlot> generateDaySlots(LocalDate date, UserPreferences prefs,
                                             List<CalendarEvent> occupied) {
        LocalTime workStart = prefs.getWorkdayStart() != null ? prefs.getWorkdayStart() : LocalTime.of(8, 0);
        LocalTime workEnd   = prefs.getWorkdayEnd()   != null ? prefs.getWorkdayEnd()   : LocalTime.of(22, 0);

        LocalDateTime cur      = date.atTime(workStart);
        LocalDateTime endOfDay = date.atTime(workEnd);

        List<CalendarEvent> dayOcc = occupied.stream()
                .filter(e -> e.getStartTime() != null && e.getStartTime().toLocalDate().equals(date))
                .sorted(Comparator.comparing(CalendarEvent::getStartTime))
                .toList();

        List<TimeSlot> slots = new ArrayList<>();
        for (CalendarEvent ev : dayOcc) {
            if (cur.isBefore(ev.getStartTime())) {
                long dur = ChronoUnit.MINUTES.between(cur, ev.getStartTime());
                if (dur >= MIN_SLOT_MINUTES) slots.add(makeSlot(cur, ev.getStartTime(), date, (int) dur));
            }
            if (ev.getEndTime() != null && ev.getEndTime().isAfter(cur)) cur = ev.getEndTime();
        }
        if (cur.isBefore(endOfDay)) {
            long dur = ChronoUnit.MINUTES.between(cur, endOfDay);
            if (dur >= MIN_SLOT_MINUTES) slots.add(makeSlot(cur, endOfDay, date, (int) dur));
        }
        return slots;
    }

    private TimeSlot makeSlot(LocalDateTime start, LocalDateTime end, LocalDate date, int dur) {
        TimeSlot s = new TimeSlot();
        s.setStart(start); s.setEnd(end); s.setDate(date); s.setDuration(dur);
        return s;
    }

    // =========================================================================
    // HABITS & WORKOUTS  (greedy – unchanged logic)
    // =========================================================================

    private List<ScheduledItem> scheduleRecurringActivities(
            List<Habit> habits, List<WorkoutSession> workouts,
            List<TimeSlot> slots, UserPreferences prefs) {

        List<ScheduledItem> scheduled = new ArrayList<>();

        for (Habit habit : habits) {
            ScheduledItem item = scheduleHabit(habit, slots, prefs);
            if (item != null) {
                scheduled.add(item);
                int dur = habit.getDurationMinutes() != null ? habit.getDurationMinutes() : 30;
                TimeSlot used = findSlotForTime(item.getStartTime(), slots);
                if (used != null) updateSlotAfterScheduling(used, dur, slots);
            }
        }

        for (WorkoutSession wo : workouts) {
            if (wo.getStartTime() != null) {
                ScheduledItem item = new ScheduledItem();
                item.setWorkoutSession(wo);
                item.setStartTime(wo.getStartTime());
                item.setEndTime(wo.getEndTime() != null
                        ? wo.getEndTime()
                        : wo.getStartTime().plusMinutes(wo.getDurationMinutes() != null ? wo.getDurationMinutes() : 60));
                item.setType(ScheduledItemType.WORKOUT);
                scheduled.add(item);
            }
        }
        return scheduled;
    }

    private ScheduledItem scheduleHabit(Habit habit, List<TimeSlot> slots, UserPreferences prefs) {
        LocalTime preferred = habit.getPreferredTime() != null ? habit.getPreferredTime() : LocalTime.of(9, 0);
        int duration = habit.getDurationMinutes() != null ? habit.getDurationMinutes() : 30;

        return slots.stream()
                .filter(s -> s.getDuration() >= duration)
                .min(Comparator.comparingLong(s ->
                        Math.abs(ChronoUnit.MINUTES.between(s.getStart().toLocalTime(), preferred))))
                .map(best -> {
                    ScheduledItem item = new ScheduledItem();
                    item.setHabit(habit);
                    item.setStartTime(best.getStart());
                    item.setEndTime(best.getStart().plusMinutes(duration));
                    item.setType(ScheduledItemType.HABIT);
                    return item;
                }).orElse(null);
    }

    private void updateSlotAfterScheduling(TimeSlot slot, int usedMin, List<TimeSlot> remaining) {
        int leftover = slot.getDuration() - usedMin;
        if (leftover < MIN_SLOT_MINUTES) remaining.remove(slot);
        else { slot.setStart(slot.getStart().plusMinutes(usedMin)); slot.setDuration(leftover); }
    }

    private TimeSlot findSlotForTime(LocalDateTime time, List<TimeSlot> slots) {
        return slots.stream()
                .filter(s -> !s.getStart().isAfter(time) && !s.getEnd().isBefore(time))
                .findFirst().orElse(null);
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
        input.setHabits(habitRepository.findActiveHabits(userId, startDate));
        input.setWorkouts(workoutSessionRepository.findByUserIdAndStartTimeBetween(userId, start, end));
        input.setCourseSchedules(courseScheduleRepository.findByUserId(userId));

        log.debug("Input: {} Tasks, {} fixe Events, {} Habits, {} Workouts",
                input.getTasks().size(), input.getFixedEvents().size(),
                input.getHabits().size(), input.getWorkouts().size());
        return input;
    }

    // =========================================================================
    // PERSISTENCE
    // =========================================================================

    @Transactional
    private void saveScheduleToDatabase(Long userId,
                                         List<ScheduledItem> scheduledTasks,
                                         List<ScheduledItem> scheduledHabits) {
        User user = userService.findById(userId);

        for (ScheduledItem item : scheduledTasks) {
            CalendarEvent ev = new CalendarEvent();
            ev.setUser(user);
            ev.setTitle(item.getTask().getTitle());
            ev.setDescription(item.getTask().getDescription());
            ev.setStartTime(item.getStartTime());
            ev.setEndTime(item.getEndTime());
            ev.setEventType(EventType.TASK);
            ev.setRelatedTask(item.getTask());
            ev.setIsFixed(false);
            ev.setColor(getColorForTask(item.getTask()));
            calendarEventRepository.save(ev);
        }

        for (ScheduledItem item : scheduledHabits) {
            CalendarEvent ev = new CalendarEvent();
            ev.setUser(user);
            ev.setStartTime(item.getStartTime());
            ev.setEndTime(item.getEndTime());
            ev.setIsFixed(false);
            if (item.getType() == ScheduledItemType.HABIT && item.getHabit() != null) {
                ev.setTitle(item.getHabit().getName());
                ev.setDescription(item.getHabit().getDescription());
                ev.setEventType(EventType.HABIT);
                ev.setRelatedHabit(item.getHabit());
                ev.setColor("#4CAF50");
            } else if (item.getType() == ScheduledItemType.WORKOUT && item.getWorkoutSession() != null) {
                ev.setTitle(item.getWorkoutSession().getName());
                ev.setDescription(item.getWorkoutSession().getDescription());
                ev.setEventType(EventType.WORKOUT);
                ev.setRelatedWorkout(item.getWorkoutSession());
                ev.setColor("#FF5722");
            }
            calendarEventRepository.save(ev);
        }

        log.info("Gespeichert: {} Task-Events, {} Habit/Workout-Events",
                scheduledTasks.size(), scheduledHabits.size());
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