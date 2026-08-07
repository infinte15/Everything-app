package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.MealType;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MealPlanDTO {
    private Long id;

    @NotNull(message = "Datum erforderlich")
    private LocalDate date;

    @NotNull(message = "Mahlzeitentyp erforderlich")
    private MealType mealType;

    @NotNull(message = "Rezept erforderlich")
    private Long recipeId;
    private String recipeName;

    /** Vorschaubild des Rezepts - der Wochenplan zeigt Kacheln, nicht nur Namen. */
    private String recipeImageUrl;

    private Integer plannedServings;

    private Boolean isCompleted;
    private LocalDateTime completedAt;

    private String notes;

    /**
     * Kochzeit als Aufgabe einplanen, damit der Scheduler sie in den Kalender legt.
     *
     * <p>Vorgabe aus. Frueher geschah das immer und unsichtbar - jetzt ist es ein Schalter im
     * Picker, und die Aufgabe verschwindet mit der Mahlzeit wieder.
     */
    private Boolean scheduleCooking;

    /** Gesetzt, wenn eine Kochzeit im Kalender steht. Nur lesend. */
    private Long cookingTaskId;

    private LocalDateTime createdAt;
}
