package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.ProgressionSuggestionDTO;
import com.Finn.everything_app.dto.ReorderRequest;
import com.Finn.everything_app.dto.RoutineDetailDTO;
import com.Finn.everything_app.dto.RoutineSummaryDTO;
import com.Finn.everything_app.dto.RoutineUpsertRequest;
import com.Finn.everything_app.mapper.RoutineMapper;
import com.Finn.everything_app.model.Routine;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.ProgressionService;
import com.Finn.everything_app.service.RoutineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Trainings-Routinen. Bewusst ein eigener Controller - SportsController ist bereits
 * gross genug.
 */
@RestController
@RequestMapping("/api/sports/routines")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoutineController {

    private final RoutineService routineService;
    private final RoutineMapper routineMapper;
    private final ProgressionService progressionService;

    @GetMapping
    public ResponseEntity<List<RoutineSummaryDTO>> getRoutines(
            @CurrentUser Long userId,
            @RequestParam(required = false) Long planId,
            @RequestParam(defaultValue = "false") boolean includeArchived) {

        List<Routine> routines = routineService.getUserRoutines(userId, planId, includeArchived);
        return ResponseEntity.ok(routines.stream().map(routineMapper::toSummary).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoutineDetailDTO> getRoutine(
            @CurrentUser Long userId,
            @PathVariable Long id) {

        return ResponseEntity.ok(routineMapper.toDetail(routineService.getRoutine(userId, id)));
    }

    /**
     * Was beim naechsten Mal ansteht - je Zeile der Routine.
     *
     * <p>Eigener Endpunkt und nicht Teil von {@code getRoutine}: die Ableitung liest die
     * Trainingshistorie, und die braucht nicht jeder, der nur die Routine anzeigen will.
     */
    @GetMapping("/{id}/progression")
    public ResponseEntity<List<ProgressionSuggestionDTO>> getProgression(
            @CurrentUser Long userId,
            @PathVariable Long id) {

        Routine routine = routineService.getRoutine(userId, id);
        return ResponseEntity.ok(progressionService.suggestForRoutine(userId, routine));
    }

    @PostMapping
    public ResponseEntity<RoutineDetailDTO> createRoutine(
            @CurrentUser Long userId,
            @Valid @RequestBody RoutineUpsertRequest request) {

        Routine created = routineService.createRoutine(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(routineMapper.toDetail(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoutineDetailDTO> updateRoutine(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody RoutineUpsertRequest request) {

        Routine updated = routineService.updateRoutine(userId, id, request);
        return ResponseEntity.ok(routineMapper.toDetail(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoutine(@CurrentUser Long userId, @PathVariable Long id) {
        routineService.deleteRoutine(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(
            @CurrentUser Long userId,
            @RequestBody ReorderRequest request) {

        routineService.reorderRoutines(userId, request.routineIds());
        return ResponseEntity.noContent().build();
    }
}
