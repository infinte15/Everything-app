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
    private Long relatedProjectId;

    /**
     * Deadline und Priorität des verknüpften Tasks — nur lesend, gespeichert wird beides am Task.
     *
     * Der Kalender braucht sie, um einen Block hervorzuheben, der die letzte Chance vor der
     * Deadline ist. Die Dringlichkeitsstufe selbst wird bewusst NICHT hier berechnet: sie hängt
     * an "jetzt" und ändert sich im Minutentakt, also gehört sie in die Oberfläche.
     */
    private LocalDateTime relatedTaskDeadline;
    private Integer relatedTaskPriority;

    private String color;
    private String notes;

    /** Nur lesend: gesetzt wird das ausschließlich über PUT /events/{id}/complete. */
    private LocalDateTime completedAt;

    /** Nur lesend: gesetzt wird das ausschließlich über PUT /events/{id}/skip. */
    private LocalDateTime skippedAt;
}