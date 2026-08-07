package com.Finn.everything_app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecipeCookLogDTO {

    private Long id;

    private Long recipeId;
    private String recipeName;

    /** Wann gekocht wurde. Leer heisst jetzt - der Normalfall ist der Knopf direkt danach. */
    private LocalDateTime cookedAt;

    @Min(value = 1, message = "Bewertung liegt zwischen 1 und 5")
    @Max(value = 5, message = "Bewertung liegt zwischen 1 und 5")
    private Short rating;

    @Min(value = 1, message = "Mindestens 1 Portion")
    private Integer servings;

    @Size(max = 500)
    private String note;
}
