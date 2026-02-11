package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class WorkoutPlanDTO {
    private Long id;

    @NotBlank(message = "Plan-Name erforderlich")
    @Size(max = 200, message = "Name darf maximal 200 Zeichen lang sein")
    private String name;

    private String description;

    @NotBlank(message = "Ziel erforderlich")
    private String goal;

    private String difficulty;

    @Min(value = 1, message = "Dauer muss mindestens 1 Tag sein")
    private Integer durationWeeks;

    @Min(value = 1, message = "Mindestens 1 Training pro Woche")
    @Max(value = 7, message = "Maximal 7 Trainings pro Woche")
    private Integer workoutsPerWeek;

    private LocalDate startDate;
    private LocalDate endDate;

    private Boolean isActive;


    private Integer totalWorkouts;
    private Integer completedWorkouts;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
