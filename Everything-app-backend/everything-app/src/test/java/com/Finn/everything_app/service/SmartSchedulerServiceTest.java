package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the CP-SAT-based SmartSchedulerService.
 *
 * The CP-SAT solver is exercised with real OR-Tools native libraries
 * (bundled in the ortools-java JAR). Repository / service dependencies
 * are mocked with Mockito.
 */
@ExtendWith(MockitoExtension.class)
class SmartSchedulerServiceTest {

    @Mock TaskRepository            taskRepository;
    @Mock CalendarEventRepository   calendarEventRepository;
    @Mock HabitRepository           habitRepository;
    @Mock HabitCompletionRepository habitCompletionRepository;
    @Mock WorkoutSessionRepository  workoutSessionRepository;
    @Mock CourseScheduleRepository  courseScheduleRepository;
    @Mock UserService               userService;
    @Mock CalendarEventService      calendarEventService;
    @Mock TaskService               taskService;
    @Mock WorkoutPlanService        workoutPlanService;

    @InjectMocks
    SmartSchedulerService service;

    private UserPreferences prefs;
    private final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);

        prefs = new UserPreferences();
        prefs.setWorkdayStart(LocalTime.of(8, 0));
        prefs.setWorkdayEnd(LocalTime.of(17, 0));   // 08:00–17:00

        lenient().when(userService.getOrCreatePreferences(1L)).thenReturn(prefs);
        lenient().when(userService.findById(1L)).thenReturn(user);
        lenient().when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        lenient().when(habitCompletionRepository.findByHabitIdAndCompletionDateBetween(anyLong(), any(), any()))
                 .thenReturn(new ArrayList<>());
        lenient().when(workoutSessionRepository.findByUserIdAndStartTimeBetween(eq(1L), any(), any()))
                 .thenReturn(new ArrayList<>());
        lenient().when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(eq(1L)))
                 .thenReturn(new ArrayList<>());
        lenient().when(courseScheduleRepository.findByUserId(1L)).thenReturn(new ArrayList<>());
        lenient().when(workoutPlanService.getActivePlan(1L)).thenReturn(null);
    }

    // ------------------------------------------------------------------
    // Test 1 – Task is scheduled AFTER a fixed (pinned) block
    // ------------------------------------------------------------------
    @Test
    void taskScheduledAfterPinnedBlock() {
        LocalDate tomorrow = TODAY.plusDays(1);
        // A 60-minute task
        Task task = makeTask(10L, "Report", 60, 3, tomorrow.plusDays(2).atTime(23, 59));
        when(taskService.getUnscheduledTasks(1L)).thenReturn(List.of(task));

        // Meeting occupies 08:00–10:00 (= the first available working window)
        CalendarEvent meeting = makeFixedEvent(20L, tomorrow.atTime(8, 0), tomorrow.atTime(10, 0));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(meeting));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertEquals(1, result.getScheduledTasks().size(), "Task must be scheduled");
        ScheduledItem scheduled = result.getScheduledTasks().get(0);

        // Task must start at or after the meeting ends (10:00)
        assertFalse(scheduled.getStartTime().isBefore(meeting.getEndTime()),
                "Task must not overlap the pinned meeting");
        // Task must end at or before working-day end (17:00)
        assertFalse(scheduled.getEndTime().isAfter(tomorrow.atTime(17, 0)),
                "Task must end within working hours");

        // With CP-SAT minimising start time, the optimal placement is exactly 10:00
        assertEquals(tomorrow.atTime(10, 0), scheduled.getStartTime(),
                "CP-SAT should place task immediately after the meeting");
        assertEquals(tomorrow.atTime(11, 0), scheduled.getEndTime());
    }

    // ------------------------------------------------------------------
    // Test 2 – Higher-priority task gets an earlier slot than lower-priority
    // ------------------------------------------------------------------
    @Test
    void highPriorityTaskScheduledBeforeLowPriority() {
        LocalDate tomorrow = TODAY.plusDays(1);
        // Two tasks, no meetings. High-priority task must get the earlier slot.
        Task lowPriority  = makeTask(1L, "Low",  60, 1, tomorrow.plusDays(5).atTime(23, 59));
        Task highPriority = makeTask(2L, "High", 60, 5, tomorrow.plusDays(5).atTime(23, 59));

        // Add in "wrong" order to verify solver reorders them by weight
        when(taskService.getUnscheduledTasks(1L)).thenReturn(List.of(lowPriority, highPriority));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertEquals(2, result.getScheduledTasks().size());

        ScheduledItem highItem = result.getScheduledTasks().stream()
                .filter(i -> i.getTask().getId().equals(2L)).findFirst().orElseThrow();
        ScheduledItem lowItem  = result.getScheduledTasks().stream()
                .filter(i -> i.getTask().getId().equals(1L)).findFirst().orElseThrow();

        assertTrue(highItem.getStartTime().isBefore(lowItem.getStartTime()) ||
                   highItem.getStartTime().isEqual(lowItem.getStartTime()),
                "High-priority task must be scheduled no later than low-priority task");
    }

    // ------------------------------------------------------------------
    // Test 3 – Task with deadline gets a slot before the deadline
    // ------------------------------------------------------------------
    @Test
    void taskRespectDeadline() {
        // Use tomorrow so nowOffset=0 and a full workday window is available
        LocalDate tomorrow = TODAY.plusDays(1);
        LocalDateTime deadline = tomorrow.atTime(12, 0); // must finish by noon tomorrow
        Task task = makeTask(3L, "Urgent", 60, 3, deadline);
        when(taskService.getUnscheduledTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertEquals(1, result.getScheduledTasks().size());
        ScheduledItem item = result.getScheduledTasks().get(0);
        assertFalse(item.getEndTime().isAfter(deadline),
                "Task must finish before its deadline");
    }

    // ------------------------------------------------------------------
    // Test 4 – Habit only ever lands on its allowed weekday (joint model)
    // ------------------------------------------------------------------
    @Test
    void habitOnlyScheduledOnAllowedWeekdays() {
        LocalDate tomorrow = TODAY.plusDays(1);
        LocalDate rangeEnd = tomorrow.plusDays(13); // two full weeks

        Habit habit = new Habit();
        habit.setId(50L);
        habit.setName("Yoga");
        habit.setMonday(false); habit.setTuesday(false); habit.setWednesday(true);
        habit.setThursday(false); habit.setFriday(false); habit.setSaturday(false); habit.setSunday(false);
        habit.setDurationMinutes(30);
        habit.setPreferredTime(LocalTime.of(9, 0));
        habit.setStartDate(tomorrow.minusDays(30));

        when(taskService.getUnscheduledTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(habit));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, rangeEnd);

        List<ScheduledItem> habitItems = result.getScheduledHabits().stream()
                .filter(i -> i.getType() == ScheduledItemType.HABIT)
                .toList();

        assertFalse(habitItems.isEmpty(), "Habit should be scheduled at least once in a 2-week window");
        for (ScheduledItem item : habitItems) {
            assertEquals(DayOfWeek.WEDNESDAY, item.getStartTime().getDayOfWeek(),
                    "Habit must only ever be placed on its allowed weekday");
        }
    }

    // ------------------------------------------------------------------
    // Test 5 – Habit is pulled toward its preferredTime, not the horizon start
    // ------------------------------------------------------------------
    @Test
    void habitDeviationPrefersPreferredTime() {
        LocalDate tomorrow = TODAY.plusDays(1);

        Habit habit = new Habit();
        habit.setId(51L);
        habit.setName("Meditation");
        habit.setMonday(true); habit.setTuesday(true); habit.setWednesday(true);
        habit.setThursday(true); habit.setFriday(true); habit.setSaturday(true); habit.setSunday(true);
        habit.setDurationMinutes(30);
        habit.setPreferredTime(LocalTime.of(9, 0));
        habit.setStartDate(tomorrow.minusDays(5));

        when(taskService.getUnscheduledTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(habit));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        List<ScheduledItem> habitItems = result.getScheduledHabits().stream()
                .filter(i -> i.getType() == ScheduledItemType.HABIT)
                .toList();

        assertEquals(1, habitItems.size());
        assertEquals(tomorrow.atTime(9, 0), habitItems.get(0).getStartTime(),
                "With no competing items, the habit should land exactly on its preferred time");
    }

    // ------------------------------------------------------------------
    // Test 6 – Flexible workout placeholder gets placed and written back
    // ------------------------------------------------------------------
    @Test
    void workoutPlaceholderGetsPlacedAndWrittenBack() {
        LocalDate tomorrow = TODAY.plusDays(1);
        LocalDate weekStart = tomorrow.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        WorkoutSession placeholder = new WorkoutSession();
        placeholder.setId(60L);
        placeholder.setName("Leg Day");
        placeholder.setDurationMinutes(45);
        placeholder.setIsFlexible(true);
        placeholder.setIsCompleted(false);
        placeholder.setTargetWeekStart(weekStart);

        when(taskService.getUnscheduledTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(1L)).thenReturn(List.of(placeholder));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        List<ScheduledItem> workoutItems = result.getScheduledHabits().stream()
                .filter(i -> i.getType() == ScheduledItemType.WORKOUT)
                .toList();

        assertEquals(1, workoutItems.size());
        LocalDateTime start = workoutItems.get(0).getStartTime();
        assertFalse(start.isBefore(tomorrow.atTime(8, 0)), "Workout must not start before work hours");
        assertFalse(workoutItems.get(0).getEndTime().isAfter(tomorrow.atTime(17, 0)), "Workout must end within work hours");
        assertEquals(start, placeholder.getStartTime(), "Solved time must be written back onto the session entity");
        verify(workoutSessionRepository).save(placeholder);
    }

    // ------------------------------------------------------------------
    // Test 7 – clearScheduledEvents invoked with correct bounds on every regeneration
    // (guards against re-runs duplicating HABIT/WORKOUT calendar events)
    // ------------------------------------------------------------------
    @Test
    void clearScheduledEventsInvokedWithBoundsOnEveryRegeneration() {
        LocalDate tomorrow = TODAY.plusDays(1);
        Task task = makeTask(70L, "Repeatable", 60, 3, tomorrow.plusDays(2).atTime(23, 59));
        when(taskService.getUnscheduledTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        service.generateOptimalSchedule(1L, tomorrow, tomorrow);
        service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        verify(calendarEventService, times(2)).clearScheduledEvents(
                eq(1L), eq(tomorrow.atStartOfDay()), eq(tomorrow.atTime(23, 59, 59)));
        verify(calendarEventRepository, times(2)).save(any(CalendarEvent.class));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Task makeTask(Long id, String title, int durationMin, int priority, LocalDateTime deadline) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setEstimatedDurationMinutes(durationMin);
        t.setPriority(priority);
        t.setDeadline(deadline);
        t.setStatus(TaskStatus.TODO);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    private CalendarEvent makeFixedEvent(Long id, LocalDateTime start, LocalDateTime end) {
        CalendarEvent ev = new CalendarEvent();
        ev.setId(id);
        ev.setTitle("Fixed Event");
        ev.setIsFixed(true);
        ev.setStartTime(start);
        ev.setEndTime(end);
        ev.setEventType(EventType.OTHER);
        return ev;
    }
}
