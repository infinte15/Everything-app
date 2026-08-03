package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.mapper.WorkoutLogMapper;
import com.Finn.everything_app.model.WorkoutSession;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.WorkoutLoggingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Laufendes Training: starten, abschliessen, nachtraeglich protokollieren. */
@RestController
@RequestMapping("/api/sports/workouts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WorkoutLogController {

    private final WorkoutLoggingService loggingService;
    private final WorkoutLogMapper logMapper;

    @PostMapping("/start")
    public ResponseEntity<ActiveWorkoutDTO> start(
            @CurrentUser Long userId,
            @RequestBody(required = false) StartWorkoutRequest request) {

        StartWorkoutRequest safe = request != null ? request : new StartWorkoutRequest();
        return ResponseEntity.status(HttpStatus.CREATED).body(loggingService.start(userId, safe));
    }

    /** Speichert Einheit und alle Saetze in einem Rutsch. Mehrfacher Aufruf ist unschaedlich. */
    @PostMapping("/{sessionId}/finish")
    public ResponseEntity<WorkoutSessionDetailDTO> finish(
            @CurrentUser Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody FinishWorkoutRequest request) {

        loggingService.finish(userId, sessionId, request);
        WorkoutSession detail = loggingService.getSessionDetail(userId, sessionId);
        return ResponseEntity.ok(logMapper.toDetail(detail));
    }

    /** Bereits absolviertes Training nachtragen. */
    @PostMapping
    public ResponseEntity<WorkoutSessionDetailDTO> logCompleted(
            @CurrentUser Long userId,
            @Valid @RequestBody FinishWorkoutRequest request) {

        WorkoutSession saved = loggingService.logCompleted(userId, request);
        WorkoutSession detail = loggingService.getSessionDetail(userId, saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(logMapper.toDetail(detail));
    }
}
