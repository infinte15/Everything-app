package com.Finn.everything_app.dto;


import com.Finn.everything_app.model.SetType;
import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

@Data
public class ExerciseSetDTO {
    private Long id;

    @NotNull(message = "Übung erforderlich")
    private Long exerciseId;
    private String exerciseName;

    /**
     * Nur beim alten Einzel-Satz-Endpunkt Pflicht. Beim gebuendelten Abschluss eines Trainings
     * steckt die Einheit im Pfad, deshalb hier keine Validierung.
     */
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

    private SetType setType;

    @Min(value = 0, message = "Pause kann nicht negativ sein")
    private Integer restSeconds;

    @Min(value = 1, message = "RPE liegt zwischen 1 und 10")
    @Max(value = 10, message = "RPE liegt zwischen 1 und 10")
    private Integer rpe;

    private Integer exerciseOrder;
    private Long routineExerciseId;
    private LocalDateTime completedAt;

    /** Nur lesend: wann der Satz trainiert wurde (Startzeit der Einheit). */
    private LocalDateTime performedAt;
}
