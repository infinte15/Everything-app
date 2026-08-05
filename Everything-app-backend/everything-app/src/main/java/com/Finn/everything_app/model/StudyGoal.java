package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ein wöchentliches Lernziel für ein Modul, z.B. „5 Stunden Analysis I pro Woche".
 *
 * Zwei Entwurfsentscheidungen, die man beim Lesen sonst für Versehen hielte:
 *
 * <p><b>Das Modul ist Pflicht.</b> Vorher war das Fach ein Freitext, der sich an nichts
 * anschließen ließ: „Analysis" und „Analysis I" waren zwei Ziele, und weder Farbe noch
 * Notenschnitt noch Karteikarten des Moduls waren erreichbar. Über {@link Course} kommt all
 * das gratis mit.
 *
 * <p><b>Keine Wochenhistorie.</b> Das Ziel steht dauerhaft; erfasst wird nur die laufende
 * Woche, markiert durch {@code loggedWeekStart}. Liegt der Stempel nicht auf diesem Montag,
 * gelten die erfassten Stunden als 0 (siehe {@code StudyGoalService.rollOver}). Eine Zeile je
 * Woche zwänge entweder zu einem leeren Board an jedem Montag oder zu einem GET mit
 * Nebenwirkung — beides schlechter als der Verzicht auf die Historie.
 */
@Entity
@Table(name = "study_goals", indexes = {
        @Index(name = "idx_study_goals_user", columnList = "user_id")
})
@Data
public class StudyGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Deko in der Oberfläche; Name und Farbe kommen aus dem Modul. */
    @Column(length = 8)
    private String emoji;

    @Column(name = "weekly_goal_hours", nullable = false)
    private Double weeklyGoalHours;

    /** Gilt für {@link #loggedWeekStart}; in einer neuen Woche wieder 0. */
    @Column(name = "logged_hours", nullable = false)
    private Double loggedHours = 0.0;

    /** Montag der Woche, auf die sich {@link #loggedHours} bezieht. */
    @Column(name = "logged_week_start")
    private LocalDate loggedWeekStart;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /**
     * Die Brücke in den Scheduler. Der Solver kennt nur Tasks, Habits und Workouts — über
     * diesen Task landen die Reststunden im Kalender, ohne dass CP-SAT einen neuen Itemtyp
     * mit eigenen Gewichten, Tagesfenstern und At-Risk-Abbildung bräuchte.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        normalizeDefaults();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @PostLoad
    protected void normalizeDefaults() {
        if (loggedHours == null) loggedHours = 0.0;
        if (emoji == null || emoji.isBlank()) emoji = "📚";
    }

    /** Was diese Woche noch zu tun ist; nie negativ. */
    @Transient
    public double getRemainingHours() {
        double goal = weeklyGoalHours != null ? weeklyGoalHours : 0.0;
        double done = loggedHours != null ? loggedHours : 0.0;
        return Math.max(0.0, goal - done);
    }
}
