package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.RoutineExerciseDTO;
import com.Finn.everything_app.dto.RoutineUpsertRequest;
import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutineServiceTest {

    @Mock RoutineRepository routineRepository;
    @Mock RoutineExerciseRepository routineExerciseRepository;
    @Mock ExerciseRepository exerciseRepository;
    @Mock WorkoutPlanRepository workoutPlanRepository;
    @Mock UserRepository userRepository;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    RoutineService service;

    private Exercise exercise(long id, String name, Integer defaultRest) {
        Exercise exercise = new Exercise();
        exercise.setId(id);
        exercise.setName(name);
        exercise.setDefaultRestSeconds(defaultRest);
        return exercise;
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    // Fremde Routinen duerfen nicht einmal als "existiert" erkennbar sein - deshalb 404.
    @Test
    void getRoutineOfAnotherUserIsNotFound() {
        when(routineRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getRoutine(1L, 7L));
    }

    @Test
    void createRoutineNumbersExercisesByListPosition() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(routineRepository.findMaxOrderIndex(1L)).thenReturn(2);
        when(exerciseRepository.findAllById(any()))
                .thenReturn(List.of(exercise(10L, "Bankdrücken", 120), exercise(11L, "Fliegende", 60)));
        when(routineRepository.save(any(Routine.class))).thenAnswer(i -> i.getArgument(0));

        RoutineUpsertRequest request = new RoutineUpsertRequest();
        request.setName("Push A");
        RoutineExerciseDTO first = new RoutineExerciseDTO();
        first.setExerciseId(11L);
        first.setOrderIndex(99); // vom Server ignoriert
        RoutineExerciseDTO second = new RoutineExerciseDTO();
        second.setExerciseId(10L);
        second.setTargetSets(5);
        second.setRestSeconds(180);
        request.setExercises(List.of(first, second));

        Routine saved = service.createRoutine(1L, request);

        assertEquals(3, saved.getOrderIndex(), "neue Routine wird hinten angehängt");
        assertEquals(2, saved.getExercises().size());
        assertEquals(0, saved.getExercises().get(0).getOrderIndex());
        assertEquals(11L, saved.getExercises().get(0).getExercise().getId());
        assertEquals(1, saved.getExercises().get(1).getOrderIndex());
        assertEquals(5, saved.getExercises().get(1).getTargetSets());
        assertEquals(180, saved.getExercises().get(1).getRestSeconds());
        // Ohne eigene Angabe erbt die Zeile die Pausenzeit der Übung.
        assertEquals(60, saved.getExercises().get(0).getRestSeconds());
        // Ohne Plan gibt es keinen Grund, den Scheduler zu wecken.
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createRoutineDefaultsTargetSetsToThree() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(routineRepository.findMaxOrderIndex(1L)).thenReturn(-1);
        when(exerciseRepository.findAllById(any())).thenReturn(List.of(exercise(10L, "Kniebeuge", null)));
        when(routineRepository.save(any(Routine.class))).thenAnswer(i -> i.getArgument(0));

        RoutineUpsertRequest request = new RoutineUpsertRequest();
        request.setName("Legs");
        RoutineExerciseDTO item = new RoutineExerciseDTO();
        item.setExerciseId(10L);
        request.setExercises(List.of(item));

        Routine saved = service.createRoutine(1L, request);

        assertEquals(3, saved.getExercises().get(0).getTargetSets());
        assertEquals(0, saved.getOrderIndex());
    }

    @Test
    void createRoutineRejectsUnknownExercise() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(routineRepository.findMaxOrderIndex(1L)).thenReturn(0);
        when(exerciseRepository.findAllById(any())).thenReturn(List.of());

        RoutineUpsertRequest request = new RoutineUpsertRequest();
        request.setName("Push A");
        RoutineExerciseDTO item = new RoutineExerciseDTO();
        item.setExerciseId(404L);
        request.setExercises(List.of(item));

        assertThrows(BadRequestException.class, () -> service.createRoutine(1L, request));
    }

    // Ohne diese Prüfung könnte man die eigene Routine an einen fremden Plan hängen.
    @Test
    void createRoutineRejectsForeignWorkoutPlan() {
        WorkoutPlan foreign = new WorkoutPlan();
        foreign.setId(50L);
        foreign.setUser(user(999L));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(routineRepository.findMaxOrderIndex(1L)).thenReturn(0);
        when(workoutPlanRepository.findById(50L)).thenReturn(Optional.of(foreign));

        RoutineUpsertRequest request = new RoutineUpsertRequest();
        request.setName("Push A");
        request.setWorkoutPlanId(50L);

        assertThrows(ResourceNotFoundException.class, () -> service.createRoutine(1L, request));
    }

    @Test
    void updateReplacesExerciseListCompletely() {
        Routine existing = new Routine();
        existing.setId(5L);
        existing.setUser(user(1L));
        RoutineExercise old = new RoutineExercise();
        old.setExercise(exercise(10L, "Altübung", null));
        old.setOrderIndex(0);
        existing.getExercises().add(old);

        when(routineRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(existing));
        when(exerciseRepository.findAllById(any())).thenReturn(List.of(exercise(20L, "Neuübung", 90)));
        when(routineRepository.save(any(Routine.class))).thenAnswer(i -> i.getArgument(0));

        RoutineUpsertRequest request = new RoutineUpsertRequest();
        request.setName("Push A");
        RoutineExerciseDTO item = new RoutineExerciseDTO();
        item.setExerciseId(20L);
        request.setExercises(List.of(item));

        Routine saved = service.updateRoutine(1L, 5L, request);

        assertEquals(1, saved.getExercises().size());
        assertEquals(20L, saved.getExercises().get(0).getExercise().getId());
    }

    // Trainierte Einheiten behalten ihre Historie, verlieren aber den Routinen-Bezug.
    @Test
    void deleteRoutineDetachesSessionsFirst() {
        Routine routine = new Routine();
        routine.setId(5L);
        routine.setUser(user(1L));
        when(routineRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(routine));

        service.deleteRoutine(1L, 5L);

        var order = inOrder(routineExerciseRepository, routineRepository);
        order.verify(routineExerciseRepository).detachSessionsFromRoutine(5L);
        order.verify(routineRepository).delete(routine);
    }

    @Test
    void reorderAssignsIndexByPosition() {
        Routine a = new Routine();
        a.setId(1L);
        a.setUser(user(1L));
        Routine b = new Routine();
        b.setId(2L);
        b.setUser(user(1L));
        when(routineRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(b));
        when(routineRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(a));

        service.reorderRoutines(1L, List.of(2L, 1L));

        ArgumentCaptor<Routine> captor = ArgumentCaptor.forClass(Routine.class);
        verify(routineRepository, times(2)).save(captor.capture());
        assertEquals(0, b.getOrderIndex());
        assertEquals(1, a.getOrderIndex());
    }
}
