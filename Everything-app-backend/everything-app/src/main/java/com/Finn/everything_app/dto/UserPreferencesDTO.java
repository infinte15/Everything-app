package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.ProductivityPeakTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalTime;

@Data
public class UserPreferencesDTO {

    private Long id;

    private LocalTime workdayStart;
    private LocalTime workdayEnd;

    private ProductivityPeakTime peakProductivityTime;

    @Min(value = 0,  message = "Pausenlänge darf nicht negativ sein")
    @Max(value = 120, message = "Pausenlänge darf höchstens 120 Minuten sein")
    private Integer breakDurationMinutes;

    @Min(value = 1, message = "Stunden bis zur Pause muss mindestens 1 sein")
    @Max(value = 12, message = "Stunden bis zur Pause darf höchstens 12 sein")
    private Integer hoursBeforeBreak;

    private Boolean groupSimilarTasks;

    @Min(value = 1, message = "Maximale Tasks pro Tag muss mindestens 1 sein")
    @Max(value = 50, message = "Maximale Tasks pro Tag darf höchstens 50 sein")
    private Integer maxTasksPerDay;

    private Boolean notificationsEnabled;

    @Min(value = 0, message = "Erinnerung darf nicht negativ sein")
    @Max(value = 1440, message = "Erinnerung darf höchstens 24 Stunden vorher sein")
    private Integer reminderMinutesBefore;

    private String themeColor;
    private Boolean darkMode;

    // --- Scheduling ---

    @Min(value = 0,  message = "Puffer darf nicht negativ sein")
    @Max(value = 60, message = "Puffer darf höchstens 60 Minuten sein")
    private Integer bufferMinutes;

    @Min(value = 60,   message = "Tageslimit muss mindestens 60 Minuten sein")
    @Max(value = 1440, message = "Tageslimit darf höchstens 24 Stunden sein")
    private Integer maxTaskMinutesPerDay;

    @Min(value = 5,   message = "Mindestblock muss mindestens 5 Minuten sein")
    @Max(value = 480, message = "Mindestblock darf höchstens 8 Stunden sein")
    private Integer defaultMinChunkMinutes;

    @Min(value = 5,   message = "Maximalblock muss mindestens 5 Minuten sein")
    @Max(value = 480, message = "Maximalblock darf höchstens 8 Stunden sein")
    private Integer defaultMaxChunkMinutes;

    private Boolean autoScheduleEnabled;
}
