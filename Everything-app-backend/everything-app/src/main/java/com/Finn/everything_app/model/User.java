package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import com.Finn.everything_app.model.*;

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
    private List<FinancialTransaction> financialTransactions;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserPreferences preferences;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
