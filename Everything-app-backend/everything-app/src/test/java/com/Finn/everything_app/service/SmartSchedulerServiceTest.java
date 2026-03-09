package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SmartSchedulerServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private CalendarEventRepository calendarEventRepository;
    @Mock
    private HabitRepository habitRepository;
    @Mock
    private WorkoutSessionRepository workoutSessionRepository;
    @Mock
    private CourseScheduleRepository courseScheduleRepository;
    @Mock
    private UserService userService;
    @Mock
    private CalendarEventService calendarEventService;
    @Mock
    private TaskService taskService;

    @InjectMocks
    private SmartSchedulerService smartSchedulerService;

    private User testUser;
    private UserPreferences prefs;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);

        prefs = new UserPreferences();
        prefs.setWorkdayStart(LocalTime.of(8, 0));
        prefs.setWorkdayEnd(LocalTime.of(17, 0)); // 9 hour workday
        
        lenient().when(userService.getUserPreferences(1L)).thenReturn(prefs);
        lenient().when(userService.findById(1L)).thenReturn(testUser);
        
        // Mock empty habits, workouts, courses
        lenient().when(habitRepository.findActiveHabits(eq(1L), any(LocalDate.class))).thenReturn(new ArrayList<>());
        lenient().when(workoutSessionRepository.findByUserIdAndStartTimeBetween(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        lenient().when(courseScheduleRepository.findByUserId(1L)).thenReturn(new ArrayList<>());
    }

    @Test
    void testGenerateOptimalSchedule_WithPinnedTasks() {
        LocalDate today = LocalDate.now();
        
        // Setup 1 unpinned task
        Task unpinnedTask = new Task();
        unpinnedTask.setId(10L);
        unpinnedTask.setTitle("Unpinned Task");
        unpinnedTask.setEstimatedDurationMinutes(60);
        unpinnedTask.setPriority(3);
        unpinnedTask.setStatus(TaskStatus.TODO);
        unpinnedTask.setDeadline(LocalDateTime.now().plusDays(2));
        unpinnedTask.setCreatedAt(LocalDateTime.now());
        
        List<Task> unscheduledTasks = List.of(unpinnedTask);
        when(taskService.getUnscheduledTasks(1L)).thenReturn(unscheduledTasks);
        
        // Setup a Pinned Task acting as a hard block (Fix Event) from 8:00 to 10:00
        CalendarEvent pinnedTaskEvent = new CalendarEvent();
        pinnedTaskEvent.setId(20L);
        pinnedTaskEvent.setIsFixed(true);
        pinnedTaskEvent.setEventType(EventType.TASK);
        pinnedTaskEvent.setStartTime(today.atTime(8, 0));
        pinnedTaskEvent.setEndTime(today.atTime(10, 0));
        
        List<CalendarEvent> fixedEvents = List.of(pinnedTaskEvent);
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(fixedEvents);

        ScheduleResult result = smartSchedulerService.generateOptimalSchedule(1L, today, today);

        // Verify that the task was scheduled AFTER the pinned task
        assertEquals(1, result.getScheduledTasks().size());
        ScheduledItem scheduledTask = result.getScheduledTasks().get(0);
        
        // It should start at 10:00 because 8:00 to 10:00 is taken by the pinned task!
        assertEquals(today.atTime(10, 0), scheduledTask.getStartTime());
        assertEquals(today.atTime(11, 0), scheduledTask.getEndTime());
    }
}
