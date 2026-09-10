package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.ProductivityPeakTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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

    /** Deckel über ALLES, was pro Tag automatisch geplant wird — nicht nur über die Aufgaben. */
    @Min(value = 60,   message = "Tagesdeckel muss mindestens 60 Minuten sein")
    @Max(value = 1440, message = "Tagesdeckel darf höchstens 24 Stunden sein")
    private Integer maxScheduledMinutesPerDay;

    /** Ende der Kernzeit; danach geplante Aufgaben kosten im Ziel (Abendstrafe). */
    private LocalTime coreHoursEnd;

    /**
     * Arbeitstage als ISO-Nummern, z. B. {@code "1,2,3,4,5"} für Montag bis Freitag.
     *
     * <p>{@code null} heißt "nicht mitgeschickt" und lässt die Einstellung unverändert; der
     * LEERSTRING heißt "alle sieben Tage" und ist der Weg, sie wieder zu entfernen —
     * {@code updatePreferences} übernimmt nur Felder ungleich null, mit {@code null} ließe sich
     * eine einmal gesetzte Auswahl also nie wieder zurücknehmen.
     */
    @Pattern(regexp = "|[1-7](,[1-7])*", message = "Arbeitstage müssen ISO-Tagesnummern sein, z. B. 1,2,3,4,5")
    private String workDays;

    /**
     * Privatzeiten — der Rahmen für Gewohnheiten und Trainings, getrennt von der Arbeitszeit.
     * {@code null} heißt Rückfall auf 06:00–23:00 im Scheduler.
     */
    private LocalTime personalHoursStart;
    private LocalTime personalHoursEnd;

    @Min(value = 5,   message = "Mindestblock muss mindestens 5 Minuten sein")
    @Max(value = 480, message = "Mindestblock darf höchstens 8 Stunden sein")
    private Integer defaultMinChunkMinutes;

    @Min(value = 5,   message = "Maximalblock muss mindestens 5 Minuten sein")
    @Max(value = 480, message = "Maximalblock darf höchstens 8 Stunden sein")
    private Integer defaultMaxChunkMinutes;

    /** Wie viele Stunden vor der Deadline eine Aufgabe fertig sein soll. */
    @Min(value = 0,   message = "Puffer darf nicht negativ sein")
    @Max(value = 168, message = "Puffer darf höchstens eine Woche sein")
    private Integer deadlineBufferHours;

    private Boolean autoScheduleEnabled;

    /** Angestrebtes Koerpergewicht in kg, oder null. */
    private Double targetWeightKg;
}
