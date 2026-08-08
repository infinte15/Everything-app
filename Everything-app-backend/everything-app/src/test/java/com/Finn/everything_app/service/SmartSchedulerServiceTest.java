package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    @Mock ProjectRepository         projectRepository;
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
        lenient().when(habitCompletionRepository.findByHabitIdInAndCompletionDateBetween(any(), any(), any()))
                 .thenReturn(new ArrayList<>());
        lenient().when(workoutSessionRepository.findByUserIdAndStartTimeBetween(eq(1L), any(), any()))
                 .thenReturn(new ArrayList<>());
        lenient().when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(eq(1L)))
                 .thenReturn(new ArrayList<>());
        lenient().when(courseScheduleRepository.findByUserId(1L)).thenReturn(new ArrayList<>());
        lenient().when(workoutPlanService.getActivePlan(1L)).thenReturn(null);
        lenient().when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(new ArrayList<>());
    }

    // ------------------------------------------------------------------
    // Test 1 – Task is scheduled AFTER a fixed (pinned) block
    // ------------------------------------------------------------------
    @Test
    void taskScheduledAfterPinnedBlock() {
        LocalDate tomorrow = TODAY.plusDays(1);
        // A 60-minute task
        Task task = makeTask(10L, "Report", 60, 3, tomorrow.plusDays(2).atTime(23, 59));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));

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
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(lowPriority, highPriority));
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
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
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

        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
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
    // Test 4b – Over a long horizon habits land in EVERY week, while tasks
    //           stay inside the (shorter) task horizon
    // ------------------------------------------------------------------
    @Test
    void habitsAreScheduledInEveryWeekOfALongHorizon() {
        LocalDate start    = TODAY.plusDays(1);
        LocalDate rangeEnd = start.plusDays(83);   // 12 Wochen, der Produktions-Horizont

        Habit habit = new Habit();
        habit.setId(52L);
        habit.setName("Laufen");
        habit.setTimesPerWeek(3);
        habit.setDurationMinutes(45);
        habit.setPreferredTime(LocalTime.of(9, 0));
        habit.setStartDate(start.minusDays(30));

        // Ein Task mit Deadline weit hinten darf trotzdem nicht jenseits des Task-Horizonts
        // (14 Tage) landen — sonst wäre der Zuschnitt wirkungslos.
        Task task = makeTask(150L, "Semesterarbeit", 120, 3, rangeEnd.atTime(23, 59));

        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(habit));

        ScheduleResult result = service.generateOptimalSchedule(1L, start, rangeEnd);

        List<ScheduledItem> habitItems = result.getScheduledHabits().stream()
                .filter(i -> i.getType() == ScheduledItemType.HABIT)
                .toList();

        // Jede ISO-Woche des Horizonts, die vollständig drinliegt, muss belegt sein.
        for (LocalDate week = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
             !week.plusDays(6).isAfter(rangeEnd);
             week = week.plusWeeks(1)) {
            final LocalDate weekStart = week;
            long inWeek = habitItems.stream()
                    .map(i -> i.getStartTime().toLocalDate())
                    .filter(d -> !d.isBefore(weekStart) && d.isBefore(weekStart.plusWeeks(1)))
                    .count();
            assertEquals(3, inWeek, "Woche ab " + weekStart + " muss 3 Habit-Blöcke bekommen");
        }

        assertFalse(result.getScheduledTasks().isEmpty(), "Task muss geplant werden");
        LocalDate taskHorizonEnd = start.plusDays(14);
        for (ScheduledItem item : result.getScheduledTasks()) {
            assertFalse(item.getStartTime().toLocalDate().isAfter(taskHorizonEnd),
                    "Task-Block darf nicht hinter den Task-Horizont rutschen");
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

        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
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

        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
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
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        service.generateOptimalSchedule(1L, tomorrow, tomorrow);
        service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        verify(calendarEventService, times(2)).clearScheduledEvents(
                eq(1L), eq(tomorrow.atStartOfDay()), eq(tomorrow.atTime(23, 59, 59)));
        verify(calendarEventRepository, times(2)).save(any(CalendarEvent.class));
    }

    // ==================================================================
    // Regressionen des Reclaim-Umbaus
    // ==================================================================

    // ------------------------------------------------------------------
    // Test 8 – Ein unplatzierbarer Task darf nicht den ganzen Plan mitreißen
    // ------------------------------------------------------------------
    @Test
    void impossibleTaskDoesNotWipeTheRestOfTheSchedule() {
        LocalDate tomorrow = TODAY.plusDays(1);
        // 2000 Minuten passen in keinen 08:00–17:00-Tag, auch nicht in Chunks.
        Task giant = makeTask(80L, "Unmöglich", 2000, 3, tomorrow.atTime(23, 59));
        giant.setSplittable(false);
        Task small = makeTask(81L, "Machbar", 60, 3, tomorrow.atTime(23, 59));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(giant, small));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertEquals(1, result.getScheduledTasks().size(),
                "Der machbare Task muss trotz des unmöglichen geplant werden");
        assertEquals("Machbar", result.getScheduledTasks().get(0).getTask().getTitle());
        assertTrue(result.getAtRisk().stream().anyMatch(a -> "Unmöglich".equals(a.getTitle())),
                "Der unmögliche Task muss als at-risk gemeldet werden");
        // Der Kalender wird trotzdem geschrieben — nicht gelöscht und leer gelassen.
        verify(calendarEventService).clearScheduledEvents(any(), any(), any());
        verify(calendarEventRepository, atLeastOnce()).save(any(CalendarEvent.class));
    }

    // ------------------------------------------------------------------
    // Test 9 – Zwei überlappende gepinnte Termine machen das Modell nicht kaputt
    // ------------------------------------------------------------------
    @Test
    void overlappingPinnedEventsDoNotBreakTheModel() {
        LocalDate tomorrow = TODAY.plusDays(1);
        Task task = makeTask(90L, "Danach", 60, 3, tomorrow.plusDays(1).atTime(23, 59));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        // Ohne das Verschmelzen der Blöcke wäre addNoOverlap hier sofort INFEASIBLE.
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(
                makeFixedEvent(91L, tomorrow.atTime(9, 0), tomorrow.atTime(11, 0)),
                makeFixedEvent(92L, tomorrow.atTime(10, 0), tomorrow.atTime(12, 0))));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertEquals(1, result.getScheduledTasks().size(), "Task muss geplant werden");
        ScheduledItem item = result.getScheduledTasks().get(0);
        // Frei sind 08:00–09:00 und ab 12:00; die verschmolzene Blockade 09:00–12:00 ist tabu.
        boolean before = !item.getEndTime().isAfter(tomorrow.atTime(9, 0));
        boolean after  = !item.getStartTime().isBefore(tomorrow.atTime(12, 0));
        assertTrue(before || after,
                "Task darf nicht in die verschmolzene Blockade 09:00–12:00 fallen, lag aber bei "
                        + item.getStartTime() + "–" + item.getEndTime());
    }

    // ------------------------------------------------------------------
    // Test 10 – Lange Tasks werden in gleich große, geordnete Blöcke zerlegt
    // ------------------------------------------------------------------
    @Test
    void longTaskIsSplitIntoBalancedOrderedChunks() {
        LocalDate tomorrow = TODAY.plusDays(1);
        Task task = makeTask(100L, "Report", 300, 3, tomorrow.plusDays(3).atTime(23, 59));
        task.setMaxChunkMinutes(120);
        task.setMinChunkMinutes(30);
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(3));

        List<ScheduledItem> items = result.getScheduledTasks();
        assertEquals(3, items.size(), "300 Min bei max 120 ergibt 3 Blöcke à 100");
        for (ScheduledItem i : items) {
            long minutes = java.time.temporal.ChronoUnit.MINUTES.between(i.getStartTime(), i.getEndTime());
            assertEquals(100, minutes, "Blöcke müssen gleich groß sein, nicht 120/120/60");
        }
        // Chronologisch geordnet und überlappungsfrei
        for (int i = 1; i < items.size(); i++) {
            assertFalse(items.get(i).getStartTime().isBefore(items.get(i - 1).getEndTime()),
                    "Blöcke desselben Tasks dürfen sich nicht überlappen");
        }
        assertEquals(3, items.get(0).getChunkCount(), "Blöcke werden für den Titel durchnummeriert");
    }

    // ------------------------------------------------------------------
    // Test 11 – Das Tageslimit für Task-Zeit wird eingehalten
    // ------------------------------------------------------------------
    @Test
    void chunksRespectMaxTaskMinutesPerDay() {
        prefs.setMaxTaskMinutesPerDay(120);
        LocalDate tomorrow = TODAY.plusDays(1);
        Task task = makeTask(110L, "Viel", 360, 3, tomorrow.plusDays(5).atTime(23, 59));
        task.setMaxChunkMinutes(120);
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(5));

        java.util.Map<LocalDate, Long> perDay = new java.util.HashMap<>();
        for (ScheduledItem i : result.getScheduledTasks()) {
            perDay.merge(i.getStartTime().toLocalDate(),
                    java.time.temporal.ChronoUnit.MINUTES.between(i.getStartTime(), i.getEndTime()), Long::sum);
        }
        assertFalse(perDay.isEmpty(), "Es muss etwas geplant worden sein");
        perDay.forEach((day, minutes) ->
                assertTrue(minutes <= 120, "Tag " + day + " hat " + minutes + " Min statt max. 120"));
    }

    // ------------------------------------------------------------------
    // maxChunksPerDay — Bloecke ueber mehrere Tage verteilen
    //
    // Zerlegt wurde schon vorher richtig, aber nichts hielt die Bloecke auseinander: die
    // Symmetriebrechung erlaubt prev.end == cur.start, und der Dringlichkeitsterm belohnt
    // ausschliesslich "frueher ist besser". Vier Bloecke landeten deshalb hintereinander am
    // Anfang des ersten freien Tages — im Kalender ein einziger langer Balken.
    // ------------------------------------------------------------------

    /** Wie viele Bloecke je Kalendertag geplant wurden. */
    private java.util.Map<LocalDate, Integer> blocksPerDay(ScheduleResult result) {
        java.util.Map<LocalDate, Integer> perDay = new java.util.HashMap<>();
        for (ScheduledItem i : result.getScheduledTasks()) {
            perDay.merge(i.getStartTime().toLocalDate(), 1, Integer::sum);
        }
        return perDay;
    }

    @Test
    void chunksSpreadAcrossDaysWhenMaxChunksPerDayIsSet() {
        LocalDate tomorrow = TODAY.plusDays(1);
        Task task = makeTask(115L, "Lernen: Analysis I", 360, 3, tomorrow.plusDays(6).atTime(23, 59));
        task.setMaxChunkMinutes(90);
        task.setMaxChunksPerDay(2);
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(6));

        assertEquals(4, result.getScheduledTasks().size(), "360 Minuten zu je 90 sind vier Bloecke");
        java.util.Map<LocalDate, Integer> perDay = blocksPerDay(result);
        perDay.forEach((day, count) ->
                assertTrue(count <= 2, "Tag " + day + " hat " + count + " Bloecke statt hoechstens 2"));
        assertTrue(perDay.size() >= 2,
                "vier Bloecke bei zwei pro Tag muessen auf mindestens zwei Tage fallen, waren aber "
                        + perDay.size());
    }

    @Test
    void einErledigterBlockBlocktSeineZeitZaehltAberNichtAlsGepinnt() {
        // Die Buchhaltungsfalle: pinnedMinutesPerTask summiert fixedEvents + frozenEvents.
        // Zaehlte ein erledigter Block dort mit, waeren seine 90 Minuten zweimal abgezogen —
        // einmal hier und einmal ueber die Gutschrift, die estimatedDurationMinutes neu setzt.
        LocalDate tomorrow = TODAY.plusDays(1);
        Task task = makeTask(117L, "Lernen", 360, 3, tomorrow.plusDays(6).atTime(23, 59));
        task.setMaxChunkMinutes(90);

        CalendarEvent erledigt = new CalendarEvent();
        erledigt.setId(500L);
        erledigt.setEventType(EventType.TASK);
        erledigt.setIsFixed(false);
        erledigt.setRelatedTask(task);
        erledigt.setStartTime(tomorrow.atTime(9, 0));
        erledigt.setEndTime(tomorrow.atTime(10, 30));
        erledigt.setCompletedAt(LocalDateTime.now());

        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), any(), eq(false), any(), any())).thenReturn(List.of(erledigt));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(6));

        assertEquals(4, result.getScheduledTasks().size(),
                "die vollen 360 Minuten bleiben zu verplanen — der erledigte Block darf sie nicht kuerzen");
        for (ScheduledItem i : result.getScheduledTasks()) {
            assertFalse(i.getStartTime().isBefore(erledigt.getEndTime())
                            && i.getEndTime().isAfter(erledigt.getStartTime()),
                    "kein neuer Block darf ueber dem erledigten liegen, er sperrt seine Zeit weiter");
        }
    }

    @Test
    void maxChunksPerDayNullBleibtUnbegrenzt() {
        // Rueckwaertskompatibilitaet: Bestandstasks haben NULL in der Spalte und duerfen sich
        // weiterhin an einem Tag stapeln.
        LocalDate tomorrow = TODAY.plusDays(1);
        Task task = makeTask(116L, "Ohne Grenze", 360, 3, tomorrow.plusDays(6).atTime(23, 59));
        task.setMaxChunkMinutes(90);
        task.setMaxChunksPerDay(null);
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(6));

        assertEquals(4, result.getScheduledTasks().size());
        assertEquals(1, blocksPerDay(result).size(),
                "ohne Grenze zieht die Dringlichkeit alle Bloecke auf denselben Tag");
    }

    // ------------------------------------------------------------------
    // Test 12 – Ein Task mit bereits gepinntem Block plant nur den Rest
    // ------------------------------------------------------------------
    @Test
    void taskWithPinnedChunkOnlySchedulesTheRemainder() {
        LocalDate tomorrow = TODAY.plusDays(1);
        Task task = makeTask(120L, "Teilweise gepinnt", 180, 3, tomorrow.plusDays(2).atTime(23, 59));
        task.setMaxChunkMinutes(120);
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));

        // 60 Minuten sind vom Nutzer bereits festgenagelt.
        CalendarEvent pinned = makeFixedEvent(121L, tomorrow.atTime(8, 0), tomorrow.atTime(9, 0));
        pinned.setEventType(EventType.TASK);
        pinned.setRelatedTask(task);
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(pinned));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(2));

        long planned = result.getScheduledTasks().stream()
                .mapToLong(i -> java.time.temporal.ChronoUnit.MINUTES.between(i.getStartTime(), i.getEndTime()))
                .sum();
        assertEquals(120, planned, "180 Min minus 60 gepinnte Minuten = 120 Min Restarbeit");
    }

    // ------------------------------------------------------------------
    // Test 13 – Verworfene Tasks verlieren ihre veralteten Zeiten
    // ------------------------------------------------------------------
    @Test
    void droppedTaskGetsItsStaleScheduleCleared() {
        LocalDate tomorrow = TODAY.plusDays(1);
        Task giant = makeTask(130L, "Passt nicht", 2000, 3, tomorrow.atTime(23, 59));
        giant.setSplittable(false);
        // Rest aus einem früheren Lauf, der jetzt nicht mehr gilt.
        giant.setScheduledStartTime(tomorrow.atTime(9, 0));
        giant.setScheduledEndTime(tomorrow.atTime(10, 0));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(giant));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        verify(taskService).clearSchedule(130L);
    }

    // ------------------------------------------------------------------
    // Test 14 – Puffer hält Tasks von Terminen fern
    // ------------------------------------------------------------------
    @Test
    void bufferMinutesKeepsTasksAwayFromMeetings() {
        prefs.setBufferMinutes(15);
        LocalDate tomorrow = TODAY.plusDays(1);
        Task task = makeTask(140L, "Nach dem Meeting", 60, 3, tomorrow.plusDays(1).atTime(23, 59));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any()))
                .thenReturn(List.of(makeFixedEvent(141L, tomorrow.atTime(8, 0), tomorrow.atTime(10, 0))));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertEquals(1, result.getScheduledTasks().size());
        assertEquals(tomorrow.atTime(10, 15), result.getScheduledTasks().get(0).getStartTime(),
                "15 Min Puffer nach dem Meeting");
    }

    // ------------------------------------------------------------------
    // Test 15 – Ohne Lösung bleibt der bestehende Plan unangetastet
    // ------------------------------------------------------------------
    @Test
    void noSolutionLeavesTheExistingScheduleUntouched() {
        // Praktisch kein Zeitbudget -> der Solver findet in Phase 1 nichts.
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "solverTimeLimitSeconds", 0.0000001);

        LocalDate tomorrow = TODAY.plusDays(1);
        List<Task> many = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            many.add(makeTask(200L + i, "T" + i, 55, 3, tomorrow.plusDays(13).atTime(23, 59)));
        }
        when(taskService.getSchedulableTasks(1L)).thenReturn(many);
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(13));

        if ("UNKNOWN".equals(result.getSolverStatus())) {
            verify(calendarEventService, never()).clearScheduledEvents(any(), any(), any());
            verify(calendarEventRepository, never()).save(any(CalendarEvent.class));
            assertNotNull(result.getMessage(), "Der Nutzer muss erfahren, dass nichts neu berechnet wurde");
        }
        // Findet der Solver trotz Mikro-Budget eine Lösung, ist das ebenfalls in Ordnung —
        // der Test darf dann nur nichts über das Nicht-Löschen behaupten.
    }

    // ------------------------------------------------------------------
    // Test 16 – Flexible Habit sucht sich ihre Tage selbst, max. einmal pro Tag
    // ------------------------------------------------------------------
    @Test
    void flexibleHabitPicksItsOwnDaysAtMostOncePerDay() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        Habit habit = new Habit();
        habit.setId(300L);
        habit.setName("Laufen");
        habit.setDurationMinutes(45);
        habit.setPriority(3);
        habit.setTimesPerWeek(3);                       // 3x pro Woche
        habit.setIdealWindow(HabitWindow.MORNING);      // 06:00–12:00
        habit.setStartDate(monday);
        // Bewusst KEINE Wochentag-Flags: der Solver soll die Tage wählen.

        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(habit));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<ScheduledItem> habits = result.getScheduledHabits();
        assertEquals(3, habits.size(), "3x pro Woche muss 3 Ausführungen ergeben");

        List<LocalDate> days = habits.stream().map(i -> i.getStartTime().toLocalDate()).toList();
        assertEquals(3, new java.util.HashSet<>(days).size(), "höchstens eine Ausführung pro Tag");

        for (ScheduledItem i : habits) {
            LocalTime t = i.getStartTime().toLocalTime();
            assertFalse(t.isBefore(LocalTime.of(8, 0)),
                    "Arbeitszeit beginnt 08:00, davor darf nichts liegen");
            assertTrue(t.isBefore(LocalTime.of(12, 0)),
                    "MORNING-Fenster endet 12:00, lag aber bei " + t);
        }
    }

    // ==================================================================
    // Drag-and-Drop: manuell verschobene Blöcke müssen die Neuplanung überleben
    // ==================================================================

    // ------------------------------------------------------------------
    // Test 17 – Gepinnte Habit-Ausführung belegt ihren Tag und die Wochenquote
    // ------------------------------------------------------------------
    @Test
    void pinnedHabitEventConsumesItsDayAndWeeklyQuota() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        Habit habit = new Habit();
        habit.setId(400L);
        habit.setName("Laufen");
        habit.setDurationMinutes(45);
        habit.setPriority(3);
        habit.setTimesPerWeek(3);
        habit.setIdealWindow(HabitWindow.ANYTIME);
        habit.setStartDate(monday);

        // Der Nutzer hat eine Ausführung auf Mittwoch 16:00 gezogen — das Frontend
        // schickt sie gepinnt zurück, also taucht sie unter den fixen Events auf.
        LocalDate wednesday = monday.plusDays(2);
        CalendarEvent pinned = makeFixedEvent(401L, wednesday.atTime(16, 0), wednesday.atTime(16, 45));
        pinned.setEventType(EventType.HABIT);
        pinned.setRelatedHabit(habit);

        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(habit));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(pinned));

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<ScheduledItem> habits = result.getScheduledHabits();
        assertEquals(2, habits.size(),
                "3x pro Woche minus die eine gepinnte Ausführung ergibt 2 neu zu planende");

        List<LocalDate> days = habits.stream().map(i -> i.getStartTime().toLocalDate()).toList();
        assertFalse(days.contains(wednesday),
                "am gepinnten Tag darf keine zweite Ausführung derselben Habit entstehen");
    }

    // ------------------------------------------------------------------
    // Test 18 – Gepinnte Ausführung einer Wochentag-Habit (Legacy-Modus)
    // ------------------------------------------------------------------
    @Test
    void pinnedLegacyHabitDayIsNotScheduledAgain() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        Habit habit = new Habit();
        habit.setId(410L);
        habit.setName("Lesen");
        habit.setDurationMinutes(30);
        habit.setPriority(3);
        habit.setMonday(true);
        habit.setTuesday(true);
        habit.setStartDate(monday);
        // timesPerWeek bleibt null -> Legacy-Modus, eine Ausführung je gesetztem Wochentag.

        CalendarEvent pinned = makeFixedEvent(411L, monday.atTime(20, 0), monday.atTime(20, 30));
        pinned.setEventType(EventType.HABIT);
        pinned.setRelatedHabit(habit);

        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(habit));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(pinned));

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(1));

        List<LocalDate> days = result.getScheduledHabits().stream()
                .map(i -> i.getStartTime().toLocalDate()).toList();
        assertEquals(List.of(monday.plusDays(1)), days,
                "nur der Dienstag ist noch offen; der Montag hängt bereits gepinnt im Kalender");
    }

    // ------------------------------------------------------------------
    // Test 19 – Ein gepinntes Workout wird nicht mehr umgeplant
    // ------------------------------------------------------------------
    @Test
    void pinnedWorkoutStaysWhereTheUserDroppedIt() {
        LocalDate tomorrow = TODAY.plusDays(1);

        WorkoutSession workout = new WorkoutSession();
        workout.setId(500L);
        workout.setName("Push Day");
        workout.setDurationMinutes(60);
        workout.setIsFlexible(true);
        workout.setTargetWeekStart(tomorrow.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        workout.setStartTime(tomorrow.atTime(15, 0));
        workout.setEndTime(tomorrow.atTime(16, 0));

        CalendarEvent pinned = makeFixedEvent(501L, tomorrow.atTime(15, 0), tomorrow.atTime(16, 0));
        pinned.setEventType(EventType.WORKOUT);
        pinned.setRelatedWorkout(workout);

        when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(1L)).thenReturn(List.of(workout));
        when(workoutSessionRepository.findByUserIdAndStartTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(workout));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(pinned));
        // Ein Task, damit das Modell überhaupt etwas zu platzieren hat.
        Task task = makeTask(510L, "Report", 60, 3, tomorrow.plusDays(2).atTime(23, 59));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertTrue(result.getScheduledHabits().stream()
                        .noneMatch(i -> i.getType() == ScheduledItemType.WORKOUT),
                "ein gepinntes Workout darf nicht erneut platziert werden");
        // Die Session behält ihre Zeiten — der Solver darf sie nicht überschreiben.
        assertEquals(tomorrow.atTime(15, 0), workout.getStartTime());
        verify(workoutSessionRepository, never()).save(any(WorkoutSession.class));

        // Und der Block wirkt weiterhin als belegte Zeit.
        ScheduledItem scheduled = result.getScheduledTasks().get(0);
        assertTrue(scheduled.getEndTime().isBefore(tomorrow.atTime(15, 0).plusSeconds(1))
                        || !scheduled.getStartTime().isBefore(tomorrow.atTime(16, 0)),
                "der Task darf nicht in das gepinnte Workout hineinlaufen");
    }

    // ==================================================================
    // Drag-and-Drop: der verschobene Block darf keinen Ersatz am alten Platz bekommen
    //
    // Zwei Löcher in der Buchhaltung erzeugten früher genau das Bild, über das der Nutzer
    // gestolpert ist: der gezogene Block bleibt liegen UND ein zweiter taucht am Ursprung auf.
    //   1. Wochentags-Gewohnheiten verbuchten nur ihren tatsächlichen, nicht ihren Ursprungstag.
    //   2. Alles, was über den Planungshorizont hinaus gezogen wurde, fiel aus der Abfrage und
    //      galt damit als ungeplant.
    // ==================================================================

    /**
     * Der Fall aus 1: Montagsblock auf Mittwoch gezogen. {@code targetDate} hält fest, dass die
     * Montags-Ausführung bereits versorgt ist — sonst ist der Montag wieder frei und immer noch
     * per Wochentags-Flag fällig.
     */
    @Test
    void verschobeneWochentagsGewohnheitBekommtKeinenErsatzAmUrsprungstag() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate wednesday = monday.plusDays(2);

        Habit habit = new Habit();
        habit.setId(420L);
        habit.setName("Lesen");
        habit.setDurationMinutes(30);
        habit.setPriority(3);
        habit.setMonday(true);
        habit.setStartDate(monday);
        // timesPerWeek bleibt null -> Legacy-Modus, eine Ausführung je gesetztem Wochentag.

        // Der Block liegt am Mittwoch, ist aber auf den Montag gebucht — genau das, was
        // updateEvent nach einem Drag-and-Drop hinterlässt.
        CalendarEvent verschoben = makeFixedEvent(421L, wednesday.atTime(20, 0), wednesday.atTime(20, 30));
        verschoben.setEventType(EventType.HABIT);
        verschoben.setRelatedHabit(habit);
        verschoben.setTargetDate(monday);

        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(habit));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(verschoben));

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        assertTrue(result.getScheduledHabits().isEmpty(),
                "die Montags-Ausführung hängt bereits (am Mittwoch) im Kalender — "
                        + "am Montag darf kein zweiter Block entstehen");
    }

    /** Der Fall aus 2 für Tasks: gepinnter Block weit jenseits des Horizonts. */
    @Test
    void gepinnterTaskBlockJenseitsDesHorizontsWirdWeiterGutgeschrieben() {
        LocalDate tomorrow = TODAY.plusDays(1);
        LocalDate weitDraussen = TODAY.plusDays(60);

        Task task = makeTask(600L, "Bericht", 120, 3, TODAY.plusDays(90).atTime(23, 59));

        CalendarEvent verschoben = makeFixedEvent(601L,
                weitDraussen.atTime(9, 0), weitDraussen.atTime(11, 0));
        verschoben.setEventType(EventType.TASK);
        verschoben.setRelatedTask(task);

        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        // Ausserhalb des Planungsfensters: die Fenster-Abfrage sieht den Block nicht mehr,
        // nur die unbeschnittene Buchhaltungs-Abfrage.
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(calendarEventService.getPinnedScheduledEventsFrom(eq(1L), any()))
                .thenReturn(List.of(verschoben));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(6));

        assertTrue(result.getScheduledTasks().isEmpty(),
                "die vollen 120 Minuten liegen bereits fest — es bleibt nichts zu planen");
    }

    /** Der Fall aus 2 für Workouts. */
    @Test
    void gepinntesWorkoutJenseitsDesHorizontsWirdNichtErneutGeplant() {
        LocalDate tomorrow = TODAY.plusDays(1);
        LocalDate weitDraussen = TODAY.plusDays(60);

        WorkoutSession workout = makeFlexibleWorkout(610L, "Push", 60,
                tomorrow.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        workout.setStartTime(weitDraussen.atTime(15, 0));
        workout.setEndTime(weitDraussen.atTime(16, 0));

        CalendarEvent verschoben = makeFixedEvent(611L,
                weitDraussen.atTime(15, 0), weitDraussen.atTime(16, 0));
        verschoben.setEventType(EventType.WORKOUT);
        verschoben.setRelatedWorkout(workout);

        when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(1L)).thenReturn(List.of(workout));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(calendarEventService.getPinnedScheduledEventsFrom(eq(1L), any()))
                .thenReturn(List.of(verschoben));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(6));

        assertTrue(result.getScheduledHabits().stream()
                        .noneMatch(i -> i.getType() == ScheduledItemType.WORKOUT),
                "das Workout hängt schon gepinnt im Kalender, nur eben weit draussen");
        verify(workoutSessionRepository, never()).save(any(WorkoutSession.class));
    }

    /**
     * Der Fall aus 2 für flexible Gewohnheiten: der Block liegt jenseits des Horizonts, bleibt
     * über {@code targetWeekStart} aber auf die Woche gebucht, aus der er stammt.
     */
    @Test
    void gepinnteFlexibleGewohnheitJenseitsDesHorizontsKuerztIhreWochenquote() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate weitDraussen = monday.plusDays(60);

        Habit habit = new Habit();
        habit.setId(620L);
        habit.setName("Laufen");
        habit.setDurationMinutes(45);
        habit.setPriority(3);
        habit.setTimesPerWeek(3);
        habit.setIdealWindow(HabitWindow.ANYTIME);
        habit.setStartDate(monday);

        CalendarEvent verschoben = makeFixedEvent(621L,
                weitDraussen.atTime(16, 0), weitDraussen.atTime(16, 45));
        verschoben.setEventType(EventType.HABIT);
        verschoben.setRelatedHabit(habit);
        verschoben.setTargetWeekStart(monday);

        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(habit));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(calendarEventService.getPinnedScheduledEventsFrom(eq(1L), any()))
                .thenReturn(List.of(verschoben));

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        assertEquals(2, result.getScheduledHabits().size(),
                "3x pro Woche minus die eine weit verschobene Ausführung ergibt 2");
    }

    // ==================================================================
    // Wunschzeiten der Gewohnheiten
    // ==================================================================

    /**
     * Der Kern der Beschwerde "eine Gewohnheit klebt direkt hinter der nächsten".
     *
     * Ohne gesetztes Wunschfenster ist die Abweichungsstrafe über den ganzen Arbeitstag exakt 0.
     * Bei so flachem Ziel behielt CP-SAT die Phase-1-Lösung, und die sitzt auf der unteren
     * Schranke der Startvariablen — also landeten alle Gewohnheiten ab Arbeitsbeginn direkt
     * hintereinander.
     */
    @Test
    void zweiGewohnheitenOhneWunschzeitKlebenNichtAneinander() {
        LocalDate tomorrow = TODAY.plusDays(1);

        Habit meditation = makeDailyHabit(1401L, "Meditation", 30);
        Habit lesen      = makeDailyHabit(1402L, "Lesen", 30);

        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any()))
                .thenReturn(List.of(meditation, lesen));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        List<LocalDateTime> starts = result.getScheduledHabits().stream()
                .map(ScheduledItem::getStartTime)
                .sorted()
                .toList();

        assertEquals(2, starts.size(), "beide Gewohnheiten sollen geplant werden");
        long gapMinutes = ChronoUnit.MINUTES.between(starts.get(0), starts.get(1));
        assertTrue(gapMinutes >= 120,
                "zwei Gewohnheiten ohne Wunschzeit sollen über den Tag verteilt werden, lagen aber "
                        + gapMinutes + " Minuten auseinander: " + starts);
    }

    /**
     * Der hergeleitete Anker ist bewusst schwach: er soll nur das flache Ziel aufbrechen. Eine
     * Gewohnheit mit ausdrücklicher Wunschzeit muss weiterhin genau dort landen.
     */
    @Test
    void ausdrueckicheWunschzeitSchlaegtDenHergeleitetenAnker() {
        LocalDate tomorrow = TODAY.plusDays(1);

        Habit fix = makeDailyHabit(1411L, "Mittagsspaziergang", 30);
        fix.setPreferredTime(LocalTime.of(12, 0));
        Habit frei = makeDailyHabit(1412L, "Lesen", 30);

        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any()))
                .thenReturn(List.of(fix, frei));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        LocalDateTime start = result.getScheduledHabits().stream()
                .filter(i -> i.getHabit() != null && i.getHabit().getId().equals(1411L))
                .map(ScheduledItem::getStartTime)
                .findFirst()
                .orElseThrow();

        assertEquals(LocalTime.of(12, 0), start.toLocalTime(),
                "die gesetzte Wunschzeit muss den hergeleiteten Anker überstimmen");
    }

    /** Gleicher Bestand, gleiche Anker — sonst wäre der Stabilitätsterm wertlos. */
    @Test
    void hergeleiteteAnkerSindUeberLaeufeStabil() {
        LocalDate tomorrow = TODAY.plusDays(1);

        Habit a = makeDailyHabit(1421L, "A", 30);
        Habit b = makeDailyHabit(1422L, "B", 30);

        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(a, b));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        List<LocalDateTime> first = service.generateOptimalSchedule(1L, tomorrow, tomorrow)
                .getScheduledHabits().stream().map(ScheduledItem::getStartTime).sorted().toList();
        List<LocalDateTime> second = service.generateOptimalSchedule(1L, tomorrow, tomorrow)
                .getScheduledHabits().stream().map(ScheduledItem::getStartTime).sorted().toList();

        assertEquals(first, second, "zwei Läufe ohne Änderung müssen dieselben Zeiten ergeben");
    }

    /**
     * Dasselbe wie beim Projektblock, nur für eine Gewohnheit mit Wochenquote: wird eine
     * Ausführung in die Folgewoche gezogen, darf die verlassene Woche keinen Ersatz bekommen.
     */
    @Test
    void eineInDieFolgewocheGezogeneGewohnheitErzeugtKeinenErsatzInDerAltenWoche() {
        LocalDate weekA = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate weekB = weekA.plusWeeks(1);

        Habit run = new Habit();
        run.setId(1450L);
        run.setName("Laufen gehen");
        run.setDurationMinutes(45);
        run.setPriority(3);
        run.setTimesPerWeek(2);
        run.setIdealWindow(HabitWindow.ANYTIME);
        run.setStartDate(weekA);

        // Gehört zum Pensum von Woche A, liegt nach dem Verschieben in Woche B.
        LocalDate droppedOn = weekB.plusDays(1);
        CalendarEvent moved = makeFixedEvent(1451L, droppedOn.atTime(17, 0), droppedOn.atTime(17, 45));
        moved.setEventType(EventType.HABIT);
        moved.setRelatedHabit(run);
        moved.setTargetWeekStart(weekA);

        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(run));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(moved));

        ScheduleResult result = service.generateOptimalSchedule(1L, weekA, weekB.plusDays(6));

        List<LocalDate> days = result.getScheduledHabits().stream()
                .filter(i -> i.getType() == ScheduledItemType.HABIT)
                .map(i -> i.getStartTime().toLocalDate())
                .sorted()
                .toList();

        long inWeekA = days.stream().filter(d -> d.isBefore(weekB)).count();
        long inWeekB = days.stream().filter(d -> !d.isBefore(weekB)).count();

        assertEquals(1, inWeekA,
                "Woche A ist durch die verschobene Ausführung halb gedeckt: " + days);
        assertEquals(2, inWeekB, "Woche B behält ihr volles Pensum: " + days);
        assertFalse(days.contains(droppedOn),
                "am Tag der verschobenen Ausführung darf keine zweite entstehen: " + days);
    }

    /** Tägliche Gewohnheit ohne Wunschfenster — alle Wochentage gesetzt, keine Zeitvorgabe. */
    private Habit makeDailyHabit(Long id, String name, int minutes) {
        Habit h = new Habit();
        h.setId(id);
        h.setName(name);
        h.setDurationMinutes(minutes);
        h.setPriority(3);
        h.setFrequency(HabitFrequency.DAILY);
        h.setMonday(true);   h.setTuesday(true); h.setWednesday(true); h.setThursday(true);
        h.setFriday(true);   h.setSaturday(true); h.setSunday(true);
        return h;
    }

    // ==================================================================
    // Verteilung der Trainingseinheiten über die Woche
    // ==================================================================

    /**
     * Der Kern der Beschwerde "zwei Trainings an einem Tag". Flexible Workouts waren die einzige
     * Gruppe ohne Wochengruppen-Constraint und hatten zugleich das stärkste Dringlichkeitsgewicht
     * im Modell — sie wurden also aktiv an den Wochenanfang gezogen und stapelten sich dort.
     */
    @Test
    void dreiWorkoutsLandenAnDreiVerschiedenenTagen() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        List<WorkoutSession> plan = List.of(
                makeFlexibleWorkout(701L, "Push", 60, monday),
                makeFlexibleWorkout(702L, "Pull", 60, monday),
                makeFlexibleWorkout(703L, "Legs", 60, monday));

        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(1L)).thenReturn(plan);

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<LocalDate> days = workoutDays(result);
        assertEquals(3, days.size(), "alle drei Einheiten sollen geplant werden");
        assertEquals(3, Set.copyOf(days).size(), "keine zwei Trainings am selben Tag: " + days);
    }

    /** Zwischen zwei Einheiten soll möglichst ein voller Ruhetag liegen. */
    @Test
    void workoutsHaltenEinenRuhetagAbstand() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        List<WorkoutSession> plan = List.of(
                makeFlexibleWorkout(711L, "Push", 60, monday),
                makeFlexibleWorkout(712L, "Pull", 60, monday),
                makeFlexibleWorkout(713L, "Legs", 60, monday));

        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(1L)).thenReturn(plan);

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<LocalDate> days = workoutDays(result);
        assertEquals(3, days.size());
        for (int i = 1; i < days.size(); i++) {
            long gap = ChronoUnit.DAYS.between(days.get(i - 1), days.get(i));
            assertTrue(gap >= 2,
                    "zwischen zwei Trainings soll ein Ruhetag liegen, war " + gap + " Tag(e): " + days);
        }
    }

    /**
     * Die Ruhetag-Regel ist weich: bei fünf Einheiten in sieben Tagen ist sie nicht mehr für jedes
     * Paar erfüllbar. Das darf weder das Modell sprengen noch Einheiten kosten.
     */
    @Test
    void fuenfWorkoutsProWocheBleibenLoesbar() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        List<WorkoutSession> plan = List.of(
                makeFlexibleWorkout(721L, "A", 45, monday),
                makeFlexibleWorkout(722L, "B", 45, monday),
                makeFlexibleWorkout(723L, "C", 45, monday),
                makeFlexibleWorkout(724L, "D", 45, monday),
                makeFlexibleWorkout(725L, "E", 45, monday));

        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(1L)).thenReturn(plan);

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<LocalDate> days = workoutDays(result);
        assertEquals(5, days.size(), "keine Einheit darf an der weichen Regel scheitern");
        assertEquals(5, Set.copyOf(days).size(), "die harte Tagesregel gilt weiterhin: " + days);
    }

    /**
     * Verschiebt der Nutzer eine Einheit per Drag-and-Drop, ist deren Tag verbraucht. Vorher hat
     * der Solver am Abend desselben Tages eine zweite Einheit angelegt — die Zeit war ja frei.
     */
    @Test
    void gepinntesWorkoutBlockiertSeinenTagFuerDieAnderen() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        WorkoutSession pinnedSession = makeFlexibleWorkout(731L, "Push", 60, monday);
        pinnedSession.setStartTime(monday.atTime(9, 0));
        pinnedSession.setEndTime(monday.atTime(10, 0));

        CalendarEvent pinned = makeFixedEvent(732L, monday.atTime(9, 0), monday.atTime(10, 0));
        pinned.setEventType(EventType.WORKOUT);
        pinned.setRelatedWorkout(pinnedSession);

        List<WorkoutSession> plan = List.of(
                pinnedSession,
                makeFlexibleWorkout(733L, "Pull", 60, monday),
                makeFlexibleWorkout(734L, "Legs", 60, monday));

        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(pinned));
        when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(1L)).thenReturn(plan);
        when(workoutSessionRepository.findByUserIdAndStartTimeBetween(eq(1L), any(), any()))
                .thenReturn(List.of(pinnedSession));

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<LocalDate> days = workoutDays(result);
        assertEquals(2, days.size(), "die gepinnte Einheit wird nicht erneut geplant");
        assertFalse(days.contains(monday),
                "der Tag der gepinnten Einheit ist verbraucht, war aber belegt mit: " + days);
    }

    /**
     * Der Ruhetag ist hart, sobald die erlaubten Tage ihn hergeben — sonst weich. Hier passen drei
     * Einheiten mit Ruhetag nicht in vier Tage, es darf aber trotzdem keine ausfallen.
     */
    @Test
    void dreiWorkoutsInVierTagenWerdenAlleGeplant() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        List<WorkoutSession> plan = List.of(
                makeFlexibleWorkout(741L, "A", 45, monday),
                makeFlexibleWorkout(742L, "B", 45, monday),
                makeFlexibleWorkout(743L, "C", 45, monday));

        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(1L)).thenReturn(plan);

        // Horizont endet Donnerstag: nur Mo–Do erlaubt, also vier Tage für drei Einheiten.
        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(3));

        List<LocalDate> days = workoutDays(result);
        assertEquals(3, days.size(), "die weiche Rückfallregel darf keine Einheit kosten: " + days);
        assertEquals(3, Set.copyOf(days).size(), "ein Training pro Tag bleibt hart: " + days);
    }

    /**
     * Schutz vor einem Ausreißer in der Laufzeit. Die Schranke ist bewusst großzügig — geprüft
     * wird nicht die exakte Dauer, sondern dass ein realistischer Bestand nicht in eine
     * Größenordnung rutscht, die man nach einem Drag-and-Drop spürt.
     */
    @Test
    void einRealistischerBestandBleibtImZeitbudget() {
        LocalDate tomorrow = TODAY.plusDays(1);

        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Task t = makeTask(2000L + i, "Aufgabe " + i, 60 + (i % 4) * 30, 1 + (i % 5),
                    tomorrow.plusDays(2 + (i % 10)).atTime(20, 0));
            tasks.add(t);
        }

        List<Habit> habits = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            habits.add(makeDailyHabit(2100L + i, "Gewohnheit " + i, 15 + i * 5));
        }

        LocalDate weekStart = tomorrow.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<WorkoutSession> workouts = List.of(
                makeFlexibleWorkout(2200L, "Push", 60, weekStart),
                makeFlexibleWorkout(2201L, "Pull", 60, weekStart),
                makeFlexibleWorkout(2202L, "Legs", 60, weekStart));

        when(taskService.getSchedulableTasks(1L)).thenReturn(tasks);
        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(habits);
        when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(1L)).thenReturn(workouts);
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        long startedAt = System.nanoTime();
        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(14));
        long millis = (System.nanoTime() - startedAt) / 1_000_000;

        assertNotNull(result.getSolverStatus());
        assertFalse(result.getScheduledTasks().isEmpty(), "der Bestand muss planbar sein");
        assertTrue(millis < 15_000,
                "ein realistischer Bestand soll deutlich im Zeitbudget bleiben, brauchte aber "
                        + millis + " ms");
    }

    // ==================================================================
    // Überspringen einer Ausführung
    // ==================================================================

    /**
     * Eine übersprungene Projekt-Session zählt weiter auf das Wochenpensum.
     *
     * Ohne das wäre Überspringen genauso wirkungslos wie Löschen: die Woche stünde unter Pensum
     * und bekäme innerhalb von Sekunden Ersatz.
     */
    @Test
    void uebersprungeneProjektSessionZaehltWeiterAufsWochenpensum() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Project project = makeProject(580L, "Everything App", 2, 60);
        project.setStartDate(monday);

        LocalDate skippedOn = monday.plusDays(1);
        CalendarEvent skipped = makeSkippedEvent(581L, skippedOn.atTime(10, 0), skippedOn.atTime(11, 0));
        skipped.setEventType(EventType.PROJECT);
        skipped.setRelatedProject(project);
        skipped.setTargetWeekStart(monday);

        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(List.of(project));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any())).thenReturn(List.of(skipped));

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<LocalDate> days = projectItems(result).stream()
                .map(i -> i.getStartTime().toLocalDate()).sorted().toList();

        assertEquals(1, days.size(),
                "2 pro Woche minus die übersprungene ergibt genau eine neue Session: " + days);
        assertFalse(days.contains(skippedOn),
                "am übersprungenen Tag darf kein Ersatz entstehen: " + days);
    }

    /** Dasselbe für eine Gewohnheit mit Wochenquote. */
    @Test
    void uebersprungeneGewohnheitZaehltWeiterAufsWochenpensum() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        Habit run = new Habit();
        run.setId(1470L);
        run.setName("Laufen gehen");
        run.setDurationMinutes(45);
        run.setPriority(3);
        run.setTimesPerWeek(3);
        run.setIdealWindow(HabitWindow.ANYTIME);
        run.setStartDate(monday);

        LocalDate skippedOn = monday.plusDays(1);
        CalendarEvent skipped = makeSkippedEvent(1471L, skippedOn.atTime(17, 0), skippedOn.atTime(17, 45));
        skipped.setEventType(EventType.HABIT);
        skipped.setRelatedHabit(run);
        skipped.setTargetWeekStart(monday);

        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(run));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any())).thenReturn(List.of(skipped));

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<LocalDate> days = result.getScheduledHabits().stream()
                .filter(i -> i.getType() == ScheduledItemType.HABIT)
                .map(i -> i.getStartTime().toLocalDate()).sorted().toList();

        assertEquals(2, days.size(),
                "3 pro Woche minus die übersprungene ergibt zwei neue Ausführungen: " + days);
        assertFalse(days.contains(skippedOn),
                "am übersprungenen Tag darf kein Ersatz entstehen: " + days);
    }

    /** Eine übersprungene Trainingseinheit wird nicht neu platziert. */
    @Test
    void uebersprungenesWorkoutWirdNichtNeuGeplant() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        WorkoutSession skippedSession = makeFlexibleWorkout(751L, "Push", 60, monday);
        skippedSession.setIsSkipped(true);
        WorkoutSession open = makeFlexibleWorkout(752L, "Pull", 60, monday);

        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(1L))
                .thenReturn(List.of(skippedSession, open));

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<ScheduledItem> workouts = result.getScheduledHabits().stream()
                .filter(i -> i.getType() == ScheduledItemType.WORKOUT).toList();

        assertEquals(1, workouts.size(), "nur die nicht übersprungene Einheit wird geplant");
        assertEquals(752L, workouts.get(0).getWorkoutSession().getId());
    }

    /** Übersprungene Blöcke geben ihre Zeit frei — sie dürfen nichts mehr blockieren. */
    @Test
    void uebersprungenerBlockSperrtKeineZeitMehr() {
        LocalDate tomorrow = TODAY.plusDays(1);

        Habit habit = makeDailyHabit(1480L, "Meditation", 30);
        // Gepinnt UND übersprungen: früher hätte der gepinnte Block seine Zeit weiter gesperrt.
        CalendarEvent skipped = makeSkippedEvent(1481L, tomorrow.atTime(9, 0), tomorrow.atTime(12, 0));
        skipped.setEventType(EventType.HABIT);
        skipped.setRelatedHabit(habit);
        skipped.setIsFixed(true);

        Task task = makeTask(1482L, "Bericht", 120, 5, tomorrow.atTime(23, 59));

        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(skipped));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertFalse(result.getScheduledTasks().isEmpty(), "der Task muss planbar sein");
        boolean usesFreedTime = result.getScheduledTasks().stream()
                .anyMatch(i -> i.getStartTime().isBefore(tomorrow.atTime(12, 0))
                        && i.getEndTime().isAfter(tomorrow.atTime(9, 0)));
        assertTrue(usesFreedTime,
                "die freigegebene Zeit des übersprungenen Blocks soll wieder nutzbar sein");
    }

    /**
     * Erst verschieben, dann überspringen: der Block ist dadurch gepinnt und taucht in einer
     * anderen Abfrage auf als die übrigen. Seine Wochenquote muss trotzdem zählen.
     */
    @Test
    void einVerschobenerUndDannUebersprungenerBlockZaehltWeiterAufsWochenpensum() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Project project = makeProject(590L, "Everything App", 2, 60);
        project.setStartDate(monday);

        LocalDate droppedOn = monday.plusDays(2);
        CalendarEvent moved = makeSkippedEvent(591L, droppedOn.atTime(10, 0), droppedOn.atTime(11, 0));
        moved.setEventType(EventType.PROJECT);
        moved.setRelatedProject(project);
        moved.setTargetWeekStart(monday);
        moved.setIsFixed(true);   // Verschieben pinnt den Block

        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(List.of(project));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(moved));

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<LocalDate> days = projectItems(result).stream()
                .map(i -> i.getStartTime().toLocalDate()).sorted().toList();

        assertEquals(1, days.size(),
                "2 pro Woche minus die übersprungene ergibt genau eine neue Session: " + days);
    }

    private CalendarEvent makeSkippedEvent(Long id, LocalDateTime start, LocalDateTime end) {
        CalendarEvent ev = makeFixedEvent(id, start, end);
        ev.setIsFixed(false);
        ev.setSkippedAt(LocalDateTime.now());
        return ev;
    }

    /** Chronologisch sortierte Tage der geplanten Trainingsblöcke. */
    private List<LocalDate> workoutDays(ScheduleResult result) {
        return result.getScheduledHabits().stream()
                .filter(i -> i.getType() == ScheduledItemType.WORKOUT)
                .map(i -> i.getStartTime().toLocalDate())
                .sorted()
                .toList();
    }

    private WorkoutSession makeFlexibleWorkout(Long id, String name, int minutes, LocalDate weekStart) {
        WorkoutSession w = new WorkoutSession();
        w.setId(id);
        w.setName(name);
        w.setDurationMinutes(minutes);
        w.setIsFlexible(true);
        w.setIsCompleted(false);
        w.setTargetWeekStart(weekStart);
        return w;
    }

    // ==================================================================
    // Weitere Szenarien der automatischen Planung
    // ==================================================================

    // ------------------------------------------------------------------
    // Test 20 – Vorlesungen blockieren ihren Wochentag-Slot in jeder Woche
    // ------------------------------------------------------------------
    @Test
    void courseScheduleBlocksItsWeekdaySlot() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        CourseSchedule lecture = new CourseSchedule();
        lecture.setId(600L);
        lecture.setDayOfWeek(DayOfWeek.MONDAY);
        lecture.setStartTime(LocalTime.of(8, 0));
        lecture.setEndTime(LocalTime.of(12, 0));

        when(courseScheduleRepository.findByUserId(1L)).thenReturn(List.of(lecture));
        when(taskService.getSchedulableTasks(1L))
                .thenReturn(List.of(makeTask(610L, "Übungsblatt", 60, 3, monday.atTime(23, 59))));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday);

        ScheduledItem item = result.getScheduledTasks().get(0);
        assertFalse(item.getStartTime().isBefore(monday.atTime(12, 0)),
                "die Vorlesung von 08:00–12:00 muss den Vormittag belegen, lag aber bei "
                        + item.getStartTime());
    }

    // ------------------------------------------------------------------
    // Test 20a/b/c – Semestergrenzen
    //
    // Ein Stundenplan wurde bis hierher UNBEGRENZT in jede Woche expandiert. Mit dem
    // Stundenplan-CRUD bekommt er die Datumsgrenzen seines Semesters — und das ändert die
    // Kalender bestehender Nutzer. Deshalb steht die Rückwärtskompatibilität (ohne Semester
    // blockiert weiter) hier gleichberechtigt neben dem neuen Verhalten.
    //
    // Der Arbeitstag ist 08:00–17:00 (siehe setUp); eine Vorlesung über genau dieses Fenster
    // macht den Tag also entweder ganz dicht oder gar nicht. Das ist die Assertion, die nicht
    // davon abhängt, wohin der Solver eine Aufgabe sonst legen würde.
    // ------------------------------------------------------------------

    /** Vorlesung über den kompletten Arbeitstag; [semester] darf null sein. */
    private CourseSchedule fullDayLecture(DayOfWeek day, Semester semester) {
        Course course = new Course();
        course.setId(700L);
        course.setName("Analysis I");
        course.setSemesterRef(semester);

        CourseSchedule lecture = new CourseSchedule();
        lecture.setId(701L);
        lecture.setCourse(course);
        lecture.setDayOfWeek(day);
        lecture.setStartTime(LocalTime.of(8, 0));
        lecture.setEndTime(LocalTime.of(17, 0));
        return lecture;
    }

    private Semester semester(LocalDate start, LocalDate end) {
        Semester s = new Semester();
        s.setId(710L);
        s.setLabel("WS 2025/26");
        s.setStartDate(start);
        s.setEndDate(end);
        return s;
    }

    @Test
    void aScheduleWithoutASemesterKeepsBlockingForever() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(courseScheduleRepository.findByUserId(1L))
                .thenReturn(List.of(fullDayLecture(DayOfWeek.MONDAY, null)));
        when(taskService.getSchedulableTasks(1L))
                .thenReturn(List.of(makeTask(720L, "Übungsblatt", 60, 3, monday.atTime(23, 59))));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday);

        assertTrue(result.getScheduledTasks().isEmpty(),
                "ohne Semester gilt der Stundenplan unbegrenzt - der Tag bleibt dicht");
    }

    @Test
    void aScheduleOfAFinishedSemesterNoLongerBlocks() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Semester lastTerm = semester(TODAY.minusMonths(6), TODAY.minusMonths(1));

        when(courseScheduleRepository.findByUserId(1L))
                .thenReturn(List.of(fullDayLecture(DayOfWeek.MONDAY, lastTerm)));
        when(taskService.getSchedulableTasks(1L))
                .thenReturn(List.of(makeTask(721L, "Übungsblatt", 60, 3, monday.atTime(23, 59))));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday);

        assertEquals(1, result.getScheduledTasks().size(),
                "das Semester endete vor einem Monat, die Vorlesung findet nicht mehr statt");
    }

    @Test
    void aScheduleOfTheRunningSemesterStillBlocks() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Semester running = semester(TODAY.minusMonths(2), TODAY.plusMonths(2));

        when(courseScheduleRepository.findByUserId(1L))
                .thenReturn(List.of(fullDayLecture(DayOfWeek.MONDAY, running)));
        when(taskService.getSchedulableTasks(1L))
                .thenReturn(List.of(makeTask(722L, "Übungsblatt", 60, 3, monday.atTime(23, 59))));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday);

        assertTrue(result.getScheduledTasks().isEmpty(),
                "das Semester läuft, die Vorlesung blockiert den Tag");
    }

    // ------------------------------------------------------------------
    // Test 20d/e/f – Vorlesungen im Kalender
    //
    // Der Stundenplan sperrte bisher nur Zeit für den Solver, war im Kalender aber unsichtbar.
    // Jetzt wird er zusätzlich als CLASS-Termin abgebildet. Entscheidend ist dabei nicht das
    // Anlegen, sondern die Idempotenz: die Neuplanung läuft entprellt nach jeder Änderung, und
    // ein Fehler beim Aufräumen brächte bei jedem Lauf einen weiteren Satz Duplikate.
    // ------------------------------------------------------------------

    /** Alle gespeicherten Events vom Typ CLASS. */
    private List<CalendarEvent> savedLectures() {
        ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(e -> e.getEventType() == EventType.CLASS)
                .collect(Collectors.toList());
    }

    @Test
    void vorlesungenLandenAlsKalendereintraege() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        CourseSchedule lecture = fullDayLecture(DayOfWeek.MONDAY, semester(TODAY.minusMonths(2), TODAY.plusMonths(2)));
        lecture.setLocation("HS 1");
        lecture.getCourse().setColor("#3B82F6");
        lecture.getCourse().setInstructor("Prof. Meier");

        when(courseScheduleRepository.findByUserId(1L)).thenReturn(List.of(lecture));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        service.generateOptimalSchedule(1L, monday, monday);

        List<CalendarEvent> lectures = savedLectures();
        assertEquals(1, lectures.size(), "genau ein Termin für den einen Montag im Zeitraum");
        CalendarEvent ev = lectures.get(0);
        assertEquals("Analysis I", ev.getTitle(), "der Titel ist der Modulname");
        assertEquals("HS 1", ev.getLocation());
        assertEquals("Prof. Meier", ev.getDescription());
        assertEquals("#3B82F6", ev.getColor(), "die Modulfarbe, nicht die Space-Farbe");
        assertEquals(monday.atTime(8, 0), ev.getStartTime());
        assertEquals(monday.atTime(17, 0), ev.getEndTime());
        assertFalse(ev.getIsFixed(),
                "abgeleitet, nicht gepinnt: isFixed=true überlebte clearClassEvents und würde sich verdoppeln");
    }

    @Test
    void einZweiterLaufErzeugtKeineDoppelten() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(courseScheduleRepository.findByUserId(1L))
                .thenReturn(List.of(fullDayLecture(DayOfWeek.MONDAY,
                        semester(TODAY.minusMonths(2), TODAY.plusMonths(2)))));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        service.generateOptimalSchedule(1L, monday, monday);
        service.generateOptimalSchedule(1L, monday, monday);

        assertEquals(2, savedLectures().size(),
                "zwei Läufe, zwei Termine — je Lauf einer, nicht einer plus zwei");
        verify(calendarEventService, times(2)).clearClassEvents(eq(1L), any(), any());
    }

    @Test
    void vorlesungenAusserhalbDesSemestersErzeugenKeinenEintrag() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Semester lastTerm = semester(TODAY.minusMonths(8), TODAY.minusMonths(2));

        when(courseScheduleRepository.findByUserId(1L))
                .thenReturn(List.of(fullDayLecture(DayOfWeek.MONDAY, lastTerm)));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        service.generateOptimalSchedule(1L, monday, monday);

        verify(calendarEventRepository, never()).save(argThat(
                e -> e != null && e.getEventType() == EventType.CLASS));
    }

    @Test
    void vorlesungenVorDemUmplanzeitpunktWerdenNichtNeuAngelegt() {
        // Der Zeitraum beginnt heute, der Umplanzeitpunkt ist also "jetzt". Eine Vorlesung, die
        // um 00:00 begonnen hat, liegt davor und bleibt unangetastet — sonst löschte die
        // Neuplanung einen bereits laufenden Termin und legte ihn neu an.
        CourseSchedule lecture = fullDayLecture(TODAY.getDayOfWeek(), null);
        lecture.setStartTime(LocalTime.MIDNIGHT);
        lecture.setEndTime(LocalTime.of(0, 30));

        when(courseScheduleRepository.findByUserId(1L)).thenReturn(List.of(lecture));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        service.generateOptimalSchedule(1L, TODAY, TODAY);

        verify(calendarEventRepository, never()).save(argThat(
                e -> e != null && e.getEventType() == EventType.CLASS));
    }

    // ------------------------------------------------------------------
    // Test 21 – notBefore verschiebt den frühesten Start
    // ------------------------------------------------------------------
    @Test
    void notBeforeDelaysTheEarliestStart() {
        LocalDate tomorrow = TODAY.plusDays(1);

        Task task = makeTask(700L, "Rückmeldung", 60, 3, tomorrow.plusDays(1).atTime(23, 59));
        // Vor 14:00 sinnlos, weil die Zuarbeit erst dann da ist.
        task.setNotBefore(tomorrow.atTime(14, 0));

        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        ScheduledItem item = result.getScheduledTasks().get(0);
        assertEquals(tomorrow.atTime(14, 0), item.getStartTime(),
                "der Task muss exakt am notBefore-Zeitpunkt beginnen, nicht früher");
    }

    // ------------------------------------------------------------------
    // Test 22 – Ein nicht teilbarer Task bleibt ein einziger Block
    // ------------------------------------------------------------------
    @Test
    void unsplittableTaskStaysOneBlock() {
        LocalDate tomorrow = TODAY.plusDays(1);

        Task task = makeTask(800L, "Klausur schreiben", 240, 4, tomorrow.plusDays(3).atTime(23, 59));
        task.setSplittable(false);   // Standard-Maximum wären 120 Minuten pro Block

        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertEquals(1, result.getScheduledTasks().size(), "splittable=false darf nicht zerlegt werden");
        ScheduledItem item = result.getScheduledTasks().get(0);
        assertEquals(240, ChronoUnit.MINUTES.between(item.getStartTime(), item.getEndTime()));
    }

    // ------------------------------------------------------------------
    // Test 23 – Bereits geplante Blöcke bleiben liegen, wo sie liegen
    // ------------------------------------------------------------------
    @Test
    void existingPlacementIsKeptWhenNothingForcesAMove() {
        LocalDate tomorrow = TODAY.plusDays(1);

        // Priorität 1 mit Absicht: die Dringlichkeit wiegt W_URGENCY * Priorität pro Slot,
        // das Verschieben W_MOVE = 3. Bei Priorität 3 stünde es exakt unentschieden, und der
        // Test würde nur noch die Suchreihenfolge des Solvers festschreiben.
        Task task = makeTask(900L, "Recherche", 60, 1, tomorrow.plusDays(5).atTime(23, 59));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        // Aus dem letzten Lauf: der Block lag um 14:00, obwohl 08:00 „dringlicher“ wäre.
        CalendarEvent previous = new CalendarEvent();
        previous.setId(901L);
        previous.setIsFixed(false);
        previous.setEventType(EventType.TASK);
        previous.setRelatedTask(task);
        previous.setStartTime(tomorrow.atTime(14, 0));
        previous.setEndTime(tomorrow.atTime(15, 0));
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any())).thenReturn(List.of(previous));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertEquals(tomorrow.atTime(14, 0), result.getScheduledTasks().get(0).getStartTime(),
                "ohne Grund zu verschieben muss der Block liegen bleiben");
    }

    // ------------------------------------------------------------------
    // Test 24 – Mehr Arbeit als Platz: der Rest wird gemeldet, nicht verschluckt
    // ------------------------------------------------------------------
    @Test
    void overloadedDayPlacesWhatFitsAndReportsTheRest() {
        LocalDate tomorrow = TODAY.plusDays(1);

        // 5 unteilbare Blöcke à 3 Stunden auf einen Tag. Bindend ist hier nicht das
        // Arbeitsfenster 08:00–17:00 (9 h), sondern das Tageslimit für Task-Zeit von
        // 480 Minuten — es passen also 2 Blöcke, nicht 3.
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Task t = makeTask(1000L + i, "Brocken " + i, 180, 3, tomorrow.atTime(23, 59));
            t.setSplittable(false);
            tasks.add(t);
        }
        when(taskService.getSchedulableTasks(1L)).thenReturn(tasks);
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertEquals(2, result.getScheduledTasks().size(),
                "das Tageslimit von 480 Minuten lässt genau 2 Blöcke à 180 Minuten zu");
        assertFalse(result.getAtRisk().isEmpty(),
                "was nicht passt, muss als at-risk gemeldet werden statt lautlos zu verschwinden");
        assertEquals(3, result.getUnscheduledTasks().size());

        // Und die platzierten Blöcke überlappen sich nicht.
        List<ScheduledItem> placed = new ArrayList<>(result.getScheduledTasks());
        placed.sort(java.util.Comparator.comparing(ScheduledItem::getStartTime));
        for (int i = 1; i < placed.size(); i++) {
            assertFalse(placed.get(i).getStartTime().isBefore(placed.get(i - 1).getEndTime()),
                    "Blöcke dürfen sich nicht überlappen");
        }
    }

    // ------------------------------------------------------------------
    // Test 25 – Nichts zu planen ist kein Fehler
    // ------------------------------------------------------------------
    @Test
    void emptyInputProducesAnEmptyScheduleWithoutTouchingTheCalendar() {
        LocalDate tomorrow = TODAY.plusDays(1);

        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertTrue(result.getScheduledTasks().isEmpty());
        assertTrue(result.getScheduledHabits().isEmpty());
        assertEquals(0, result.getTotalTasksScheduled());
        verify(calendarEventRepository, never()).save(any(CalendarEvent.class));
    }

    // ==================================================================
    // Die Vergangenheit ist eingefroren
    // ==================================================================

    // ------------------------------------------------------------------
    // Test 26 – Bereits gelaufene Blöcke werden nicht weggelöscht
    // ------------------------------------------------------------------
    @Test
    void alreadyStartedBlocksSurviveRegeneration() {
        // Horizont beginnt gestern: alles von gestern liegt garantiert in der
        // Vergangenheit, unabhängig von der Uhrzeit, zu der der Test läuft.
        LocalDate yesterday = TODAY.minusDays(1);

        Task task = makeTask(1200L, "Recherche", 120, 3, TODAY.plusDays(1).atTime(23, 59));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        // 60 der 120 Minuten wurden gestern früh bereits verplant (und sind gelaufen).
        CalendarEvent past = new CalendarEvent();
        past.setId(1201L);
        past.setIsFixed(false);
        past.setEventType(EventType.TASK);
        past.setRelatedTask(task);
        past.setStartTime(yesterday.atTime(9, 0));
        past.setEndTime(yesterday.atTime(10, 0));
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any())).thenReturn(List.of(past));

        service.generateOptimalSchedule(1L, yesterday, TODAY.plusDays(1));

        // Gelöscht werden darf frühestens ab jetzt — sonst verschwindet der Vormittag
        // aus dem Kalender, sobald irgendetwas eine Neuplanung auslöst.
        org.mockito.ArgumentCaptor<LocalDateTime> from =
                org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(calendarEventService).clearScheduledEvents(eq(1L), from.capture(), any());
        assertFalse(from.getValue().isBefore(TODAY.atStartOfDay()),
                "gestrige Blöcke dürfen nicht mehr im Löschfenster liegen, war: " + from.getValue());
    }

    // ------------------------------------------------------------------
    // Test 27 – Gelaufene Zeit wird von der Restdauer abgezogen
    // ------------------------------------------------------------------
    @Test
    void alreadyStartedBlockCountsTowardsTheTaskDuration() {
        LocalDate yesterday = TODAY.minusDays(1);

        Task task = makeTask(1210L, "Recherche", 120, 3, TODAY.plusDays(1).atTime(23, 59));
        task.setSplittable(false);
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        CalendarEvent past = new CalendarEvent();
        past.setId(1211L);
        past.setIsFixed(false);
        past.setEventType(EventType.TASK);
        past.setRelatedTask(task);
        past.setStartTime(yesterday.atTime(9, 0));
        past.setEndTime(yesterday.atTime(10, 0));
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any())).thenReturn(List.of(past));

        ScheduleResult result = service.generateOptimalSchedule(1L, yesterday, TODAY.plusDays(1));

        long planned = result.getScheduledTasks().stream()
                .mapToLong(i -> ChronoUnit.MINUTES.between(i.getStartTime(), i.getEndTime()))
                .sum();
        assertEquals(60, planned,
                "von 120 Minuten sind 60 gelaufen — neu zu planen sind genau die restlichen 60");
    }

    // ------------------------------------------------------------------
    // Test 27b – Ein vollständig gelaufener Task behält seine Zeiten
    // ------------------------------------------------------------------
    @Test
    void taskFullyCoveredByPastBlocksKeepsItsSpan() {
        LocalDate yesterday = TODAY.minusDays(1);

        // 60 von 60 Minuten liegen bereits hinter uns: es gibt nichts mehr neu zu planen.
        Task task = makeTask(1220L, "Erledigt sich", 60, 3, TODAY.plusDays(1).atTime(23, 59));
        task.setScheduledStartTime(yesterday.atTime(9, 0));
        task.setScheduledEndTime(yesterday.atTime(10, 0));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        CalendarEvent past = new CalendarEvent();
        past.setId(1221L);
        past.setIsFixed(false);
        past.setEventType(EventType.TASK);
        past.setRelatedTask(task);
        past.setStartTime(yesterday.atTime(9, 0));
        past.setEndTime(yesterday.atTime(10, 0));
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any())).thenReturn(List.of(past));

        ScheduleResult result = service.generateOptimalSchedule(1L, yesterday, TODAY.plusDays(1));

        // Der Block steht im Kalender — die Task-Liste darf ihn nicht als „ungeplant“ führen.
        verify(taskService, never()).clearSchedule(1220L);
        verify(taskService).scheduleTask(1220L, yesterday.atTime(9, 0), yesterday.atTime(10, 0));
        assertTrue(result.getUnscheduledTasks().stream().noneMatch(t -> t.getId().equals(1220L)),
                "ein Task, dessen Blöcke im Kalender stehen, ist nicht ungeplant");
    }

    // ------------------------------------------------------------------
    // Test 27c – Dasselbe für einen gepinnten Block
    // ------------------------------------------------------------------
    @Test
    void taskFullyCoveredByPinnedBlockKeepsItsSpan() {
        LocalDate tomorrow = TODAY.plusDays(1);

        Task task = makeTask(1230L, "Selbst gelegt", 90, 3, tomorrow.plusDays(2).atTime(23, 59));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));

        // Der Nutzer hat den Block selbst gelegt und gepinnt — er deckt den Task komplett ab.
        CalendarEvent pinned = makeFixedEvent(1231L, tomorrow.atTime(14, 0), tomorrow.atTime(15, 30));
        pinned.setEventType(EventType.TASK);
        pinned.setRelatedTask(task);
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(pinned));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(2));

        verify(taskService, never()).clearSchedule(1230L);
        verify(taskService).scheduleTask(1230L, tomorrow.atTime(14, 0), tomorrow.atTime(15, 30));
        assertTrue(result.getUnscheduledTasks().isEmpty());
    }

    // ------------------------------------------------------------------
    // Test 27d – Überfälliger Task wird trotz verpasstem Block neu geplant
    //
    // Gegenstück zu 27b: dort deckt der vergangene Block den Task ab und alles bleibt stehen.
    // Ist die Deadline aber abgelaufen und der Task immer noch offen, gilt der Block als
    // verpasst — die volle Restdauer muss nach vorne wandern.
    // ------------------------------------------------------------------
    @Test
    void ueberfaelligerTaskWirdTrotzVerpasstemBlockNeuGeplant() {
        LocalDate yesterday = TODAY.minusDays(1);

        Task task = makeTask(1240L, "Steuererklärung", 60, 3, yesterday.atTime(23, 59));
        task.setScheduledStartTime(yesterday.atTime(9, 0));
        task.setScheduledEndTime(yesterday.atTime(10, 0));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        // Der Block lag gestern und wurde nie abgehakt.
        CalendarEvent missed = new CalendarEvent();
        missed.setId(1241L);
        missed.setIsFixed(false);
        missed.setEventType(EventType.TASK);
        missed.setRelatedTask(task);
        missed.setStartTime(yesterday.atTime(9, 0));
        missed.setEndTime(yesterday.atTime(10, 0));
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any())).thenReturn(List.of(missed));

        ScheduleResult result = service.generateOptimalSchedule(1L, yesterday, TODAY.plusDays(2));

        long planned = result.getScheduledTasks().stream()
                .mapToLong(i -> ChronoUnit.MINUTES.between(i.getStartTime(), i.getEndTime()))
                .sum();
        assertEquals(60, planned,
                "der verpasste Block zählt nicht als geleistet — die vollen 60 Minuten werden neu geplant");

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskService).scheduleTask(eq(1240L), start.capture(), any());
        assertFalse(start.getValue().isBefore(LocalDateTime.now().toLocalDate().atStartOfDay()),
                "der Task muss auf den nächstmöglichen Termin rücken, stand aber auf "
                        + start.getValue());
    }

    // ------------------------------------------------------------------
    // Test 27e – Ein abgehakter Block zählt auch bei abgelaufener Deadline
    // ------------------------------------------------------------------
    @Test
    void abgehakterBlockZaehltAuchBeiAbgelaufenerDeadline() {
        LocalDate yesterday = TODAY.minusDays(1);

        // Deadline abgelaufen, aber die Zeit ist nachweislich gelaufen: 60 von 60 Minuten sind
        // gutgeschrieben. Es gibt nichts neu zu planen, und der Block bleibt der Termin des Tasks.
        Task task = makeTask(1250L, "War erledigt", 60, 3, yesterday.atTime(23, 59));
        task.setCompletedMinutes(60);
        task.setScheduledStartTime(yesterday.atTime(9, 0));
        task.setScheduledEndTime(yesterday.atTime(10, 0));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        CalendarEvent done = new CalendarEvent();
        done.setId(1251L);
        done.setIsFixed(false);
        done.setEventType(EventType.TASK);
        done.setRelatedTask(task);
        done.setStartTime(yesterday.atTime(9, 0));
        done.setEndTime(yesterday.atTime(10, 0));
        done.setCompletedAt(yesterday.atTime(10, 0));
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any())).thenReturn(List.of(done));

        ScheduleResult result = service.generateOptimalSchedule(1L, yesterday, TODAY.plusDays(2));

        assertTrue(result.getScheduledTasks().isEmpty(),
                "ein abgehakter Block ist geleistete Zeit — es darf nichts neu geplant werden");
        verify(taskService, never()).clearSchedule(1250L);
        verify(taskService).scheduleTask(1250L, yesterday.atTime(9, 0), yesterday.atTime(10, 0));
    }

    // ------------------------------------------------------------------
    // Test 27f – Der überfällige Task kommt vor den entspannten
    // ------------------------------------------------------------------
    @Test
    void ueberfaelligerTaskLandetVorEinemEntspanntenTask() {
        LocalDate tomorrow = TODAY.plusDays(1);

        Task overdue = makeTask(1260L, "Längst fällig", 60, 3, TODAY.minusDays(1).atTime(23, 59));
        Task relaxed = makeTask(1261L, "Hat noch Zeit", 60, 3, TODAY.plusDays(10).atTime(23, 59));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(relaxed, overdue));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(2));

        LocalDateTime overdueStart = startOf(result, 1260L);
        LocalDateTime relaxedStart = startOf(result, 1261L);
        assertNotNull(overdueStart, "der überfällige Task muss geplant werden");
        assertNotNull(relaxedStart, "der entspannte Task muss geplant werden");
        assertTrue(overdueStart.isBefore(relaxedStart),
                "der überfällige Task gehört nach vorne: " + overdueStart + " vs. " + relaxedStart);
    }

    // ------------------------------------------------------------------
    // Test 27g – Auch ein lange überfälliger Task wird noch geplant
    //
    // Die Verspätung ist hier größer als der gesamte Horizont. Mit der alten Obergrenze von
    // late war die Deadline-Bedingung damit unerfüllbar und CP-SAT musste den Block verwerfen.
    // ------------------------------------------------------------------
    @Test
    void langeUeberfaelligerTaskWirdNichtVerworfen() {
        LocalDate tomorrow = TODAY.plusDays(1);

        Task ancient = makeTask(1270L, "Seit Ewigkeiten offen", 60, 3,
                TODAY.minusDays(200).atTime(23, 59));
        ancient.setScheduledStartTime(tomorrow.atTime(9, 0));
        ancient.setScheduledEndTime(tomorrow.atTime(10, 0));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(ancient));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow.plusDays(2));

        assertNotNull(startOf(result, 1270L),
                "eine 200 Tage alte Deadline darf den Task nicht aus dem Plan werfen");
        assertTrue(result.getUnscheduledTasks().stream().noneMatch(t -> t.getId().equals(1270L)));
        verify(taskService, never()).clearSchedule(1270L);
    }

    /** Startzeit des ersten Blocks eines Tasks, oder {@code null}, wenn er nicht geplant wurde. */
    private LocalDateTime startOf(ScheduleResult result, Long taskId) {
        return result.getScheduledTasks().stream()
                .filter(i -> i.getTask() != null && taskId.equals(i.getTask().getId()))
                .map(ScheduledItem::getStartTime)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // Test 28 – Flexible Habits bleiben an ihren Tagen
    // ------------------------------------------------------------------
    @Test
    void flexibleHabitKeepsItsDaysAcrossRegenerations() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        Habit habit = new Habit();
        habit.setId(1300L);
        habit.setName("Laufen");
        habit.setDurationMinutes(45);
        habit.setPriority(3);
        habit.setTimesPerWeek(3);
        habit.setIdealWindow(HabitWindow.ANYTIME);
        habit.setStartDate(monday);

        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(habit));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        // Aus dem letzten Lauf: Montag, Mittwoch, Freitag um 09:00.
        List<CalendarEvent> previous = new ArrayList<>();
        int id = 1301;
        for (int offset : new int[]{ 0, 2, 4 }) {
            CalendarEvent ev = new CalendarEvent();
            ev.setId((long) id++);
            ev.setIsFixed(false);
            ev.setEventType(EventType.HABIT);
            ev.setRelatedHabit(habit);
            ev.setStartTime(monday.plusDays(offset).atTime(9, 0));
            ev.setEndTime(monday.plusDays(offset).atTime(9, 45));
            previous.add(ev);
        }
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any())).thenReturn(previous);

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<LocalDate> days = result.getScheduledHabits().stream()
                .map(i -> i.getStartTime().toLocalDate()).sorted().toList();
        assertEquals(List.of(monday, monday.plusDays(2), monday.plusDays(4)), days,
                "ohne Grund umzuplanen müssen flexible Habits an ihren Tagen bleiben");
        assertTrue(result.getScheduledHabits().stream()
                        .allMatch(i -> i.getStartTime().toLocalTime().equals(LocalTime.of(9, 0))),
                "und auch zur bisherigen Uhrzeit");
    }

    // ------------------------------------------------------------------
    // Test 29 – Peak-Productivity-Fenster wird bevorzugt
    // ------------------------------------------------------------------
    @Test
    void peakProductivityWindowIsPreferredForTasks() {
        prefs.setWorkdayEnd(LocalTime.of(22, 0));
        prefs.setPeakProductivityTime(ProductivityPeakTime.EVENING);   // 17:00–22:00

        LocalDate tomorrow = TODAY.plusDays(1);
        Task task = makeTask(1400L, "Konzept", 60, 3, tomorrow.plusDays(5).atTime(23, 59));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        LocalTime start = result.getScheduledTasks().get(0).getStartTime().toLocalTime();
        assertFalse(start.isBefore(LocalTime.of(17, 0)),
                "bei Peak EVENING muss der Block in den Abend wandern, lag aber " + start);
    }

    // ------------------------------------------------------------------
    // Test 29b – Ein gerade laufender Block wird nicht überplant
    // ------------------------------------------------------------------
    @Test
    void blockInProgressIsNotOverbooked() {
        prefs.setBreakDurationMinutes(15);
        LocalDateTime now = LocalDateTime.now();

        Task other = makeTask(1250L, "Anderes", 60, 5, TODAY.plusDays(1).atTime(23, 59));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(other));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        // Läuft seit einer halben Stunde und dauert noch eine halbe Stunde.
        CalendarEvent running = new CalendarEvent();
        running.setId(1251L);
        running.setIsFixed(false);
        running.setEventType(EventType.TASK);
        running.setRelatedTask(makeTask(1252L, "Läuft gerade", 60, 3, null));
        running.setStartTime(now.minusMinutes(30));
        running.setEndTime(now.plusMinutes(30));
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any())).thenReturn(List.of(running));

        ScheduleResult result = service.generateOptimalSchedule(1L, TODAY, TODAY.plusDays(1));

        for (ScheduledItem i : result.getScheduledTasks()) {
            assertFalse(i.getStartTime().isBefore(running.getEndTime())
                            && i.getEndTime().isAfter(running.getStartTime()),
                    "der laufende Block " + running.getStartTime() + "–" + running.getEndTime()
                            + " darf nicht überplant werden, war: " + i.getStartTime());
            // Auch auf einen laufenden Block folgt die eingestellte Pause.
            if (i.getStartTime().toLocalDate().equals(running.getEndTime().toLocalDate())
                    && !i.getStartTime().isBefore(running.getEndTime())) {
                assertFalse(i.getStartTime().isBefore(running.getEndTime().plusMinutes(15)),
                        "nach dem laufenden Block müssen 15 Minuten Pause bleiben, war: "
                                + i.getStartTime());
            }
        }
    }

    // ------------------------------------------------------------------
    // Test 30 – Zwischen zwei Blöcken bleibt die eingestellte Pause
    // ------------------------------------------------------------------
    @Test
    void breakDurationKeepsBlocksApart() {
        prefs.setBreakDurationMinutes(15);

        LocalDate tomorrow = TODAY.plusDays(1);
        Task a = makeTask(1500L, "A", 60, 3, tomorrow.plusDays(3).atTime(23, 59));
        Task b = makeTask(1501L, "B", 60, 3, tomorrow.plusDays(3).atTime(23, 59));
        a.setSplittable(false);
        b.setSplittable(false);
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(a, b));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        List<ScheduledItem> placed = new ArrayList<>(result.getScheduledTasks());
        assertEquals(2, placed.size());
        placed.sort(java.util.Comparator.comparing(ScheduledItem::getStartTime));
        long gap = ChronoUnit.MINUTES.between(placed.get(0).getEndTime(), placed.get(1).getStartTime());
        assertTrue(gap >= 15, "zwischen zwei Blöcken müssen 15 Minuten Pause liegen, waren: " + gap);
    }

    // ------------------------------------------------------------------
    // Test 30b – Pause und Meeting-Puffer addieren sich nicht
    // ------------------------------------------------------------------
    @Test
    void breakAndMeetingBufferDoNotStack() {
        prefs.setBufferMinutes(10);
        prefs.setBreakDurationMinutes(15);

        LocalDate tomorrow = TODAY.plusDays(1);
        // Nur ein einziger Platz kommt in Frage: 08:00–09:00, direkt gefolgt von der Pause bis
        // 09:15 und dem Meeting ab 09:15. Mit addierten 25 Minuten müsste der Block weichen.
        Task task = makeTask(1550L, "Vorbereitung", 60, 5, tomorrow.atTime(9, 15));
        task.setSplittable(false);
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task));

        CalendarEvent meeting = makeFixedEvent(1551L, tomorrow.atTime(9, 15), tomorrow.atTime(12, 0));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(meeting));

        ScheduleResult result = service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        assertEquals(1, result.getScheduledTasks().size(),
                "der Block passt in die Lücke — 15 Minuten Pause reichen, es sind nicht 10+15");
        assertEquals(tomorrow.atTime(8, 0), result.getScheduledTasks().get(0).getStartTime());
    }

    // ------------------------------------------------------------------
    // Test 31 – Ein zweiter Lauf ohne Änderung ändert nichts (Idempotenz)
    // ------------------------------------------------------------------
    @Test
    void secondRunWithoutChangesReproducesTheSameSchedule() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        // Gemischte, realistische Last: Tasks unterschiedlicher Priorität, eine flexible Habit,
        // eine Wochentag-Habit, ein Kurs und ein fixer Termin.
        List<Task> tasks = List.of(
                makeTask(1600L, "Hausarbeit", 240, 5, monday.plusDays(4).atTime(18, 0)),
                makeTask(1601L, "Lesen",       90, 2, monday.plusDays(6).atTime(23, 59)),
                makeTask(1602L, "Mails",       45, 3, monday.plusDays(2).atTime(23, 59)));
        when(taskService.getSchedulableTasks(1L)).thenReturn(tasks);

        Habit laufen = new Habit();
        laufen.setId(1610L);
        laufen.setName("Laufen");
        laufen.setDurationMinutes(45);
        laufen.setPriority(3);
        laufen.setTimesPerWeek(3);
        laufen.setIdealWindow(HabitWindow.MORNING);
        laufen.setStartDate(monday);

        Habit vokabeln = new Habit();
        vokabeln.setId(1611L);
        vokabeln.setName("Vokabeln");
        vokabeln.setDurationMinutes(30);
        vokabeln.setPriority(4);
        vokabeln.setIdealWindow(HabitWindow.EVENING);
        vokabeln.setStartDate(monday);
        vokabeln.setMonday(true);
        vokabeln.setWednesday(true);
        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any()))
                .thenReturn(List.of(laufen, vokabeln));

        CourseSchedule course = new CourseSchedule();
        course.setDayOfWeek(DayOfWeek.TUESDAY);
        course.setStartTime(LocalTime.of(10, 0));
        course.setEndTime(LocalTime.of(12, 0));
        when(courseScheduleRepository.findByUserId(1L)).thenReturn(List.of(course));

        CalendarEvent meeting = makeFixedEvent(1620L, monday.atTime(9, 0), monday.atTime(10, 30));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(meeting));

        ScheduleResult first = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));
        assertFalse(first.getScheduledTasks().isEmpty());

        // Was der erste Lauf geschrieben hat, ist beim zweiten Lauf der Vorzustand.
        org.mockito.ArgumentCaptor<CalendarEvent> saved =
                org.mockito.ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository, atLeastOnce()).save(saved.capture());
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any())).thenReturn(new ArrayList<>(saved.getAllValues()));

        ScheduleResult second = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        assertEquals(describe(first), describe(second),
                "ohne Änderung an den Eingaben darf sich der Plan nicht bewegen");
    }

    // ------------------------------------------------------------------
    // Test 32 – Der erzeugte Plan ist in sich konsistent
    // ------------------------------------------------------------------
    @Test
    void generatedScheduleRespectsAllHardConstraints() {
        prefs.setBufferMinutes(10);
        prefs.setBreakDurationMinutes(10);

        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(makeTask(1700L + i, "T" + i, 90 + i * 30, 1 + (i % 5),
                    monday.plusDays(3 + (i % 4)).atTime(20, 0)));
        }
        when(taskService.getSchedulableTasks(1L)).thenReturn(tasks);

        Habit habit = new Habit();
        habit.setId(1720L);
        habit.setName("Sport");
        habit.setDurationMinutes(60);
        habit.setPriority(3);
        habit.setTimesPerWeek(4);
        habit.setIdealWindow(HabitWindow.ANYTIME);
        habit.setStartDate(monday);
        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(habit));

        CalendarEvent meeting = makeFixedEvent(1730L, monday.plusDays(1).atTime(13, 0),
                monday.plusDays(1).atTime(15, 0));
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(meeting));

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<ScheduledItem> all = new ArrayList<>(result.getScheduledTasks());
        all.addAll(result.getScheduledHabits());
        assertFalse(all.isEmpty());
        all.sort(java.util.Comparator.comparing(ScheduledItem::getStartTime));

        for (int i = 0; i < all.size(); i++) {
            ScheduledItem item = all.get(i);

            assertFalse(item.getStartTime().toLocalTime().isBefore(LocalTime.of(8, 0)),
                    item.getStartTime() + " liegt vor Arbeitsbeginn");
            assertFalse(item.getEndTime().toLocalTime().isAfter(LocalTime.of(17, 0)),
                    item.getEndTime() + " liegt nach Feierabend");
            assertEquals(item.getStartTime().toLocalDate(), item.getEndTime().toLocalDate(),
                    "kein Block darf über Mitternacht laufen");
            assertFalse(item.getStartTime().isBefore(LocalDateTime.now()),
                    "nichts wird in die Vergangenheit geplant");

            // Puffer um den fixen Termin herum.
            assertFalse(item.getStartTime().isBefore(meeting.getEndTime())
                            && item.getEndTime().isAfter(meeting.getStartTime()),
                    item + " überlappt den fixen Termin");

            if (i > 0) {
                assertFalse(item.getStartTime().isBefore(all.get(i - 1).getEndTime()),
                        "Blöcke dürfen sich nicht überlappen");
            }
        }

        // Tageslimit für Task-Zeit.
        java.util.Map<LocalDate, Long> perDay = new java.util.HashMap<>();
        for (ScheduledItem t : result.getScheduledTasks()) {
            perDay.merge(t.getStartTime().toLocalDate(),
                    ChronoUnit.MINUTES.between(t.getStartTime(), t.getEndTime()), Long::sum);
        }
        perDay.forEach((day, minutes) -> assertTrue(minutes <= 480,
                "am " + day + " sind " + minutes + " Minuten Task-Zeit verplant, erlaubt sind 480"));
    }

    /** Vergleichbare Kurzform eines Plans: Typ, Titel und Zeitraum je Block. */
    private List<String> describe(ScheduleResult result) {
        List<String> out = new ArrayList<>();
        for (ScheduledItem i : result.getScheduledTasks()) {
            out.add("task " + i.getTask().getId() + " " + i.getStartTime() + "-" + i.getEndTime());
        }
        for (ScheduledItem i : result.getScheduledHabits()) {
            out.add("other " + (i.getHabit() != null ? i.getHabit().getId() : "?")
                    + " " + i.getStartTime() + "-" + i.getEndTime());
        }
        java.util.Collections.sort(out);
        return out;
    }

    // ==================================================================
    // Projekt-Sessions: wöchentliche Projektzeit in den Kalenderlücken
    // ==================================================================

    // ------------------------------------------------------------------
    // Ein Projekt mit 3x/Woche bekommt drei Blöcke an drei verschiedenen Tagen
    // ------------------------------------------------------------------
    @Test
    void projektBekommtDreiBloeckeProWoche() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Project project = makeProject(500L, "Hausbau", 3, 60);

        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(List.of(project));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<ScheduledItem> sessions = projectItems(result);
        assertEquals(3, sessions.size(), "weeklySessionCount=3 ergibt drei Blöcke");

        List<LocalDate> days = sessions.stream().map(i -> i.getStartTime().toLocalDate()).toList();
        assertEquals(3, new java.util.HashSet<>(days).size(),
                "höchstens eine Projekt-Session pro Tag");

        for (ScheduledItem s : sessions) {
            assertEquals(60, ChronoUnit.MINUTES.between(s.getStartTime(), s.getEndTime()));
            assertEquals(project, s.getProject());
        }
    }

    // ------------------------------------------------------------------
    // Projektzeit läuft über den vollen Horizont, nicht nur über das Task-Fenster
    // ------------------------------------------------------------------
    @Test
    void projektSessionsInJederWocheDesHorizonts() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Project project = makeProject(510L, "Roman schreiben", 2, 90);

        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(List.of(project));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        // Sechs volle Wochen — deutlich mehr als der Task-Horizont von 14 Tagen.
        LocalDate end = monday.plusWeeks(6).minusDays(1);
        ScheduleResult result = service.generateOptimalSchedule(1L, monday, end);

        for (int week = 0; week < 6; week++) {
            LocalDate weekStart = monday.plusWeeks(week);
            LocalDate weekEnd = weekStart.plusDays(6);
            long inWeek = projectItems(result).stream()
                    .map(i -> i.getStartTime().toLocalDate())
                    .filter(d -> !d.isBefore(weekStart) && !d.isAfter(weekEnd))
                    .count();
            assertEquals(2, inWeek, "Woche ab " + weekStart + " braucht zwei Projekt-Blöcke");
        }
    }

    // ------------------------------------------------------------------
    // Nur planbare Status fragen wir überhaupt ab
    // ------------------------------------------------------------------
    @Test
    void abgeschlosseneUndPausierteProjekteWerdenNichtGeladen() {
        LocalDate tomorrow = TODAY.plusDays(1);
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        service.generateOptimalSchedule(1L, tomorrow, tomorrow);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<ProjectStatus>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(projectRepository).findByUserIdAndStatusIn(eq(1L), captor.capture());

        java.util.Collection<ProjectStatus> asked = captor.getValue();
        assertTrue(asked.contains(ProjectStatus.PLANNING), "ein frisches Projekt braucht Projektzeit");
        assertTrue(asked.contains(ProjectStatus.ACTIVE));
        assertTrue(asked.contains(ProjectStatus.IN_PROGRESS));
        assertFalse(asked.contains(ProjectStatus.COMPLETED));
        assertFalse(asked.contains(ProjectStatus.CANCELLED));
        assertFalse(asked.contains(ProjectStatus.ON_HOLD));
    }

    // ------------------------------------------------------------------
    // Start- und Zieldatum schneiden den Bereich zu
    // ------------------------------------------------------------------
    @Test
    void projektSessionsRespektierenStartUndZieldatum() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Project project = makeProject(520L, "Umzug", 3, 60);
        project.setStartDate(monday.plusDays(1));
        project.setTargetEndDate(monday.plusDays(3));

        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(List.of(project));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(13));

        List<LocalDate> days = projectItems(result).stream()
                .map(i -> i.getStartTime().toLocalDate()).toList();
        assertFalse(days.isEmpty(), "im erlaubten Fenster muss Projektzeit liegen");
        for (LocalDate d : days) {
            assertFalse(d.isBefore(project.getStartDate()), "nichts vor dem Startdatum: " + d);
            assertFalse(d.isAfter(project.getTargetEndDate()), "nichts nach dem Zieldatum: " + d);
        }
    }

    // ------------------------------------------------------------------
    // Gepinnter Projektblock verbraucht seinen Tag und seine Wochenquote
    // ------------------------------------------------------------------
    @Test
    void gepinnterProjektblockVerbrauchtSeineWochenquote() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Project project = makeProject(530L, "Garten", 3, 60);
        project.setStartDate(monday);

        LocalDate wednesday = monday.plusDays(2);
        CalendarEvent pinned = makeFixedEvent(531L, wednesday.atTime(16, 0), wednesday.atTime(17, 0));
        pinned.setEventType(EventType.PROJECT);
        pinned.setRelatedProject(project);

        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(List.of(project));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(pinned));

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        List<ScheduledItem> sessions = projectItems(result);
        assertEquals(2, sessions.size(),
                "3x pro Woche minus der eine gepinnte Block ergibt 2 neu zu planende");

        List<LocalDate> days = sessions.stream().map(i -> i.getStartTime().toLocalDate()).toList();
        assertFalse(days.contains(wednesday),
                "am gepinnten Tag darf keine zweite Session desselben Projekts entstehen");
    }

    /**
     * Ein in die Folgewoche gezogener Projektblock darf in seiner Ursprungswoche keinen Ersatz
     * nach sich ziehen.
     *
     * Vorher wurde das Pensum je ISO-Woche unabhängig aufgefüllt: die Zielwoche zählte den
     * verschobenen Block mit, die verlassene Woche stand dadurch unter Pensum und bekam einen
     * neuen Block — den der Stabilitätsanker prompt wieder an den alten Platz legte. Für den
     * Nutzer sah es aus, als stünde der Termin nach dem Verschieben doppelt im Kalender.
     */
    @Test
    void einInDieFolgewocheGezogenerProjektblockErzeugtKeinenErsatzInDerAltenWoche() {
        LocalDate weekA = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate weekB = weekA.plusWeeks(1);

        Project project = makeProject(560L, "Everything App", 2, 60);
        project.setStartDate(weekA);

        // Der Block gehört zum Pensum von Woche A, liegt nach dem Verschieben aber in Woche B.
        LocalDate droppedOn = weekB.plusDays(1);
        CalendarEvent moved = makeFixedEvent(561L, droppedOn.atTime(16, 0), droppedOn.atTime(17, 0));
        moved.setEventType(EventType.PROJECT);
        moved.setRelatedProject(project);
        moved.setTargetWeekStart(weekA);

        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(List.of(project));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(moved));

        ScheduleResult result = service.generateOptimalSchedule(1L, weekA, weekB.plusDays(6));

        List<LocalDate> days = projectItems(result).stream()
                .map(i -> i.getStartTime().toLocalDate())
                .sorted()
                .toList();

        long inWeekA = days.stream().filter(d -> d.isBefore(weekB)).count();
        long inWeekB = days.stream().filter(d -> !d.isBefore(weekB)).count();

        assertEquals(1, inWeekA,
                "Woche A ist durch den verschobenen Block schon zur Hälfte gedeckt, es fehlt nur "
                        + "noch eine Session — geplant wurde aber: " + days);
        assertEquals(2, inWeekB, "Woche B behält ihr volles Pensum: " + days);
        assertFalse(days.contains(droppedOn),
                "am Tag des verschobenen Blocks darf keine zweite Session entstehen: " + days);
    }

    /** Die Wochenzuordnung muss am neu angelegten Kalendereintrag hängen, sonst geht sie verloren. */
    @Test
    void projektblockWirdMitSeinerZielwocheGespeichert() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Project project = makeProject(570L, "Everything App", 1, 60);
        project.setStartDate(monday);

        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(List.of(project));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        ArgumentCaptor<CalendarEvent> saved = ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository, atLeastOnce()).save(saved.capture());

        CalendarEvent block = saved.getAllValues().stream()
                .filter(e -> e.getEventType() == EventType.PROJECT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("kein Projektblock gespeichert"));

        assertEquals(monday, block.getTargetWeekStart(),
                "der Block muss die Woche kennen, deren Pensum er abdeckt");
    }

    // ------------------------------------------------------------------
    // Zweiter Lauf: gleiche Anzahl, gleiche Zeiten (Stabilitätsanker)
    // ------------------------------------------------------------------
    @Test
    void zweiterLaufErzeugtKeineDoppeltenProjektbloecke() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Project project = makeProject(540L, "Podcast", 2, 60);
        project.setStartDate(monday);

        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(List.of(project));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult first = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));
        List<ScheduledItem> firstSessions = projectItems(first);
        assertEquals(2, firstSessions.size());

        // Der zweite Lauf sieht die Blöcke des ersten als "previousScheduledEvents".
        List<CalendarEvent> previous = new ArrayList<>();
        long id = 5400L;
        for (ScheduledItem s : firstSessions) {
            CalendarEvent ev = new CalendarEvent();
            ev.setId(id++);
            ev.setIsFixed(false);
            ev.setEventType(EventType.PROJECT);
            ev.setRelatedProject(project);
            ev.setStartTime(s.getStartTime());
            ev.setEndTime(s.getEndTime());
            previous.add(ev);
        }
        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any())).thenReturn(previous);

        ScheduleResult second = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));
        List<ScheduledItem> secondSessions = projectItems(second);

        assertEquals(2, secondSessions.size(), "der zweite Lauf darf nichts verdoppeln");
        assertEquals(
                firstSessions.stream().map(ScheduledItem::getStartTime).sorted().toList(),
                secondSessions.stream().map(ScheduledItem::getStartTime).sorted().toList(),
                "ohne Grund zu verschieben müssen die Blöcke liegen bleiben");
    }

    // ------------------------------------------------------------------
    // Opt-out: 0 oder null Sessions ergeben keine Blöcke
    // ------------------------------------------------------------------
    @Test
    void ohneWochenpensumEntstehtKeineProjektzeit() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Project keine = makeProject(550L, "Ideensammlung", 0, 60);
        Project unbestimmt = makeProject(551L, "Archiv", 3, 60);
        unbestimmt.setWeeklySessionCount(null);

        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any()))
                .thenReturn(List.of(keine, unbestimmt));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        ScheduleResult result = service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        assertTrue(projectItems(result).isEmpty(),
                "weeklySessionCount 0/null ist der Opt-out und darf nichts erzeugen");
    }

    // ------------------------------------------------------------------
    // Der Projektblock wird als CalendarEvent mit Projekt-FK gespeichert
    // ------------------------------------------------------------------
    @Test
    void projektblockWirdMitProjektFremdschluesselGespeichert() {
        LocalDate monday = TODAY.plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Project project = makeProject(560L, "Website", 1, 60);
        project.setStartDate(monday);

        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(List.of(project));
        when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());

        service.generateOptimalSchedule(1L, monday, monday.plusDays(6));

        ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository, atLeastOnce()).save(captor.capture());

        List<CalendarEvent> projectEvents = captor.getAllValues().stream()
                .filter(e -> e.getEventType() == EventType.PROJECT)
                .toList();
        assertEquals(1, projectEvents.size());
        CalendarEvent ev = projectEvents.get(0);
        assertEquals(project, ev.getRelatedProject(), "ohne FK findet der Detail-Screen den Block nicht");
        assertEquals("Website", ev.getTitle());
        assertFalse(ev.getIsFixed(), "vom Scheduler erzeugte Blöcke sind beweglich");
    }

    private List<ScheduledItem> projectItems(ScheduleResult result) {
        return result.getScheduledHabits().stream()
                .filter(i -> i.getProject() != null)
                .collect(Collectors.toList());
    }

    private Project makeProject(Long id, String name, Integer weekly, Integer durationMinutes) {
        Project p = new Project();
        p.setId(id);
        p.setName(name);
        p.setStatus(ProjectStatus.ACTIVE);
        p.setWeeklySessionCount(weekly);
        p.setSessionDurationMinutes(durationMinutes);
        return p;
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
