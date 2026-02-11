package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Data
public class WorkoutSessionDTO {
    private Long id;

    @NotBlank(message = "Session-Name erforderlich")
    private String name;

    private String description;

    private Long workoutPlanId;
    private String workoutPlanName;

    @NotNull(message = "Startzeit erforderlich")
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Min(value = 1, message = "Dauer muss mindestens 1 Minute sein")
    private Integer durationMinutes;

    private String workoutType;


    @Min(value = 1, message = "Intensität muss zwischen 1 und 10 liegen")
    @Max(value = 10, message = "Intensität muss zwischen 1 und 10 liegen")
    private Integer intensity;

    private Integer caloriesBurned;

    private String notes;
    private String location;

    private Boolean isCompleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
