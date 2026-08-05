package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "projects")
@Data
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_end_date")
    private LocalDate targetEndDate;

    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.PLANNING;

    @Column(name = "completion_percentage")
    private Integer completionPercentage = 0;

    @Column(name = "tasks_total")
    private Integer tasksTotal = 0;

    @Column(name = "tasks_completed")
    private Integer tasksCompleted = 0;

    @Column(name = "weekly_session_count")
    private Integer weeklySessionCount = 1;

    @Column(name = "session_duration_minutes")
    private Integer sessionDurationMinutes = 60;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Bewusst OHNE cascade: Tasks sind gewoehnliche Aufgaben mit einer Zuordnung und ueberleben
    // ihr Projekt. cascade = ALL wuerde sie beim Loeschen des Projekts mitreissen und dabei in die
    // calendar_events.related_task_id-Constraint laufen (vgl. TaskService.deleteTask, das die
    // Kalenderbloecke vorher aufraeumt). ProjectService.deleteProject entkoppelt stattdessen.
    @OneToMany(mappedBy = "project")
    private List<Task> tasks;

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

