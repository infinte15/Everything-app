package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalTime;

@Entity
@Table(name = "user_preferences")
@Data
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalTime workdayStart;
    private LocalTime workdayEnd;


    @Enumerated(EnumType.STRING)
    private ProductivityPeakTime peakProductivityTime;


    private Integer breakDurationMinutes;
    private Integer hoursBeforeBreak;


    private Boolean groupSimilarTasks;
    private Integer maxTasksPerDay;


    private Boolean notificationsEnabled;
    private Integer reminderMinutesBefore;


    private String themeColor;
    private Boolean darkMode;


    // --- Scheduling-Einstellungen ---
    // Alle nullable: ddl-auto=update legt sie auf bestehenden Zeilen als NULL an, deshalb wird
    // im Scheduler nie direkt gelesen, sondern immer über die Resolver mit Default-Fallback.

    /** Puffer um fixe Termine herum, in Minuten. Default 0 — das Feature ist opt-in. */
    @Column(name = "buffer_minutes")
    private Integer bufferMinutes;

    /** Obergrenze für automatisch verplante Task-Zeit pro Kalendertag. */
    @Column(name = "max_task_minutes_per_day")
    private Integer maxTaskMinutesPerDay;

    /** Untergrenze für einen einzelnen Task-Block beim Aufteilen. */
    @Column(name = "default_min_chunk_minutes")
    private Integer defaultMinChunkMinutes;

    /** Obergrenze für einen einzelnen Task-Block beim Aufteilen. */
    @Column(name = "default_max_chunk_minutes")
    private Integer defaultMaxChunkMinutes;

    /** Schaltet die automatische Neuplanung komplett ab. */
    @Column(name = "auto_schedule_enabled")
    private Boolean autoScheduleEnabled;
}

