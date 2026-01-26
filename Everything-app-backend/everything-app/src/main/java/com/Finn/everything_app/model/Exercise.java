package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "exercises")
@Data
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "workout_session_id", nullable = false)
    private WorkoutSession workoutSession;

    @Column(nullable = false)
    private String name;

    private Integer sets;
    private Integer reps;
    private Double weight;  // in kg

    private Integer durationSeconds;  // Für zeitbasierte Übungen (Plank, etc.)

    private Boolean completed;

    private String notes;
}