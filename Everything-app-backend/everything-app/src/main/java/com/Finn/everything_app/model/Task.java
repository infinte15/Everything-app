package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Data
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    private Integer priority;  // 1 = niedrig, 5 = hoch

    private LocalDateTime deadline;
    private Integer estimatedDurationMinutes;  // Geschätzte Dauer

    private LocalDateTime scheduledStartTime;  // Vom Smart Scheduler gesetzt
    private LocalDateTime scheduledEndTime;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;  // OPEN, IN_PROGRESS, COMPLETED, CANCELLED

    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;  // Task gehört zu Projekt

    @Enumerated(EnumType.STRING)
    private SpaceType spaceType;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        status = TaskStatus.OPEN;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

enum TaskStatus {
    OPEN, IN_PROGRESS, COMPLETED, CANCELLED
}

enum SpaceType {
    STUDY, SPORTS, TASKS, RECIPES, FINANCE
}