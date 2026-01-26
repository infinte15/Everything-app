package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "workout_sessions")
@Data
public class WorkoutSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "workout_plan_id")
    private WorkoutPlan workoutPlan;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String name;

    private LocalDateTime scheduledDateTime;
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "workoutSession", cascade = CascadeType.ALL)
    private List<Exercise> exercises;

    private Integer durationMinutes;

    private String notes;
}