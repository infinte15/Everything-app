package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.EventType;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Data
public class CalendarEventDTO {
    private Long id;

    @NotBlank(message = "Titel erforderlich")
    private String title;

    private String description;

    @NotNull(message = "Startzeit erforderlich")
    private LocalDateTime startTime;

    @NotNull(message = "Endzeit erforderlich")
    private LocalDateTime endTime;

    private String location;
    private EventType eventType;
    private Boolean isFixed;


    private Long relatedTaskId;
    private Long relatedHabitId;
    private Long relatedWorkoutId;

    private String color;
    private String notes;
}