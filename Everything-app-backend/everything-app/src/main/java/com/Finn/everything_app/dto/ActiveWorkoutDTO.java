package com.Finn.everything_app.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Antwort auf {@code POST /api/sports/workouts/start}. */
@Data
public class ActiveWorkoutDTO {
    private Long sessionId;
    private String name;
    private LocalDateTime startedAt;
    private Long routineId;
    private String routineName;
    private Long workoutPlanId;

    private List<PlannedExerciseDTO> plannedExercises = new ArrayList<>();
}
