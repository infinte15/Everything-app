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
}

