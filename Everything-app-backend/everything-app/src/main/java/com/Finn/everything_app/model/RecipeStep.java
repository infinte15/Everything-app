package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Ein Arbeitsschritt eines Rezepts.
 *
 * <p>Aus demselben Grund eine eigene Zeile wie bei {@link RecipeIngredient}: die Anleitung war
 * ein TEXT-Block, und wer beim Kochen Schritt 4 von 7 sucht, braucht eine Liste, keinen Absatz.
 * Die Nummer steht in {@link #position} und wird beim Anzeigen erzeugt - fuehrende "1." aus der
 * Quelle werden beim Import entfernt, sonst steht dort spaeter "1. 1. Mehl sieben".
 */
@Entity
@Table(name = "recipe_steps", indexes = {
        @Index(name = "idx_recipe_steps_recipe", columnList = "recipe_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Rueckverweis - ausgenommen wie bei {@link RecipeIngredient#getRecipe()}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Recipe recipe;

    /** Reihenfolge der Schritte, bei 0 beginnend. */
    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;
}
