package com.Finn.everything_app.model;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "exercises", indexes = {
        @Index(name = "idx_exercises_external_id", columnList = "external_id"),
        @Index(name = "idx_exercises_muscle_group", columnList = "muscle_group")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    /**
     * Denormalisiertes Spiegelbild von {@code primaryMuscles[0].getSlug()}.
     *
     * <p>Die Spalte bleibt NOT NULL: {@code ddl-auto=update} kann die Bedingung auf einer
     * gefuellten Tabelle nicht mehr loesen, und der bestehende Client liest das Feld.
     * Gepflegt wird sie von {@code ExerciseService.syncMuscleGroupMirror} und vom Seeder.
     * Fuer Auswertungen ist immer {@link #primaryMuscles} die Quelle der Wahrheit.
     */
    @Column(name = "muscle_group", nullable = false, length = 50)
    private String muscleGroup;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "exercise_primary_muscles",
            joinColumns = @JoinColumn(name = "exercise_id"),
            indexes = @Index(name = "idx_epm_exercise", columnList = "exercise_id"))
    @Column(name = "muscle", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 200)
    private Set<MuscleGroup> primaryMuscles = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "exercise_secondary_muscles",
            joinColumns = @JoinColumn(name = "exercise_id"),
            indexes = @Index(name = "idx_esm_exercise", columnList = "exercise_id"))
    @Column(name = "muscle", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 200)
    private Set<MuscleGroup> secondaryMuscles = new LinkedHashSet<>();

    @Column(length = 50)
    private String equipment;

    @Column(length = 50)
    private String difficulty;

    /** strength, stretching, cardio, plyometrics, powerlifting, olympic weightlifting, strongman */
    @Column(length = 50)
    private String category;

    /** push / pull / static - Spaltenname bewusst nicht "force" (reserviertes Wort). */
    @Column(name = "force_type", length = 20)
    private String force;

    /** compound / isolation - steuert u.a. die Default-Pausenzeit. */
    @Column(length = 20)
    private String mechanic;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    /** Startposition der Uebung. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Endposition der Uebung (zweites Bild des Datensatzes).
     *
     * <p>Nur die free-exercise-db lieferte zwei Standbilder. Beim ExerciseDB-Katalog bleibt das
     * Feld leer - dort uebernimmt {@link #animationUrl} die Bewegung.
     */
    @Column(name = "image_url_end", length = 500)
    private String imageUrlEnd;

    /**
     * Animation der Uebung (180x180 GIF), extern gehostet und zur Laufzeit geladen.
     *
     * <p>Die Medien sind (c) Gym visual (https://gymvisual.com/) und liegen bewusst NICHT im
     * Repository: hier steht nur die URL auf den oeffentlichen Spiegel des Datensatzes. Der
     * Client zeigt die Attribution neben dem Bild an.
     */
    @Column(name = "animation_url", length = 500)
    private String animationUrl;

    /** Stabiler natuerlicher Schluessel des Katalog-Datensatzes, macht den Seeder idempotent. */
    @Column(name = "external_id", length = 120, unique = true)
    private String externalId;

    /** Herkunft des Datensatzes, z.B. "free-exercise-db". */
    @Column(length = 50)
    private String source;

    /** Vorgeschlagene Pausenzeit in Sekunden, wenn die Routine nichts eigenes vorgibt. */
    @Column(name = "default_rest_seconds")
    private Integer defaultRestSeconds;

    /** true = geseedete Katalog-Uebung, gehoert keinem User und ist schreibgeschuetzt. */
    @Column(name = "is_system")
    private Boolean isSystem = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;
    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL)
    private List<ExerciseSet> exerciseSets;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}