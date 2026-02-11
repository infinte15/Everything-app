package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.FlashcardDeckDTO;
import com.Finn.everything_app.model.FlashcardDeck;
import org.springframework.stereotype.Component;

@Component
public class FlashcardDeckMapper {

    public FlashcardDeckDTO toDTO(FlashcardDeck deck) {
        if (deck == null) return null;

        FlashcardDeckDTO dto = new FlashcardDeckDTO();
        dto.setId(deck.getId());
        dto.setName(deck.getName());
        dto.setDescription(deck.getDescription());
        dto.setCourseId(deck.getCourse() != null ? deck.getCourse().getId() : null);
        dto.setCourseName(deck.getCourse() != null ? deck.getCourse().getName() : null);
        dto.setTotalCards(deck.getTotalCards());
        dto.setCardsToReview(deck.getCardsToReview());
        dto.setMasteredCards(deck.getMasteredCards());
        dto.setCreatedAt(deck.getCreatedAt());
        dto.setUpdatedAt(deck.getUpdatedAt());
        dto.setLastStudiedAt(deck.getLastStudiedAt());

        return dto;
    }

    public FlashcardDeck toEntity(FlashcardDeckDTO dto) {
        if (dto == null) return null;

        FlashcardDeck deck = new FlashcardDeck();
        deck.setId(dto.getId());
        deck.setName(dto.getName());
        deck.setDescription(dto.getDescription());

        return deck;
    }
}