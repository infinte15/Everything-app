package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudyGoalDTO {
    private Long id;

    @NotNull(message = "Ein Lernziel braucht ein Modul")
    private Long courseId;

    @Size(max = 8, message = "Emoji darf maximal 8 Zeichen lang sein")
    private String emoji;

    @NotNull(message = "Zielstunden erforderlich")
    @Positive(message = "Zielstunden müssen größer als 0 sein")
    private Double weeklyGoalHours;

    @PositiveOrZero(message = "Erfasste Stunden dürfen nicht negativ sein")
    private Double loggedHours;

    /** Abgeleitet, nur lesend: Name und Farbe kommen aus dem Modul, nicht vom Client. */
    private String courseName;
    private String courseColor;
    private Double remainingHours;
    private Double progress;

    /** Der Brücken-Task, über den die Reststunden im Kalender landen. */
    private Long taskId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
