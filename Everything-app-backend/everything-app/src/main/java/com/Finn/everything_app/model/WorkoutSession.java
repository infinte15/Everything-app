package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "workout_sessions")
@Data
public class WorkoutSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "workout_type", length = 50)
    private String workoutType;

    @Column(nullable = false)
    private Integer intensity = 5;

    @Column(name = "calories_burned")
    private Integer caloriesBurned;

    @Column(length = 2000)
    private String notes;

    @Column(length = 50)
    private String location;

    @Column(name = "is_completed")
    private Boolean isCompleted = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_plan_id")
    private WorkoutPlan workoutPlan;

    // Welche Routine hier trainiert wird bzw. werden soll. Auch vom Scheduler gesetzt, damit ein
    // automatisch platzierter Platzhalter weiss, welches Training dahintersteckt.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id")
    private Routine routine;

    @OneToMany(mappedBy = "workoutSession", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExerciseSet> exerciseSets;

    @Column(name = "scheduled_date_time")
    private LocalDateTime scheduledDateTime;

    @Column(name = "actual_duration_minutes")
    private Integer actualDurationMinutes;

    @Column(name = "distance_km")
    private Double distanceKm;

    // true = scheduler may auto-place/move this session's time; false = user pinned an explicit time
    @Column(name = "is_flexible")
    private Boolean isFlexible = false;

    // Which ISO week (Monday) this auto-generated placeholder counts toward, for weekly-target bookkeeping
    @Column(name = "target_week_start")
    private LocalDate targetWeekStart;

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