package com.Finn.everything_app.service;

import com.Finn.everything_app.model.Routine;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.model.WorkoutPlan;
import com.Finn.everything_app.model.WorkoutSession;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Deckt die Platzhalter-Erzeugung ab, an der der CP-SAT-Scheduler haengt. */
@ExtendWith(MockitoExtension.class)
class WorkoutPlanServiceTest {

    @Mock WorkoutPlanRepository workoutPlanRepository;
    @Mock WorkoutSessionRepository workoutSessionRepository;
    @Mock RoutineRepository routineRepository;
    @Mock UserRepository userRepository;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    WorkoutPlanService service;

    private static final LocalDate WEEK = LocalDate.of(2026, 8, 3); // Montag

    private WorkoutPlan plan(int perWeek) {
        WorkoutPlan plan = new WorkoutPlan();
        plan.setId(3L);
        plan.setName("Hypertrophie");
        plan.setWorkoutsPerWeek(perWeek);
        return plan;
    }

    private Routine routine(long id, String name, Integer minutes) {
        Routine routine = new Routine();
        routine.setId(id);
        routine.setName(name);
        routine.setEstimatedDurationMinutes(minutes);
        return routine;
    }

    private void stubUser() {
        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    private List<WorkoutSession> capturePlaceholders(int expected) {
        ArgumentCaptor<WorkoutSession> captor = ArgumentCaptor.forClass(WorkoutSession.class);
        verify(workoutSessionRepository, times(expected)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void placeholdersRotateThroughTheRoutinesOfThePlan() {
        stubUser();
        when(workoutSessionRepository.countByWorkoutPlanIdAndTargetWeekStart(3L, WEEK)).thenReturn(0L);
        when(workoutSessionRepository.countByWorkoutPlanIdAndIsFlexibleAndStartTimeBetween(
                anyLong(), any(), any(), any())).thenReturn(0L);
        when(routineRepository.findByWorkoutPlanIdAndIsArchivedFalseOrderByOrderIndexAscIdAsc(3L))
                .thenReturn(List.of(
                        routine(10L, "Push", 55),
                        routine(11L, "Pull", 60),
                        routine(12L, "Legs", null)));

        service.generateWeeklyPlaceholders(1L, plan(3), WEEK);

        List<WorkoutSession> created = capturePlaceholders(3);
        assertEquals(List.of("Push", "Pull", "Legs"),
                created.stream().map(WorkoutSession::getName).toList());
        assertEquals(55, created.get(0).getDurationMinutes());
        assertEquals(60, created.get(1).getDurationMinutes());
        assertEquals(45, created.get(2).getDurationMinutes(), "ohne eigene Dauer greift der Standard");
        assertEquals(10L, created.get(0).getRoutine().getId());
        created.forEach(s -> {
            assertTrue(s.getIsFlexible());
            assertEquals(WEEK, s.getTargetWeekStart());
        });
    }

    // Eine teilweise gefuellte Woche setzt die Rotation fort, statt wieder bei Push zu beginnen.
    @Test
    void rotationContinuesWhereThePartiallyFilledWeekStopped() {
        stubUser();
        when(workoutSessionRepository.countByWorkoutPlanIdAndTargetWeekStart(3L, WEEK)).thenReturn(1L);
        when(workoutSessionRepository.countByWorkoutPlanIdAndIsFlexibleAndStartTimeBetween(
                anyLong(), any(), any(), any())).thenReturn(0L);
        when(routineRepository.findByWorkoutPlanIdAndIsArchivedFalseOrderByOrderIndexAscIdAsc(3L))
                .thenReturn(List.of(routine(10L, "Push", 55), routine(11L, "Pull", 60)));

        service.generateWeeklyPlaceholders(1L, plan(3), WEEK);

        assertEquals(List.of("Pull", "Push"),
                capturePlaceholders(2).stream().map(WorkoutSession::getName).toList());
    }

    // Ohne Routinen bleibt das alte Verhalten unveraendert.
    @Test
    void planWithoutRoutinesKeepsTheGenericPlaceholderName() {
        stubUser();
        when(workoutSessionRepository.countByWorkoutPlanIdAndTargetWeekStart(3L, WEEK)).thenReturn(0L);
        when(workoutSessionRepository.countByWorkoutPlanIdAndIsFlexibleAndStartTimeBetween(
                anyLong(), any(), any(), any())).thenReturn(0L);
        when(routineRepository.findByWorkoutPlanIdAndIsArchivedFalseOrderByOrderIndexAscIdAsc(3L))
                .thenReturn(List.of());

        service.generateWeeklyPlaceholders(1L, plan(2), WEEK);

        List<WorkoutSession> created = capturePlaceholders(2);
        created.forEach(s -> {
            assertEquals("Hypertrophie Session", s.getName());
            assertEquals(45, s.getDurationMinutes());
            assertNull(s.getRoutine());
        });
    }

    @Test
    void weeklyTargetAlreadyMetCreatesNothing() {
        when(workoutSessionRepository.countByWorkoutPlanIdAndTargetWeekStart(3L, WEEK)).thenReturn(2L);
        when(workoutSessionRepository.countByWorkoutPlanIdAndIsFlexibleAndStartTimeBetween(
                anyLong(), any(), any(), any())).thenReturn(1L);

        service.generateWeeklyPlaceholders(1L, plan(3), WEEK);

        verify(workoutSessionRepository, never()).save(any());
    }

    // Routinen sind wiederverwendbar: das Loeschen des Programms darf sie nicht mitnehmen.
    @Test
    void deletePlanDetachesRoutinesBeforeDeleting() {
        WorkoutPlan plan = plan(3);
        User user = new User();
        user.setId(1L);
        plan.setUser(user);
        when(workoutPlanRepository.findById(3L)).thenReturn(Optional.of(plan));

        service.deletePlan(3L);

        var order = inOrder(routineRepository, workoutPlanRepository);
        order.verify(routineRepository).detachFromPlan(3L);
        order.verify(workoutPlanRepository).delete(plan);
    }
}
