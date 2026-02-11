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

    @Column(name = "easiness_factor")
    private Integer easinessFactor = 250;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


}