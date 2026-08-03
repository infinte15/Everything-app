package com.Finn.everything_app.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Schliesst ein Training in einem einzigen Request ab: Einheit plus alle Saetze.
 *
 * <p>Der Aufruf ist wiederholbar - der Server ersetzt die Saetze der Einheit komplett,
 * ein zweiter identischer Aufruf verdoppelt also nichts.
 */
@Data
public class FinishWorkoutRequest {

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMinutes;
    private String notes;
    private Integer intensity;
    private Integer caloriesBurned;
    private String location;

    /** Nur fuer das nachtraegliche Protokollieren via {@code POST /workouts}. */
    private Long routineId;
    private String name;

    @Valid
    private List<LoggedExercise> exercises = new ArrayList<>();

    @Data
    public static class LoggedExercise {
        private Long exerciseId;
        private Integer orderIndex;
        private Integer restSeconds;
        private Long routineExerciseId;

        @Valid
        private List<ExerciseSetDTO> sets = new ArrayList<>();
    }
}
