package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.MealType;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


@Data
public class RecipeDTO {
    private Long id;

    @NotBlank(message = "Rezeptname erforderlich")
    @Size(max = 200, message = "Name darf maximal 200 Zeichen lang sein")
    private String name;

    private String description;

    @NotNull(message = "Zubereitungszeit erforderlich")
    @Min(value = 0, message = "Zubereitungszeit kann nicht negativ sein")
    private Integer prepTimeMinutes;

    @NotNull(message = "Kochzeit erforderlich")
    @Min(value = 0, message = "Kochzeit kann nicht negativ sein")
    private Integer cookTimeMinutes;

    @NotNull(message = "Portionen erforderlich")
    @Min(value = 1, message = "Mindestens 1 Portion")
    private Integer servings;

    @NotBlank(message = "Kategorie erforderlich")
    private String category;

    /** Zu welchen Mahlzeiten das Rezept passt. Leer heisst: aus der Kategorie ableiten. */
    private Set<MealType> suitableFor = new LinkedHashSet<>();

    @NotEmpty(message = "Mindestens eine Zutat erforderlich")
    @Valid
    private List<RecipeIngredientDTO> ingredients = new ArrayList<>();

    @NotEmpty(message = "Mindestens ein Zubereitungsschritt erforderlich")
    @Valid
    private List<RecipeStepDTO> steps = new ArrayList<>();

    /**
     * Zutaten und Anleitung als Klartext, nur lesend.
     *
     * <p>Nicht fuer die App gedacht, sondern zum Hinsehen: wer eine Antwort im Terminal prueft,
     * will nicht dreissig JSON-Objekte lesen, um zu erkennen, ob der Import etwas Sinnvolles
     * erzeugt hat. Beim Schreiben werden die Felder ignoriert.
     */
    private String ingredientsText;
    private String instructionsText;

    // Naehrwerte je Portion.
    private Integer calories;
    private Double protein;
    private Double carbs;
    private Double fat;

    private String difficulty;

    private String imageUrl;

    private String tags;

    private Boolean isFavorite;

    @Min(value = 1, message = "Bewertung liegt zwischen 1 und 5")
    @Max(value = 5, message = "Bewertung liegt zwischen 1 und 5")
    private Short rating;

    private Integer cookCount;
    private LocalDateTime lastCookedAt;

    private String notes;

    private String sourceUrl;
    private String sourceName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
