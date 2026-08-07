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

    private LocalDateTime createdAt;
}
