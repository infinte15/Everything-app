package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "exercise_sets")
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

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_session_id", nullable = false)
    private WorkoutSession workoutSession;
}