package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.RoutineExerciseDTO;
import com.Finn.everything_app.dto.RoutineUpsertRequest;
import com.Finn.everything_app.event.ScheduleChangedEvent;
import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoutineService {

    private final RoutineRepository routineRepository;
    private final RoutineExerciseRepository routineExerciseRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final UserRepository userRepository;
    private final RoutineHabitService routineHabitService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<Routine> getUserRoutines(Long userId, Long planId, boolean includeArchived) {
        if (planId != null) {
            return routineRepository.findByUserIdAndWorkoutPlanIdOrderByOrderIndexAscIdAsc(userId, planId);
        }
        return includeArchived
                ? routineRepository.findByUserIdOrderByOrderIndexAscIdAsc(userId)
                : routineRepository.findByUserIdAndIsArchivedFalseOrderByOrderIndexAscIdAsc(userId);
    }

    @Transactional(readOnly = true)
    public Routine getRoutine(Long userId, Long routineId) {
        return routineRepository.findByIdAndUserId(routineId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Routine nicht gefunden"));
    }

    @Transactional
    public Routine createRoutine(Long userId, RoutineUpsertRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        Routine routine = new Routine();
        routine.setUser(user);
        routine.setOrderIndex(routineRepository.findMaxOrderIndex(userId) + 1);
        applyFields(routine, request, userId);
        replaceExercises(routine, request.getExercises());

        Routine saved = routineRepository.save(routine);
        routineHabitService.sync(saved);
        // Der Scheduler rotiert die Routinen eines Plans in die Wochen-Platzhalter ein.
        if (saved.getWorkoutPlan() != null) {
            eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        }
        return saved;
    }

    @Transactional
    public Routine updateRoutine(Long userId, Long routineId, RoutineUpsertRequest request) {
        Routine routine = getRoutine(userId, routineId);
        boolean planBefore = routine.getWorkoutPlan() != null;

        applyFields(routine, request, userId);
        replaceExercises(routine, request.getExercises());

        Routine saved = routineRepository.save(routine);
        routineHabitService.sync(saved);
        if (planBefore || saved.getWorkoutPlan() != null) {
            eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        }
        return saved;
    }

    @Transactional
    public void deleteRoutine(Long userId, Long routineId) {
        Routine routine = getRoutine(userId, routineId);
        boolean hadPlan = routine.getWorkoutPlan() != null;

        // Bereits trainierte Einheiten behalten ihre Historie, verlieren aber den Bezug.
        routineExerciseRepository.detachSessionsFromRoutine(routineId);
        // Anders als beim blossen Abwaehlen des Wochentags verschwindet die Routine hier ganz;
        // eine Gewohnheit, die auf nichts mehr zeigt, waere ein Fremdkoerper im Habit-Space.
        routineHabitService.remove(routineId);
        routineRepository.delete(routine);

        if (hadPlan) {
            eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        }
    }

    @Transactional
    public void reorderRoutines(Long userId, List<Long> routineIds) {
        if (routineIds == null || routineIds.isEmpty()) {
            return;
        }
        for (int i = 0; i < routineIds.size(); i++) {
            Routine routine = getRoutine(userId, routineIds.get(i));
            routine.setOrderIndex(i);
            routineRepository.save(routine);
        }
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
    }

    private void applyFields(Routine routine, RoutineUpsertRequest request, Long userId) {
        routine.setName(request.getName());
        routine.setDescription(request.getDescription());
        routine.setImageUrl(request.getImageUrl());
        routine.setColorHex(request.getColorHex());
        routine.setDayLabel(request.getDayLabel());
        routine.setPreferredWeekday(request.getPreferredWeekday());
        routine.setEstimatedDurationMinutes(request.getEstimatedDurationMinutes());
        if (request.getIsArchived() != null) {
            routine.setIsArchived(request.getIsArchived());
        }

        if (request.getWorkoutPlanId() == null) {
            routine.setWorkoutPlan(null);
            return;
        }
        // Ohne diese Pruefung koennte man die eigene Routine an einen fremden Plan haengen.
        WorkoutPlan plan = workoutPlanRepository.findById(request.getWorkoutPlanId())
                .filter(p -> p.getUser() != null && p.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Trainingsplan nicht gefunden"));
        routine.setWorkoutPlan(plan);
    }

    /**
     * Ersetzt die Uebungsliste vollstaendig. {@code orphanRemoval} raeumt die alten Zeilen ab,
     * die Reihenfolge kommt ausschliesslich aus der Listenposition.
     */
    private void replaceExercises(Routine routine, List<RoutineExerciseDTO> requested) {
        List<RoutineExerciseDTO> items = requested != null ? requested : List.of();

        Set<Long> exerciseIds = items.stream()
                .map(RoutineExerciseDTO::getExerciseId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, Exercise> byId = exerciseRepository.findAllById(exerciseIds).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));

        List<RoutineExercise> rebuilt = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            RoutineExerciseDTO dto = items.get(i);
            Exercise exercise = byId.get(dto.getExerciseId());
            if (exercise == null) {
                throw new BadRequestException("Übung nicht gefunden: " + dto.getExerciseId());
            }

            RoutineExercise re = new RoutineExercise();
            re.setRoutine(routine);
            re.setExercise(exercise);
            re.setOrderIndex(i);
            re.setTargetSets(dto.getTargetSets() != null ? dto.getTargetSets() : 3);
            re.setTargetRepsMin(dto.getTargetRepsMin());
            re.setTargetRepsMax(dto.getTargetRepsMax());
            re.setTargetWeight(dto.getTargetWeight());
            re.setTargetDurationSeconds(dto.getTargetDurationSeconds());
            re.setRestSeconds(dto.getRestSeconds() != null
                    ? dto.getRestSeconds()
                    : exercise.getDefaultRestSeconds());
            re.setNotes(dto.getNotes());
            re.setSupersetGroup(dto.getSupersetGroup());
            re.setProgressionPolicy(dto.getProgressionPolicy());
            re.setIncrementKg(dto.getIncrementKg());
            re.setIsBodyweight(dto.getIsBodyweight());
            rebuilt.add(re);
        }

        routine.getExercises().clear();
        routine.getExercises().addAll(rebuilt);
    }
}
