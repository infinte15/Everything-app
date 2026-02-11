package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.mapper.*;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sports")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SportsController {

    private final WorkoutPlanService planService;
    private final WorkoutSessionService sessionService;
    private final ExerciseService exerciseService;
    private final ExerciseSetService setService;

    private final WorkoutPlanMapper planMapper;
    private final WorkoutSessionMapper sessionMapper;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseSetMapper setMapper;

    // ==================== WORKOUT PLANS ====================


    @GetMapping("/plans")
    public ResponseEntity<List<WorkoutPlanDTO>> getAllPlans(@CurrentUser Long userId) {
        List<WorkoutPlan> plans = planService.getUserPlans(userId);
        return ResponseEntity.ok(
                plans.stream().map(planMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/plans/active")
    public ResponseEntity<WorkoutPlanDTO> getActivePlan(@CurrentUser Long userId) {
        WorkoutPlan plan = planService.getActivePlan(userId);
        return ResponseEntity.ok(planMapper.toDTO(plan));
    }


    @PostMapping("/plans")
    public ResponseEntity<WorkoutPlanDTO> createPlan(
            @CurrentUser Long userId,
            @Valid @RequestBody WorkoutPlanDTO planDTO) {

        WorkoutPlan plan = planMapper.toEntity(planDTO);
        WorkoutPlan created = planService.createPlan(userId, plan);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                planMapper.toDTO(created)
        );
    }


    @PutMapping("/plans/{id}")
    public ResponseEntity<WorkoutPlanDTO> updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody WorkoutPlanDTO planDTO) {

        WorkoutPlan plan = planMapper.toEntity(planDTO);
        WorkoutPlan updated = planService.updatePlan(id, plan);

        return ResponseEntity.ok(planMapper.toDTO(updated));
    }


    @PutMapping("/plans/{id}/activate")
    public ResponseEntity<WorkoutPlanDTO> activatePlan(
            @CurrentUser Long userId,
            @PathVariable Long id) {

        WorkoutPlan activated = planService.activatePlan(userId, id);
        return ResponseEntity.ok(planMapper.toDTO(activated));
    }


    @DeleteMapping("/plans/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable Long id) {
        planService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== WORKOUT SESSIONS ====================


    @GetMapping("/sessions")
    public ResponseEntity<List<WorkoutSessionDTO>> getAllSessions(@CurrentUser Long userId) {
        List<WorkoutSession> sessions = sessionService.getUserSessions(userId);
        return ResponseEntity.ok(
                sessions.stream().map(sessionMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/sessions/plan/{planId}")
    public ResponseEntity<List<WorkoutSessionDTO>> getSessionsByPlan(@PathVariable Long planId) {
        List<WorkoutSession> sessions = sessionService.getSessionsByPlan(planId);
        return ResponseEntity.ok(
                sessions.stream().map(sessionMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/sessions/date-range")
    public ResponseEntity<List<WorkoutSessionDTO>> getSessionsByDateRange(
            @CurrentUser Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        List<WorkoutSession> sessions = sessionService.getSessionsInDateRange(userId, start, end);
        return ResponseEntity.ok(
                sessions.stream().map(sessionMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/sessions/{id}")
    public ResponseEntity<WorkoutSessionDTO> getSessionById(@PathVariable Long id) {
        WorkoutSession session = sessionService.getSessionById(id);
        return ResponseEntity.ok(sessionMapper.toDTO(session));
    }


    @PostMapping("/sessions")
    public ResponseEntity<WorkoutSessionDTO> createSession(
            @CurrentUser Long userId,
            @Valid @RequestBody WorkoutSessionDTO sessionDTO) {

        WorkoutSession session = sessionMapper.toEntity(sessionDTO);
        WorkoutSession created = sessionService.createSession(userId, session, sessionDTO.getWorkoutPlanId());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                sessionMapper.toDTO(created)
        );
    }


    @PutMapping("/sessions/{id}")
    public ResponseEntity<WorkoutSessionDTO> updateSession(
            @PathVariable Long id,
            @Valid @RequestBody WorkoutSessionDTO sessionDTO) {

        WorkoutSession session = sessionMapper.toEntity(sessionDTO);
        WorkoutSession updated = sessionService.updateSession(id, session);

        return ResponseEntity.ok(sessionMapper.toDTO(updated));
    }


    @PutMapping("/sessions/{id}/complete")
    public ResponseEntity<WorkoutSessionDTO> completeSession(@PathVariable Long id) {
        WorkoutSession completed = sessionService.completeSession(id);
        return ResponseEntity.ok(sessionMapper.toDTO(completed));
    }


    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== EXERCISES ====================


    @GetMapping("/exercises")
    public ResponseEntity<List<ExerciseDTO>> getAllExercises() {
        List<Exercise> exercises = exerciseService.getAllExercises();
        return ResponseEntity.ok(
                exercises.stream().map(exerciseMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/exercises/muscle/{muscleGroup}")
    public ResponseEntity<List<ExerciseDTO>> getExercisesByMuscleGroup(@PathVariable String muscleGroup) {
        List<Exercise> exercises = exerciseService.getExercisesByMuscleGroup(muscleGroup);
        return ResponseEntity.ok(
                exercises.stream().map(exerciseMapper::toDTO).collect(Collectors.toList())
        );
    }


    @GetMapping("/exercises/equipment/{equipment}")
    public ResponseEntity<List<ExerciseDTO>> getExercisesByEquipment(@PathVariable String equipment) {
        List<Exercise> exercises = exerciseService.getExercisesByEquipment(equipment);
        return ResponseEntity.ok(
                exercises.stream().map(exerciseMapper::toDTO).collect(Collectors.toList())
        );
    }


    @PostMapping("/exercises")
    public ResponseEntity<ExerciseDTO> createExercise(
            @CurrentUser Long userId,
            @Valid @RequestBody ExerciseDTO exerciseDTO) {

        Exercise exercise = exerciseMapper.toEntity(exerciseDTO);
        Exercise created = exerciseService.createExercise(userId, exercise);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                exerciseMapper.toDTO(created)
        );
    }


    @PutMapping("/exercises/{id}")
    public ResponseEntity<ExerciseDTO> updateExercise(
            @PathVariable Long id,
            @Valid @RequestBody ExerciseDTO exerciseDTO) {

        Exercise exercise = exerciseMapper.toEntity(exerciseDTO);
        Exercise updated = exerciseService.updateExercise(id, exercise);

        return ResponseEntity.ok(exerciseMapper.toDTO(updated));
    }


    @DeleteMapping("/exercises/{id}")
    public ResponseEntity<Void> deleteExercise(@PathVariable Long id) {
        exerciseService.deleteExercise(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== EXERCISE SETS ====================


    @GetMapping("/sets/session/{sessionId}")
    public ResponseEntity<List<ExerciseSetDTO>> getSetsBySession(@PathVariable Long sessionId) {
        List<ExerciseSet> sets = setService.getSetsBySession(sessionId);
        return ResponseEntity.ok(
                sets.stream().map(setMapper::toDTO).collect(Collectors.toList())
        );
    }


    @PostMapping("/sets")
    public ResponseEntity<ExerciseSetDTO> createSet(
            @Valid @RequestBody ExerciseSetDTO setDTO) {

        ExerciseSet set = setMapper.toEntity(setDTO);
        ExerciseSet created = setService.createSet(
                set,
                setDTO.getExerciseId(),
                setDTO.getWorkoutSessionId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                setMapper.toDTO(created)
        );
    }

    @PutMapping("/sets/{id}")
    public ResponseEntity<ExerciseSetDTO> updateSet(
            @PathVariable Long id,
            @Valid @RequestBody ExerciseSetDTO setDTO) {

        ExerciseSet set = setMapper.toEntity(setDTO);
        ExerciseSet updated = setService.updateSet(id, set);

        return ResponseEntity.ok(setMapper.toDTO(updated));
    }


    @DeleteMapping("/sets/{id}")
    public ResponseEntity<Void> deleteSet(@PathVariable Long id) {
        setService.deleteSet(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== STATISTICS ====================


    @GetMapping("/stats/progress")
    public ResponseEntity<WorkoutProgressDTO> getProgress(
            @CurrentUser Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {


        WorkoutProgressDTO progress = sessionService.calculateProgress(
                userId,
                startDate != null ? LocalDate.parse(startDate) : null,
                endDate != null ? LocalDate.parse(endDate) : null
        );

        return ResponseEntity.ok(progress);
    }
}