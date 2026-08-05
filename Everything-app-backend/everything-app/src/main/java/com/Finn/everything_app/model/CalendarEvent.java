package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "calendar_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private EventType eventType = EventType.OTHER;

    @Column(name = "is_fixed")
    private Boolean isFixed = false; // Fixe Events (z.B. Meetings) vs. flexible (Tasks)

    @Column(length = 50)
    private String color;

    @Column(length = 2000)
    private String notes;

    /**
     * Vom Nutzer als erledigt abgehakt; {@code null} heißt offen.
     *
     * Trägt drei Dinge auf einmal: die Anzeige, den Ausschluss aus
     * {@code CalendarEventService.clearScheduledEvents} (ein erledigter Block ist Protokoll,
     * keine Planung, und darf von der nächsten Neuplanung nicht weggeräumt werden) und die
     * Einordnung als eingefrorener Block — er sperrt seine Zeit weiter, zählt aber nicht mehr
     * als gepinnte Minuten, weil seine Minuten bereits gutgeschrieben sind.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_task_id")
    private Task relatedTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_habit_id")
    private Habit relatedHabit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_workout_id")
    private WorkoutSession relatedWorkout;

    // Projekt-Sessions haben bewusst keine eigene Entitaet: die Wochenquote wird bei jedem Lauf
    // neu aus Project.weeklySessionCount gerechnet, der Block hier ist die einzige Kopie seiner
    // Zeit. Deshalb zeigt der Fremdschluessel direkt aufs Projekt.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_project_id")
    private Project relatedProject;

    // Getter und Setter werden von Lombok @Data automatisch generiert

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