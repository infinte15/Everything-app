package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.ExerciseSetDTO;
import com.Finn.everything_app.dto.FinishWorkoutRequest;
import com.Finn.everything_app.dto.StartWorkoutRequest;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.mapper.WorkoutLogMapper;
import com.Finn.everything_app.mapper.WorkoutSessionMapper;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutLoggingServiceTest {

    @Mock WorkoutSessionRepository sessionRepository;
    @Mock ExerciseSetRepository setRepository;
    @Mock ExerciseRepository exerciseRepository;
    @Mock RoutineRepository routineRepository;
    @Mock UserRepository userRepository;
    @Mock WorkoutPlanService workoutPlanService;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    private WorkoutLoggingService service() {
        return new WorkoutLoggingService(
                sessionRepository, setRepository, exerciseRepository, routineRepository,
                userRepository, workoutPlanService, new WorkoutLogMapper(new WorkoutSessionMapper()),
                eventPublisher);
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Exercise exercise(long id, String name) {
        Exercise exercise = new Exercise();
        exercise.setId(id);
        exercise.setName(name);
        return exercise;
    }

    private WorkoutSession session(long id, long userId) {
        WorkoutSession session = new WorkoutSession();
        session.setId(id);
        session.setUser(user(userId));
        session.setStartTime(LocalDateTime.now().minusMinutes(45));
        session.setIsCompleted(false);
        return session;
    }

    private FinishWorkoutRequest finishRequest(long exerciseId, int sets) {
        FinishWorkoutRequest request = new FinishWorkoutRequest();
        FinishWorkoutRequest.LoggedExercise block = new FinishWorkoutRequest.LoggedExercise();
        block.setExerciseId(exerciseId);
        block.setOrderIndex(0);
        for (int i = 1; i <= sets; i++) {
            ExerciseSetDTO set = new ExerciseSetDTO();
            set.setSetNumber(i);
            set.setReps(10);
            set.setWeight(60.0);
            set.setIsCompleted(true);
            block.getSets().add(set);
        }
        request.setExercises(List.of(block));
        return request;
    }

    // Der Abschluss ersetzt die Sätze der Einheit vollständig - ein zweiter identischer
    // Aufruf (Netzwerk-Wiederholung) darf sie nicht verdoppeln.
    @Test
    void finishDeletesExistingSetsBeforeInserting() {
        WorkoutSession session = session(5L, 1L);
        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(session));
        when(exerciseRepository.findAllById(any())).thenReturn(List.of(exercise(10L, "Bankdrücken")));
        when(sessionRepository.save(any(WorkoutSession.class))).thenAnswer(i -> i.getArgument(0));

        service().finish(1L, 5L, finishRequest(10L, 3));

        var order = inOrder(setRepository);
        order.verify(setRepository).deleteByWorkoutSessionId(5L);
        ArgumentCaptor<List<ExerciseSet>> captor = ArgumentCaptor.captor();
        order.verify(setRepository).saveAll(captor.capture());
        assertEquals(3, captor.getValue().size());
        assertEquals(0, captor.getValue().get(0).getExerciseOrder());
        assertEquals(SetType.NORMAL, captor.getValue().get(0).getSetType());
    }

    // Sonst zählt eine Wiederholung des Abschlusses dasselbe Training mehrfach.
    @Test
    void finishIncrementsPlanCounterOnlyOnFirstCompletion() {
        WorkoutPlan plan = new WorkoutPlan();
        plan.setId(3L);

        WorkoutSession session = session(5L, 1L);
        session.setWorkoutPlan(plan);
        session.setIsCompleted(true); // bereits abgeschlossen

        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(session));
        when(exerciseRepository.findAllById(any())).thenReturn(List.of(exercise(10L, "Bankdrücken")));
        when(sessionRepository.save(any(WorkoutSession.class))).thenAnswer(i -> i.getArgument(0));

        service().finish(1L, 5L, finishRequest(10L, 2));

        verify(workoutPlanService, never()).incrementCompletedWorkouts(anyLong());
    }

    @Test
    void finishIncrementsPlanAndRoutineCountersOnFirstCompletion() {
        WorkoutPlan plan = new WorkoutPlan();
        plan.setId(3L);
        Routine routine = new Routine();
        routine.setId(9L);
        routine.setPerformCount(4);

        WorkoutSession session = session(5L, 1L);
        session.setWorkoutPlan(plan);
        session.setRoutine(routine);

        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(session));
        when(exerciseRepository.findAllById(any())).thenReturn(List.of(exercise(10L, "Bankdrücken")));
        when(sessionRepository.save(any(WorkoutSession.class))).thenAnswer(i -> i.getArgument(0));

        WorkoutSession saved = service().finish(1L, 5L, finishRequest(10L, 2));

        verify(workoutPlanService).incrementCompletedWorkouts(3L);
        assertEquals(5, routine.getPerformCount());
        assertNotNull(routine.getLastPerformedAt());
        assertTrue(saved.getIsCompleted());
        assertNotNull(saved.getCompletedAt());
        assertFalse(saved.getIsFlexible());
    }

    @Test
    void finishOfAnotherUsersSessionIsNotFound() {
        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service().finish(1L, 5L, finishRequest(10L, 1)));
    }

    // Ein eingeplanter Platzhalter wird beim Start festgenagelt, damit der Solver ihn
    // nicht mitten im Training verschiebt.
    @Test
    void startPinsScheduledPlaceholderAndNotifiesScheduler() {
        WorkoutSession placeholder = session(5L, 1L);
        placeholder.setIsFlexible(true);
        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(placeholder));
        when(sessionRepository.save(any(WorkoutSession.class))).thenAnswer(i -> i.getArgument(0));

        StartWorkoutRequest request = new StartWorkoutRequest();
        request.setSessionId(5L);

        var dto = service().start(1L, request);

        assertFalse(placeholder.getIsFlexible());
        assertEquals(5L, dto.getSessionId());
        verify(eventPublisher).publishEvent(any());
    }

    // Ein frisches Ad-hoc-Training beginnt jetzt und braucht keinen Solver-Lauf.
    @Test
    void startEmptyWorkoutDoesNotNotifyScheduler() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(sessionRepository.save(any(WorkoutSession.class))).thenAnswer(i -> {
            WorkoutSession s = i.getArgument(0);
            s.setId(77L);
            return s;
        });

        var dto = service().start(1L, new StartWorkoutRequest());

        assertEquals(77L, dto.getSessionId());
        assertEquals("Training", dto.getName());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void startFromRoutineFillsPlannedExercisesWithPreviousPerformance() {
        Exercise bench = exercise(10L, "Bankdrücken");
        bench.setDefaultRestSeconds(120);

        Routine routine = new Routine();
        routine.setId(9L);
        routine.setName("Push A");
        RoutineExercise re = new RoutineExercise();
        re.setId(31L);
        re.setExercise(bench);
        re.setOrderIndex(0);
        re.setTargetSets(4);
        routine.getExercises().add(re);

        WorkoutSession previousSession = new WorkoutSession();
        previousSession.setId(2L);
        previousSession.setStartTime(LocalDateTime.now().minusDays(3));
        ExerciseSet previousSet = new ExerciseSet();
        previousSet.setExercise(bench);
        previousSet.setWorkoutSession(previousSession);
        previousSet.setSetNumber(1);
        previousSet.setReps(8);
        previousSet.setWeight(80.0);
        previousSet.setIsCompleted(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(routineRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(routine));
        when(sessionRepository.save(any(WorkoutSession.class))).thenAnswer(i -> {
            WorkoutSession s = i.getArgument(0);
            s.setId(88L);
            return s;
        });
        when(setRepository.findCompletedSetsForExercises(List.of(10L), 1L))
                .thenReturn(List.of(previousSet));

        StartWorkoutRequest request = new StartWorkoutRequest();
        request.setRoutineId(9L);

        var dto = service().start(1L, request);

        assertEquals("Push A", dto.getName());
        assertEquals(1, dto.getPlannedExercises().size());
        var planned = dto.getPlannedExercises().get(0);
        assertEquals(4, planned.getTargetSets());
        assertEquals(120, planned.getRestSeconds(), "erbt die Pausenzeit der Übung");
        assertEquals(1, planned.getPrevious().size());
        assertEquals(80.0, planned.getPrevious().get(0).getWeight());
        assertEquals(80.0, planned.getPersonalRecordWeight());
    }

    // Die Abfrage liefert absteigend nach Startzeit - nur die jüngste Einheit ist "vorher".
    @Test
    void previousPerformanceUsesOnlyTheMostRecentSession() {
        Exercise bench = exercise(10L, "Bankdrücken");
        Routine routine = new Routine();
        routine.setId(9L);
        routine.setName("Push A");
        RoutineExercise re = new RoutineExercise();
        re.setExercise(bench);
        routine.getExercises().add(re);

        WorkoutSession recent = new WorkoutSession();
        recent.setId(2L);
        recent.setStartTime(LocalDateTime.now().minusDays(3));
        WorkoutSession older = new WorkoutSession();
        older.setId(1L);
        older.setStartTime(LocalDateTime.now().minusDays(10));

        ExerciseSet recentSet = new ExerciseSet();
        recentSet.setExercise(bench);
        recentSet.setWorkoutSession(recent);
        recentSet.setSetNumber(1);
        recentSet.setWeight(80.0);
        recentSet.setReps(8);
        recentSet.setIsCompleted(true);

        ExerciseSet olderSet = new ExerciseSet();
        olderSet.setExercise(bench);
        olderSet.setWorkoutSession(older);
        olderSet.setSetNumber(1);
        olderSet.setWeight(100.0);
        olderSet.setReps(3);
        olderSet.setIsCompleted(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(routineRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(routine));
        when(sessionRepository.save(any(WorkoutSession.class))).thenAnswer(i -> i.getArgument(0));
        when(setRepository.findCompletedSetsForExercises(List.of(10L), 1L))
                .thenReturn(List.of(recentSet, olderSet));

        StartWorkoutRequest request = new StartWorkoutRequest();
        request.setRoutineId(9L);

        var planned = service().start(1L, request).getPlannedExercises().get(0);

        assertEquals(1, planned.getPrevious().size());
        assertEquals(80.0, planned.getPrevious().get(0).getWeight());
        // Der Rekord berücksichtigt dagegen die gesamte Historie.
        assertEquals(100.0, planned.getPersonalRecordWeight());
    }

    @Test
    void personalRecordsAggregateAcrossHistory() {
        Exercise bench = exercise(10L, "Bankdrücken");
        WorkoutSession first = new WorkoutSession();
        first.setId(1L);
        first.setStartTime(LocalDateTime.of(2026, 1, 5, 10, 0));
        WorkoutSession second = new WorkoutSession();
        second.setId(2L);
        second.setStartTime(LocalDateTime.of(2026, 3, 5, 10, 0));

        ExerciseSet light = new ExerciseSet();
        light.setExercise(bench);
        light.setWorkoutSession(first);
        light.setWeight(60.0);
        light.setReps(12);
        light.setIsCompleted(true);

        ExerciseSet heavy = new ExerciseSet();
        heavy.setExercise(bench);
        heavy.setWorkoutSession(second);
        heavy.setWeight(100.0);
        heavy.setReps(3);
        heavy.setIsCompleted(true);

        when(exerciseRepository.findById(10L)).thenReturn(Optional.of(bench));
        when(setRepository.findCompletedSetsForExercises(List.of(10L), 1L))
                .thenReturn(List.of(light, heavy));

        var records = service().getPersonalRecords(1L, 10L);

        assertEquals(100.0, records.getMaxWeight());
        assertEquals(3, records.getMaxWeightReps());
        assertEquals(12, records.getMaxReps());
        assertEquals(720.0, records.getMaxSetVolumeKg(), 0.001, "60 kg x 12 schlägt 100 kg x 3");
        assertEquals(2, records.getTotalSetsAllTime());
        assertEquals(first.getStartTime(), records.getFirstPerformedAt());
        assertEquals(second.getStartTime(), records.getLastPerformedAt());
        // Epley: 60 * (1 + 12/30) = 84 gegen 100 * (1 + 3/30) = 110
        assertEquals(110.0, records.getBest1RM(), 0.001);
    }
}
