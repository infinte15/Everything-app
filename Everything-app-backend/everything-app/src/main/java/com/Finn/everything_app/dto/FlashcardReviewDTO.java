package com.Finn.everything_app.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** Eine protokollierte Bewertung. Reines Lese-DTO — Reviews entstehen nur über den Review-Endpunkt. */
@Data
public class FlashcardReviewDTO {
    private Long id;
    private Long flashcardId;
    private Long deckId;

    /** AGAIN, HARD, GOOD oder EASY. */
    private String rating;

    private LocalDateTime reviewedAt;

    // Vorher/nachher, damit die Auswertung Wachstum von Rückfall unterscheiden kann.
    private Double intervalDaysBefore;
    private Double intervalDaysAfter;
    private Double easeAfter;
}
