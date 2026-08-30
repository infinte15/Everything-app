package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "habits")
@Data
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HabitFrequency frequency = HabitFrequency.DAILY;


    private Boolean monday = false;
    private Boolean tuesday = false;
    private Boolean wednesday = false;
    private Boolean thursday = false;
    private Boolean friday = false;
    private Boolean saturday = false;
    private Boolean sunday = false;

    @Column(name = "preferred_time")
    private LocalTime preferredTime;

    // --- Flexible Planung (Reclaim-Stil) ---
    // Alle nullable. Ist timesPerWeek null, gilt weiterhin exakt das alte Verhalten:
    // eine Pflicht-Ausführung pro gesetztem Wochentag-Flag, nur an genau diesem Tag.

    /** "N mal pro Woche" — der Solver sucht sich die Tage selbst aus. */
    @Column(name = "times_per_week")
    private Integer timesPerWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "ideal_window")
    private HabitWindow idealWindow;

    @Column(name = "ideal_window_start")
    private LocalTime idealWindowStart;

    @Column(name = "ideal_window_end")
    private LocalTime idealWindowEnd;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "current_streak")
    private Integer currentStreak = 0;

    @Column(name = "longest_streak")
    private Integer longestStreak = 0;

    private String color;
    private Integer priority;
    private String category;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "habit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HabitCompletion> completions;

    /**
     * Die Gym-Routine, aus der diese Gewohnheit entstanden ist - sonst {@code null}.
     *
     * <p>Weist man im Gym-Space einer Routine einen Wochentag zu, ist das fachlich eine
     * Gewohnheit ("montags Push"), und sie gehoert in den Habit-Space samt Streak. Angelegt und
     * gepflegt wird sie von {@link com.Finn.everything_app.service.RoutineHabitService}, nicht
     * von Hand.
     *
     * <p><b>Wichtig fuer den Planer:</b> eine Gewohnheit mit Routine wird <em>nicht</em>
     * eingeplant. Die Zeit im Kalender belegt der Workout-Platzhalter derselben Routine; beides
     * zu planen hiesse, denselben Termin zweimal in die Woche zu legen. Siehe
     * {@code SmartSchedulerService.loadInput}.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", unique = true)
    private Routine routine;

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

