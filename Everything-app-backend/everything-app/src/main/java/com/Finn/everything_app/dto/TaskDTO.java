package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.SpaceType;
import com.Finn.everything_app.model.TaskStatus;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Data
public class TaskDTO {
    private Long id;

    @NotBlank(message = "Titel darf nicht leer sein")
    @Size(max = 200, message = "Titel darf maximal 200 Zeichen lang sein")
    private String title;

    @Size(max = 2000, message = "Beschreibung darf maximal 2000 Zeichen lang sein")
    private String description;

    @Min(value = 1, message = "Priorität muss zwischen 1 und 5 liegen")
    @Max(value = 5, message = "Priorität muss zwischen 1 und 5 liegen")
    private Integer priority;

    private LocalDateTime deadline;

    @Min(value = 5, message = "Dauer muss mindestens 5 Minuten sein")
    private Integer estimatedDurationMinutes;

    private LocalDateTime scheduledStartTime;
    private LocalDateTime scheduledEndTime;

    private TaskStatus status;
    private SpaceType spaceType;

    private Long projectId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}