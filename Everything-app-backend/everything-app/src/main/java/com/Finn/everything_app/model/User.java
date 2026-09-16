package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;


import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data

public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    /**
     * Zaehler fuer den Token-Widerruf. Jedes ausgestellte JWT traegt den Stand, der beim
     * Ausstellen galt (Claim {@code tv}). {@code POST /api/auth/logout-all} zaehlt ihn hoch
     * und entwertet damit in einem Schritt alle vorher ausgegebenen Token - ohne Sperrliste
     * und ohne Wechsel von {@code jwt.secret}, der jeden Nutzer zugleich abmelden wuerde.
     *
     * <p>Das ist die Bedingung, unter der eine Laufzeit von 30 Tagen vertretbar ist: ein
     * verlorenes Geraet laesst sich einzeln aussperren.
     *
     * <p>{@code columnDefinition} mit DEFAULT ist noetig, weil {@code ddl-auto=update} die
     * Spalte sonst als NOT NULL ohne Vorgabewert an eine bereits gefuellte Tabelle haengt -
     * und Postgres das mit "column contains null values" ablehnt.
     */
    @Column(name = "token_version", nullable = false, columnDefinition = "integer default 0")
    private int tokenVersion = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Task> tasks;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<CalendarEvent> calendarEvents;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Habit> habits;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<StudyNote> studyNotes;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Project> projects;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<WorkoutPlan> workoutPlans;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Recipe> recipes;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<FinanceTransaction> financialTransactions;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserPreferences preferences;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
