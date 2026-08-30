package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Eine stehende Notiz zu einer Uebung - "Bank auf Stufe 3", "Handgelenke wickeln".
 *
 * <p>Bewusst getrennt von {@link RoutineExercise#getNotes()} (gilt nur in einer Routine) und
 * den Notizen an der Einheit (gelten nur an dem Tag). Diese hier taucht bei <em>jedem</em>
 * Training derselben Uebung auf, egal aus welcher Routine.
 */
@Entity
@Table(name = "exercise_notes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exercise_notes_user_exercise",
                columnNames = {"user_id", "exercise_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(name = "text", length = 1000, nullable = false)
    private String text;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
