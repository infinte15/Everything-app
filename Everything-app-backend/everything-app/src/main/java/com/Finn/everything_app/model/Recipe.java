package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "recipes", indexes = {
        @Index(name = "idx_recipes_user_category", columnList = "user_id, category"),
        @Index(name = "idx_recipes_user_last_cooked", columnList = "user_id, last_cooked_at")
})
@Data
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "prep_time_minutes", nullable = false)
    private Integer prepTimeMinutes;

    @Column(name = "cook_time_minutes", nullable = false)
    private Integer cookTimeMinutes;

    @Column(nullable = false)
    private Integer servings;

    /**
     * Art des Gerichts aus dem festen Katalog ({@code data/recipe-categories.json}).
     *
     * <p>Frueher wurde diese Spalte doppelt benutzt: {@code generateWeeklyPlan} verglich sie
     * gegen {@code "MITTAGESSEN"}, um passende Rezepte zu finden. Wann man etwas isst und was es
     * ist, sind zwei verschiedene Fragen - die erste beantwortet jetzt {@link #suitableFor}.
     */
    @Column(nullable = false, length = 50)
    private String category;

    /**
     * Zu welchen Mahlzeiten das Rezept passt.
     *
     * <p>Wird beim Anlegen und Importieren aus der Kategorie vorbelegt und bleibt danach
     * editierbar - eine Suppe kann Mittag- und Abendessen sein, und nur der Koch weiss, ob
     * Pfannkuchen bei ihm Fruehstueck sind.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "recipe_meal_types",
            joinColumns = @JoinColumn(name = "recipe_id"),
            indexes = @Index(name = "idx_recipe_meal_types_recipe", columnList = "recipe_id"))
    @Column(name = "meal_type", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<MealType> suitableFor = new LinkedHashSet<>();

    /**
     * Zutaten als Liste, nicht mehr als Textblock.
     *
     * <p>{@code orphanRemoval}, weil eine Zutat ausserhalb ihres Rezepts keinen Sinn ergibt:
     * loescht man sie aus der Liste, soll die Zeile verschwinden, nicht mit
     * {@code recipe_id = null} zurueckbleiben.
     */
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<RecipeIngredient> ingredientList = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<RecipeStep> steps = new ArrayList<>();

    /**
     * Altlast: Zutaten und Anleitung als Klartext.
     *
     * <p>Die Spalten sind seit der Migration nullbar und werden nur noch gespiegelt beschrieben,
     * damit ein Rollback auf die vorige Jar und der noch nicht umgebaute Client weiterlaufen.
     * Sie verschwinden mit {@code 2026-08-07-recipe-drop-legacy-text.sql}; gelesen wird
     * ausschliesslich {@link #ingredientList} und {@link #steps}.
     */
    @Column(columnDefinition = "TEXT")
    private String ingredients;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    // Naehrwerte je Portion.
    private Integer calories;
    private Double protein;
    private Double carbs;
    private Double fat;

    @Column(length = 50)
    private String difficulty;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String tags;

    @Column(name = "is_favorite")
    private Boolean isFavorite = false;

    // ── Eigene Bewertung und Kochprotokoll ────────────────────────────────────────────────

    /** Eigene Bewertung, 1 bis 5. Nullbar: nicht bewertet ist etwas anderes als schlecht. */
    private Short rating;

    /** Wie oft gekocht. Denormalisiert aus {@link RecipeCookLog}, siehe dort. */
    @Column(name = "cook_count", nullable = false)
    private Integer cookCount = 0;

    @Column(name = "last_cooked_at")
    private LocalDateTime lastCookedAt;

    /** Eigene Notiz zum Rezept ("beim naechsten Mal weniger Salz"). */
    @Column(columnDefinition = "TEXT")
    private String notes;

    // ── Herkunft ──────────────────────────────────────────────────────────────────────────

    /** Adresse der importierten Seite - Nachweis und Weg zurueck zu den Kommentaren. */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "source_name", length = 100)
    private String sourceName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL)
    private List<MealPlan> mealPlans;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Ersetzt die Zutatenliste und nummeriert sie neu durch.
     *
     * <p>Ueber diesen Weg, nicht ueber {@code setIngredientList}: die Liste ist eine von
     * Hibernate verwaltete Sammlung. Wird sie durch eine neue Instanz ersetzt, wirft Hibernate
     * "A collection with cascade=all-delete-orphan was no longer referenced". Also den Inhalt
     * tauschen, nicht die Sammlung.
     */
    public void replaceIngredients(List<RecipeIngredient> replacement) {
        ingredientList.clear();
        for (int i = 0; i < replacement.size(); i++) {
            RecipeIngredient ingredient = replacement.get(i);
            ingredient.setRecipe(this);
            ingredient.setPosition(i);
            ingredientList.add(ingredient);
        }
    }

    public void replaceSteps(List<RecipeStep> replacement) {
        steps.clear();
        for (int i = 0; i < replacement.size(); i++) {
            RecipeStep step = replacement.get(i);
            step.setRecipe(this);
            step.setPosition(i);
            steps.add(step);
        }
    }

    public int getTotalTimeMinutes() {
        return (prepTimeMinutes == null ? 0 : prepTimeMinutes)
                + (cookTimeMinutes == null ? 0 : cookTimeMinutes);
    }
}
