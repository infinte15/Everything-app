package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.FlashcardReviewDTO;
import com.Finn.everything_app.model.FlashcardReview;
import org.springframework.stereotype.Component;

@Component
public class FlashcardReviewMapper {

    /** Nur eine Richtung: das Protokoll schreibt ausschließlich der Server. */
    public FlashcardReviewDTO toDTO(FlashcardReview review) {
        if (review == null) return null;

        FlashcardReviewDTO dto = new FlashcardReviewDTO();
        dto.setId(review.getId());
        dto.setFlashcardId(review.getFlashcard() != null ? review.getFlashcard().getId() : null);
        dto.setDeckId(review.getFlashcard() != null && review.getFlashcard().getDeck() != null
                ? review.getFlashcard().getDeck().getId()
                : null);
        dto.setRating(review.getRating() != null ? review.getRating().name() : null);
        dto.setReviewedAt(review.getReviewedAt());
        dto.setIntervalDaysBefore(review.getIntervalDaysBefore());
        dto.setIntervalDaysAfter(review.getIntervalDaysAfter());
        dto.setEaseAfter(review.getEaseAfter());

        return dto;
    }
}
