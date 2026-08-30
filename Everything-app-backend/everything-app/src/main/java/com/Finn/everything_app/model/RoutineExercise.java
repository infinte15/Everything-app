package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Eine Uebung innerhalb einer {@link Routine} samt Zielvorgaben.
 *
 * <p>Das ist die Vorlage, nicht das Protokoll: was tatsaechlich trainiert wurde, steht in
 * {@link ExerciseSet} an der Trainingseinheit.
 */
@Entity
@Table(name = "routine_exercises", indexes = {
        @Index(name = "idx_routine_exercises_routine", columnList = "routine_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutineExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", nullable = false)
    private Routine routine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    @Column(name = "target_sets")
    private Integer targetSets;

    @Column(name = "target_reps_min")
    private Integer targetRepsMin;

    /** Zusammen mit {@link #targetRepsMin} die Spanne, die Lyfta als "8-12" anzeigt. */
    @Column(name = "target_reps_max")
    private Integer targetRepsMax;

    @Column(name = "target_weight")
    private Double targetWeight;

    /** Fuer Uebungen auf Zeit (Planke, Cardio) statt auf Wiederholungen. */
    @Column(name = "target_duration_seconds")
    private Integer targetDurationSeconds;

    /** Pausenzeit nach einem Satz dieser Uebung - speist den Pausen-Timer im Client. */
    @Column(name = "rest_seconds")
    private Integer restSeconds;

    @Column(length = 500)
    private String notes;

    /** Uebungen mit derselben Nummer werden im Wechsel trainiert (Supersatz). */
    @Column(name = "superset_group")
    private Integer supersetGroup;

    /**
     * Wie die naechste Vorgabe aus dem Verlauf abgeleitet wird. Nullable, weil
     * {@code ddl-auto=update} kein NOT NULL auf eine gefuellte Tabelle setzen kann -
     * {@code null} ist als {@link ProgressionPolicy#OFF} zu lesen.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "progression_policy", length = 20)
    private ProgressionPolicy progressionPolicy;

    /**
     * Ladbare Stufe in kg. {@code null} heisst "aus der Uebung ableiten" - Bein- und
     * Rueckenuebungen springen groesser als Isolationsarbeit.
     */
    @Column(name = "increment_kg")
    private Double incrementKg;

    /**
     * Koerpergewichtsuebung: gesteigert werden Wiederholungen und Saetze, nicht die Last.
     * Ohne das Kennzeichen liesse sich "0 kg geloggt" nicht von "Gewicht vergessen"
     * unterscheiden.
     */
    @Column(name = "is_bodyweight")
    private Boolean isBodyweight;
}
