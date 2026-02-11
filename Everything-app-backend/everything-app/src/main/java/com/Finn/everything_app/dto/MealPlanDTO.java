package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MealPlanDTO {
    private Long id;

    @NotNull(message = "Datum erforderlich")
    private LocalDate date;

    @NotBlank(message = "Mahlzeitentyp erforderlich")
    private String mealType;

    @NotNull(message = "Rezept erforderlich")
    private Long recipeId;
    private String recipeName;

    private Integer plannedServings;

    private Boolean isCompleted;
    private LocalDateTime completedAt;

    private String notes;

    private LocalDateTime createdAt;
}
