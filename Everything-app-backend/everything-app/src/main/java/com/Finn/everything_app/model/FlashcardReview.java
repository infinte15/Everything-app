package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Ein einzelnes Review — das Protokoll hinter den Lernstatistiken.
 *
 * Ohne diese Zeilen gäbe es kein „heute X Karten gelernt", keine Retention-Kurve und kein
 * Rückgängig: die Karte selbst kennt nur ihren aktuellen Zustand, nicht ihren Weg dorthin.
 * Kostet einen Insert pro Bewertung.
 *
 * Das Intervall wird VOR und NACH der Bewertung festgehalten, weil erst die Differenz zeigt,
 * ob eine Karte gewachsen oder zurückgefallen ist.
 */
@Entity
@Table(name = "flashcard_reviews")
@Data
public class FlashcardReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flashcard_id", nullable = false)
    private Flashcard flashcard;

    // Redundant zu flashcard.deck.user, aber die Auswertung filtert immer nach Nutzer und
    // Zeitraum — ohne die Spalte hinge an jeder Statistik ein Doppel-Join.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReviewRating rating;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    @Column(name = "interval_days_before")
    private Double intervalDaysBefore;

    @Column(name = "interval_days_after")
    private Double intervalDaysAfter;

    @Column(name = "ease_after")
    private Double easeAfter;

    @PrePersist
    protected void onCreate() {
        if (reviewedAt == null) {
            reviewedAt = LocalDateTime.now();
        }
    }
}
