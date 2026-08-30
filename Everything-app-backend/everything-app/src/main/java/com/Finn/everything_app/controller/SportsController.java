package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.mapper.*;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.projection.SessionAggregateRow;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
    private final WorkoutLoggingService loggingService;
    private final ExerciseNoteService exerciseNoteService;

    private final WorkoutPlanMapper planMapper;
    private final WorkoutSessionMapper sessionMapper;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseSetMapper setMapper;
    private final WorkoutLogMapper logMapper;

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
        return ResponseEntity.ok(withAggregates(sessionService.getUserSessions(userId)));
    }

    /**
     * Haengt Satzanzahl und Volumen an eine Liste von Einheiten - mit genau einer zusaetzlichen
     * Abfrage statt einer pro Einheit.
     */
    private List<WorkoutSessionDTO> withAggregates(List<WorkoutSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Long> ids = sessions.stream().map(WorkoutSession::getId).toList();
        Map<Long, SessionAggregateRow> byId = setService.aggregateBySessionIds(ids);

        return sessions.stream()
                .map(s -> sessionMapper.toDTO(s, byId.get(s.getId())))
                .collect(Collectors.toList());
    }


    @GetMapping("/sessions/plan/{planId}")
    public ResponseEntity<List<WorkoutSessionDTO>> getSessionsByPlan(@PathVariable Long planId) {
        List<WorkoutSession> sessions = sessionService.getSessionsByPlan(planId);
        return ResponseEntity.ok(
                sessions.stream().map(sessionMapper::toDTO).collect(Collectors.toList())
        );
    }


    /**
     * Liefert die Einheit inklusive aller protokollierten Übungen und Sätze. Zwingend
     * user-gebunden - hier hängt die komplette Trainingshistorie dran.
     */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<WorkoutSessionDetailDTO> getSessionById(
            @CurrentUser Long userId,
            @PathVariable Long id) {

        WorkoutSession session = loggingService.getSessionDetail(userId, id);
        return ResponseEntity.ok(logMapper.toDetail(session));
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


    /**
     * Paginierte Katalog-Suche. Ohne Parameter liefert der Endpunkt die erste Seite
     * alphabetisch - der Katalog hat ~870 Einträge, eine ungefilterte Vollausgabe wäre
     * für den Client unbrauchbar.
     */
    @GetMapping("/exercises")
    public ResponseEntity<PagedResponse<ExerciseDTO>> searchExercises(
            @CurrentUser Long userId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String muscle,
            @RequestParam(required = false) String equipment,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        return ResponseEntity.ok(PagedResponse.of(
                exerciseService.searchExercises(
                        userId, search, muscle, equipment, category, difficulty, page, size),
                exerciseMapper::toDTO
        ));
    }


    @GetMapping("/exercises/muscles")
    public ResponseEntity<List<MuscleGroupDTO>> getMuscleGroups() {
        return ResponseEntity.ok(
                java.util.Arrays.stream(MuscleGroup.values())
                        .map(m -> new MuscleGroupDTO(m.getSlug(), m.getLabel()))
                        .collect(Collectors.toList())
        );
    }


    @GetMapping("/exercises/filters")
    public ResponseEntity<ExerciseFiltersDTO> getExerciseFilters() {
        return ResponseEntity.ok(new ExerciseFiltersDTO(
                exerciseService.getEquipmentValues(),
                exerciseService.getCategoryValues(),
                exerciseService.getDifficultyValues()
        ));
    }


    @GetMapping("/exercises/{id}")
    public ResponseEntity<ExerciseDTO> getExerciseById(@PathVariable Long id) {
        return ResponseEntity.ok(exerciseMapper.toDTO(exerciseService.getExerciseById(id)));
    }


    @GetMapping("/exercises/{id}/history")
    public ResponseEntity<List<ExerciseHistoryEntryDTO>> getExerciseHistory(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "20") int limit) {

        return ResponseEntity.ok(loggingService.getExerciseHistory(userId, id, limit));
    }


    /**
     * Stehende Notiz zur Uebung - erscheint bei jedem Training, unabhaengig von der Routine.
     */
    @GetMapping("/exercises/{id}/note")
    public ResponseEntity<ExerciseNoteDTO> getExerciseNote(
            @CurrentUser Long userId, @PathVariable Long id) {

        return ResponseEntity.ok(exerciseNoteService.get(userId, id));
    }

    /** Leerer Text loescht die Notiz. */
    @PutMapping("/exercises/{id}/note")
    public ResponseEntity<ExerciseNoteDTO> saveExerciseNote(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ExerciseNoteDTO request) {

        return ResponseEntity.ok(exerciseNoteService.save(userId, id, request.text()));
    }

    @GetMapping("/exercises/{id}/records")
    public ResponseEntity<PersonalRecordDTO> getPersonalRecords(
            @CurrentUser Long userId,
            @PathVariable Long id) {

        return ResponseEntity.ok(loggingService.getPersonalRecords(userId, id));
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
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ExerciseDTO exerciseDTO) {

        Exercise exercise = exerciseMapper.toEntity(exerciseDTO);
        Exercise updated = exerciseService.updateExercise(userId, id, exercise);

        return ResponseEntity.ok(exerciseMapper.toDTO(updated));
    }


    @DeleteMapping("/exercises/{id}")
    public ResponseEntity<Void> deleteExercise(@CurrentUser Long userId, @PathVariable Long id) {
        exerciseService.deleteExercise(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ==================== EXERCISE SETS ====================


    @GetMapping("/sets/session/{sessionId}")
    public ResponseEntity<List<ExerciseSetDTO>> getSetsBySession(
            @CurrentUser Long userId,
            @PathVariable Long sessionId) {

        List<ExerciseSet> sets = setService.getSetsBySessionForUser(sessionId, userId);
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

}