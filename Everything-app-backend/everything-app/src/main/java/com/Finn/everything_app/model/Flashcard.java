package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "flashcards")
@Data
public class Flashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String question;

    @Column(nullable = false, length = 1000)
    private String answer;

    @Column(length = 100)
    private String category;

    @Column(length = 50)
    private String difficulty;

    @Column(length = 500)
    private String tags;

    // Spaced Repetition Algorithmus
    @Column(name = "repetition_count")
    private Integer repetitionCount = 0;

    // EF mal 100 als Ganzzahl. Bleibt so — das ist ein Speicherdetail, nach außen liefert das
    // DTO ein Double (siehe FlashcardMapper).
    @Column(name = "easiness_factor")
    private Integer easinessFactor = 250;

    // Das TATSÄCHLICH zuletzt vergebene Intervall. Vorher wurde es aus repetitionCount
    // rekursiv nachgerechnet, wodurch jede Ease-Änderung rückwirkend die ganze Historie
    // umschrieb. Bestandszeilen haben hier NULL — Leser müssen das auf 0 abbilden.
    @Column(name = "interval_days")
    private Double intervalDays = 0.0;

    // Position in den Lernschritten (1 Min / 6 Min), bevor die Karte in den Tagesrhythmus geht.
    @Column(name = "learning_step")
    private Integer learningStep = 0;

    // Wie oft die Karte nach dem Lernen wieder vergessen wurde.
    @Column(name = "lapses")
    private Integer lapses = 0;

    @Column(name = "next_review_date")
    private LocalDateTime nextReviewDate;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", nullable = false)
    private FlashcardDeck deck;

    @ManyToOne
    @JoinColumn(name = "study_note_id")
    private StudyNote studyNote;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (nextReviewDate == null) {
            nextReviewDate = LocalDateTime.now();
        }
        normalizeSrsDefaults();
    }

    /**
     * Bestandskarten haben nach ddl-auto=update NULL in den neuen Spalten — der
     * Feldinitialisierer greift nur bei frisch konstruierten Objekten, nicht bei geladenen
     * Zeilen. Ohne diese Normalisierung fliegt beim ersten Review eine NPE beim Unboxing.
     * Deshalb @PostLoad und nicht @PreUpdate: gebraucht wird der Wert beim LESEN.
     */
    @PostLoad
    protected void normalizeSrsDefaults() {
        if (intervalDays == null)  intervalDays  = 0.0;
        if (learningStep == null)  learningStep  = 0;
        if (lapses == null)        lapses        = 0;
        if (repetitionCount == null) repetitionCount = 0;
        if (easinessFactor == null)  easinessFactor  = 250;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


}