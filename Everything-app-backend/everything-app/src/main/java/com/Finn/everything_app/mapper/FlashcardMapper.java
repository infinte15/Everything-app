package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.FlashcardDTO;
import com.Finn.everything_app.model.Flashcard;
import org.springframework.stereotype.Component;

@Component
public class FlashcardMapper {

    /** EF wird als Ganzzahl x100 gespeichert; nach außen ist es der übliche Faktor (2.5). */
    private static final double EASE_SCALE = 100.0;

    public FlashcardDTO toDTO(Flashcard card) {
        if (card == null) return null;

        FlashcardDTO dto = new FlashcardDTO();
        dto.setId(card.getId());
        dto.setQuestion(card.getQuestion());
        dto.setAnswer(card.getAnswer());
        dto.setDeckId(card.getDeck() != null ? card.getDeck().getId() : null);
        dto.setDeckName(card.getDeck() != null ? card.getDeck().getName() : null);
        dto.setCategory(card.getCategory());
        dto.setDifficulty(card.getDifficulty());
        dto.setRepetitionCount(card.getRepetitionCount());
        dto.setEaseFactor(card.getEasinessFactor() != null
                ? card.getEasinessFactor() / EASE_SCALE
                : null);
        dto.setIntervalDays(card.getIntervalDays());
        dto.setLearningStep(card.getLearningStep());
        dto.setLapses(card.getLapses());
        dto.setNextReviewDate(card.getNextReviewDate());
        dto.setLastReviewedAt(card.getLastReviewedAt());
        dto.setTags(card.getTags());
        dto.setCreatedAt(card.getCreatedAt());
        dto.setUpdatedAt(card.getUpdatedAt());

        return dto;
    }

    public Flashcard toEntity(FlashcardDTO dto) {
        if (dto == null) return null;

        Flashcard card = new Flashcard();
        card.setId(dto.getId());
        card.setQuestion(dto.getQuestion());
        card.setAnswer(dto.getAnswer());
        card.setCategory(dto.getCategory());
        card.setDifficulty(dto.getDifficulty());
        card.setTags(dto.getTags());
        // Der Wiederholungszustand wird bewusst NICHT aus dem DTO übernommen. Er gehört dem
        // Server; sonst könnte ein Client sich seine Intervalle selbst setzen.

        return card;
    }
}