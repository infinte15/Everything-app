package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.SpaceType;
import com.Finn.everything_app.model.TaskStatus;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonProperty("category")
    private String category;

    // --- Chunking ---
    @Min(value = 5, message = "Mindestblock muss mindestens 5 Minuten sein")
    private Integer minChunkMinutes;

    @Min(value = 5, message = "Maximalblock muss mindestens 5 Minuten sein")
    private Integer maxChunkMinutes;

    private Boolean splittable;

    @Min(value = 1, message = "Es muss mindestens ein Block pro Tag erlaubt sein")
    private Integer maxChunksPerDay;

    @Min(value = 0, message = "Erledigte Minuten dürfen nicht negativ sein")
    private Integer completedMinutes;

    private LocalDateTime notBefore;

    /**
     * Felder, die dieser Aufruf ausdrücklich LEEREN soll — siehe {@link TaskClearableField}.
     *
     * Nur eingehend: {@code TaskMapper.toDTO} setzt das nie, es geht also nie an den Client
     * zurück. Beim Anlegen ohne Bedeutung.
     */
    private Set<TaskClearableField> clearFields;
}