package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Eine wiederverwendbare Trainings-Routine ("Push A") mit fester Uebungsreihenfolge.
 *
 * <p>Bewusst eine eigene Entity statt einer Erweiterung von {@link WorkoutPlan}: ein Plan ist
 * das <em>Programm</em> (Wochenziel, aktiv/inaktiv, genau eines pro User), eine Routine ist das
 * <em>Rezept</em> und davon gibt es mehrere pro Programm. Die Plan-Zuordnung ist optional,
 * damit man eine Routine auch ohne Programm einfach starten kann.
 */
@Entity
@Table(name = "routines", indexes = {
        @Index(name = "idx_routines_user", columnList = "user_id"),
        @Index(name = "idx_routines_plan", columnList = "workout_plan_id")
})
// Trainingseinheiten verweisen lazy hierher; ohne Batching laedt eine Liste von Einheiten
// eine Routine pro Zeile nach.
@BatchSize(size = 100)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Routine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    /** Titelbild der Routine. Reine URL - es gibt in diesem Projekt keinen Datei-Upload. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Fallback-Farbe, wenn kein Bild gesetzt ist (z.B. "#30D158"). */
    @Column(name = "color_hex", length = 16)
    private String colorHex;

    /** Freie Tagesbezeichnung wie "Push A" oder "Montag". */
    @Column(name = "day_label", length = 50)
    private String dayLabel;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    /** Position in der Rotation des Plans. */
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    @Column(name = "is_archived")
    private Boolean isArchived = false;

    @Column(name = "last_performed_at")
    private LocalDateTime lastPerformedAt;

    @Column(name = "perform_count")
    private Integer performCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Optional: gehoert die Routine zu einem Programm, rotiert der Scheduler sie ein. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_plan_id")
    private WorkoutPlan workoutPlan;

    @OneToMany(mappedBy = "routine", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<RoutineExercise> exercises = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (orderIndex == null) orderIndex = 0;
        if (isArchived == null) isArchived = false;
        if (performCount == null) performCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
