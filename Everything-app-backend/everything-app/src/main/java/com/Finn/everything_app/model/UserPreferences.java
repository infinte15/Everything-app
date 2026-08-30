package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
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

    /**
     * Privatzeiten — der Rahmen für Gewohnheiten und Trainings (Reclaims "Personal Hours").
     *
     * Getrennt von {@code workdayStart}/{@code workdayEnd}, weil beides verschiedene Fragen
     * beantwortet: die Arbeitszeit sagt, wann Aufgaben liegen dürfen, die Privatzeit, wann der Tag
     * überhaupt für einen selbst verfügbar ist.
     *
     * Ohne diese Trennung waren Gewohnheiten hart auf die Arbeitszeit geklemmt, und damit war ihr
     * Wunschfenster für einen normalen Arbeitstag von 08:00–17:00 schlicht unerreichbar:
     * {@link HabitWindow#MORNING} beginnt um 06:00, {@link HabitWindow#EVENING} liegt mit
     * 17:00–22:00 vollständig dahinter. "Vor dem Schlafen lesen" wurde deshalb um 15 Uhr geplant.
     *
     * {@code null} heißt Rückfall auf 06:00–23:00 (siehe {@code SmartSchedulerService}).
     */
    @Column(name = "personal_hours_start")
    private LocalTime personalHoursStart;

    @Column(name = "personal_hours_end")
    private LocalTime personalHoursEnd;


    @Enumerated(EnumType.STRING)
    private ProductivityPeakTime peakProductivityTime;


    private Integer breakDurationMinutes;

    /**
     * Obergrenze für die ANZAHL automatisch verplanter Aufgabenblöcke pro Tag.
     *
     * Sieht auf den ersten Blick aus wie die entfernten {@code hoursBeforeBreak} und
     * {@code groupSimilarTasks} — ist es aber nicht: {@code addDailyLoadLimits} im
     * {@code SmartSchedulerService} wertet dieses Feld aus. Vor dem Aufräumen also nachsehen,
     * nicht nach Gefühl entscheiden.
     */
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

    /**
     * Obergrenze für ALLE automatisch verplante Zeit pro Kalendertag — Aufgaben, Gewohnheiten,
     * Trainings und Projektzeit zusammen.
     *
     * {@link #maxTaskMinutesPerDay} deckelt nur die Aufgaben. Gewohnheiten, Trainings und
     * Projektsitzungen liefen bisher an jedem Deckel vorbei und konnten einen Tag füllen, bevor
     * die Aufgaben überhaupt an die Reihe kamen.
     */
    @Column(name = "max_scheduled_minutes_per_day")
    private Integer maxScheduledMinutesPerDay;

    /**
     * Ende der Kernzeit. Aufgaben dahinter sind erlaubt, kosten aber im Ziel (Abendstrafe).
     *
     * Abgrenzung zu {@code workdayEnd}: das ist die harte Grenze, hinter der gar nichts mehr
     * geplant wird. Wer bis 22 Uhr arbeiten KANN, will nicht, dass um 21 Uhr geplant wird, solange
     * vormittags noch Platz ist.
     */
    @Column(name = "core_hours_end")
    private LocalTime coreHoursEnd;

    /** Untergrenze für einen einzelnen Task-Block beim Aufteilen. */
    @Column(name = "default_min_chunk_minutes")
    private Integer defaultMinChunkMinutes;

    /** Obergrenze für einen einzelnen Task-Block beim Aufteilen. */
    @Column(name = "default_max_chunk_minutes")
    private Integer defaultMaxChunkMinutes;

    /**
     * Wie viele Stunden VOR der Deadline eine Aufgabe fertig sein soll.
     *
     * Der Hauptlauf plant gegen {@code deadline - dieser Puffer}, nicht gegen die Deadline selbst:
     * "geplant" und "auf Kante genäht" waren vorher dasselbe, ein Block durfte bis zur letzten
     * Minute laufen. Passt es mit Puffer nicht mehr, greift der Nachlauf (CATCH_UP) und rechnet mit
     * der echten Deadline — der Puffer ist also ein Ziel, keine zusätzliche harte Grenze, und er
     * erzeugt für sich genommen nie eine Warnung.
     */
    @Column(name = "deadline_buffer_hours")
    private Integer deadlineBufferHours;

    /** Schaltet die automatische Neuplanung komplett ab. */
    @Column(name = "auto_schedule_enabled")
    private Boolean autoScheduleEnabled;

    /**
     * Tag des letzten Scheduler-Laufs, damit das rollierende Planungsfenster nachgeholt werden
     * kann (siehe ScheduleRollForwardScheduler).
     *
     * Der nächtliche Cron allein reicht nicht: läuft der Rechner um drei Uhr nicht, wird das
     * Fenster nie weitergeschoben und die letzte Woche des Plans läuft still leer. Mit diesem
     * Datum erkennt der Nachzügler-Sweep genau den Fall — unabhängig davon, wie lange die
     * Anwendung aus war.
     */
    @Column(name = "last_schedule_run_date")
    private LocalDate lastScheduleRunDate;

    /**
     * Angestrebtes Koerpergewicht in Kilogramm, oder null wenn keins gesetzt ist.
     *
     * <p>Liegt hier und nicht am Gewichtsverlauf: es ist eine Einstellung, kein Messwert. Der
     * Gym-Space zeichnet daraus die Ziellinie im Diagramm und den Abstand darunter.
     *
     * <p>Gesetzt wird es ueber {@code PUT /api/sports/bodyweight/target}, bewusst nicht ueber
     * {@code UserService.updatePreferences}: das uebernimmt nur Felder, die nicht null sind, und
     * koennte "Ziel entfernen" damit nie von "Feld nicht mitgeschickt" unterscheiden.
     */
    @Column(name = "target_weight_kg")
    private Double targetWeightKg;
}

