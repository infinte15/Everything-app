package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "exercise_sets", indexes = {
        @Index(name = "idx_sets_session", columnList = "workout_session_id"),
        @Index(name = "idx_sets_exercise", columnList = "exercise_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    private Integer reps;

    private Double weight;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(length = 500)
    private String notes;

    @Column(name = "is_completed")
    private Boolean isCompleted = false;

    /** Bewusst nullable - siehe {@link SetType}. Leser behandeln null wie NORMAL. */
    @Enumerated(EnumType.STRING)
    @Column(name = "set_type", length = 20)
    private SetType setType = SetType.NORMAL;

    /** Tatsaechlich eingelegte Pause nach diesem Satz. */
    @Column(name = "rest_seconds")
    private Integer restSeconds;

    /** Gefuehlte Anstrengung 1-10. */
    private Integer rpe;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Position des Uebungsblocks innerhalb der Einheit. Ohne dieses Feld laesst sich die
     * tatsaechlich trainierte Reihenfolge spaeter nicht mehr rekonstruieren.
     */
    @Column(name = "exercise_order")
    private Integer exerciseOrder;

    /**
     * Aus welcher Routinen-Zeile der Satz stammt - absichtlich nur die ID und kein
     * Fremdschluessel, damit das Loeschen einer Routine die Historie nicht mitnimmt.
     */
    @Column(name = "routine_exercise_id")
    private Long routineExerciseId;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_session_id", nullable = false)
    private WorkoutSession workoutSession;
}