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

    @Column(nullable = false)
    private Integer priority; // 1 = niedrig, 5 = hoch

    private LocalDateTime deadline;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "scheduled_start_time")
    private LocalDateTime scheduledStartTime;

    @Column(name = "scheduled_end_time")
    private LocalDateTime scheduledEndTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(name = "space_type", nullable = false)
    private SpaceType spaceType = SpaceType.TASKS;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "category")
    private String category = "Personal";

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // --- Chunking (Reclaim-Stil: lange Tasks werden in mehrere Sessions aufgeteilt) ---
    // Alle nullable: ddl-auto=update legt sie auf bestehenden Zeilen als NULL an. Im Scheduler
    // wird deshalb nie direkt gelesen, sondern immer über Resolver mit Prefs-/Konstanten-Fallback.

    /** Kleinster sinnvoller Einzelblock in Minuten. */
    @Column(name = "min_chunk_minutes")
    private Integer minChunkMinutes;

    /** Größter Einzelblock in Minuten — darüber wird aufgeteilt. */
    @Column(name = "max_chunk_minutes")
    private Integer maxChunkMinutes;

    /** false = muss am Stück geplant werden. */
    @Column(name = "splittable")
    private Boolean splittable;

    /** Bereits erledigte Minuten; nur der Rest wird noch verplant. */
    @Column(name = "completed_minutes")
    private Integer completedMinutes;

    /** Frühestens ab diesem Zeitpunkt planen (Reclaims "start after"). */
    @Column(name = "not_before")
    private LocalDateTime notBefore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

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
