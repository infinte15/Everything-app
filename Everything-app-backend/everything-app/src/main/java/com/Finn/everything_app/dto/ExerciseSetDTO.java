package com.Finn.everything_app.dto;


import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class ExerciseSetDTO {
    private Long id;

    @NotNull(message = "Übung erforderlich")
    private Long exerciseId;
    private String exerciseName;

    @NotNull(message = "Workout-Session erforderlich")
    private Long workoutSessionId;

    @NotNull(message = "Satz-Nummer erforderlich")
    @Min(value = 1, message = "Satz-Nummer muss mindestens 1 sein")
    private Integer setNumber;

    @Min(value = 1, message = "Wiederholungen müssen mindestens 1 sein")
    private Integer reps;

    @Min(value = 0, message = "Gewicht kann nicht negativ sein")
    private Double weight;

    @Min(value = 0, message = "Dauer kann nicht negativ sein")
    private Integer durationSeconds;

    private String notes;

    private Boolean isCompleted;
}
