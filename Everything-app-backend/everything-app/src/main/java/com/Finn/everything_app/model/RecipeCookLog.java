package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Ein Mal gekocht.
 *
 * <p>Chefkochs Sterne sind die Meinung von tausend Fremden. In einem eigenen Kochbuch traegt
 * eine andere Information: wie oft du ein Rezept wirklich gemacht hast und wann zuletzt. Genau
 * daraus lebt der Entdecken-Tab ("lange nicht gekocht"), und genau das weiss sonst niemand.
 *
 * <p>Die Zaehler liegen zusaetzlich denormalisiert am {@link Recipe}
 * ({@code cookCount}, {@code lastCookedAt}), damit die Entdecken-Abfragen ein schlichtes
 * {@code ORDER BY} sind statt eines Group-by ueber diese Tabelle bei jedem Aufruf. Beide werden
 * in derselben Transaktion gepflegt wie der Eintrag hier.
 */
@Entity
@Table(name = "recipe_cook_logs", indexes = {
        @Index(name = "idx_recipe_cook_logs_recipe", columnList = "recipe_id"),
        @Index(name = "idx_recipe_cook_logs_user_date", columnList = "user_id, cooked_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeCookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(name = "cooked_at", nullable = false)
    private LocalDateTime cookedAt;

    /** Bewertung dieses Durchgangs, 1 bis 5. Nullbar - man muss nicht jedes Mal urteilen. */
    private Short rating;

    /** Wie viele Portionen es diesmal waren. Nullbar. */
    private Integer servings;

    @Column(length = 500)
    private String note;
}
