package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RecipeDTO {
    private Long id;

    @NotBlank(message = "Rezeptname erforderlich")
    @Size(max = 200, message = "Name darf maximal 200 Zeichen lang sein")
    private String name;

    private String description;

    @NotNull(message = "Zubereitungszeit erforderlich")
    @Min(value = 1, message = "Zubereitungszeit muss mindestens 1 Minute sein")
    private Integer prepTimeMinutes;

    @NotNull(message = "Kochzeit erforderlich")
    @Min(value = 0, message = "Kochzeit kann nicht negativ sein")
    private Integer cookTimeMinutes;

    @NotNull(message = "Portionen erforderlich")
    @Min(value = 1, message = "Mindestens 1 Portion")
    private Integer servings;

    @NotBlank(message = "Kategorie erforderlich")
    private String category;


    @NotBlank(message = "Zutaten erforderlich")
    private String ingredients;

    @NotBlank(message = "Anleitung erforderlich")
    private String instructions;

    // Nährwerte pro Portion
    private Integer calories;
    private Double protein;
    private Double carbs;
    private Double fat;

    private String difficulty;

    private String imageUrl;

    private String tags;

    private Boolean isFavorite;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}