package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "meal_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "meal_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MealType mealType;

    @Column(name = "planned_servings")
    private Integer plannedServings = 1;

    @Column(name = "is_completed")
    private Boolean isCompleted = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(length = 500)
    private String notes;

    /**
     * Die Aufgabe, die der Scheduler fuer die Kochzeit einplant - falls gewuenscht.
     *
     * <p>Nur die Id, keine Beziehung: der Wochenplan soll nicht an der Aufgabenverwaltung
     * haengen, und geloescht wird ueber den {@code TaskService}, damit dessen Aufraeumarbeit
     * (Kalendereintraege, Projektzahlen, Neuplanung) mitlaeuft.
     *
     * <p>Vorher legte der Flutter-Provider diese Aufgabe still selbst an - mit erfundener
     * Faelligkeit um 13:00 bzw. 19:00 - und liess sie stehen, wenn man die Mahlzeit wieder
     * aus dem Plan nahm.
     */
    @Column(name = "cooking_task_id")
    private Long cookingTaskId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /** Rueckverweis - ausgenommen wie bei {@link RecipeIngredient#getRecipe()}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Recipe recipe;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}