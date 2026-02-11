package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlashcardService {

    private final FlashcardRepository flashcardRepository;
    private final FlashcardDeckRepository deckRepository;
    private final FlashcardDeckService deckService;

    @Transactional
    public Flashcard createCard(Long userId, Flashcard card, Long deckId) {
        FlashcardDeck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> new RuntimeException("Deck nicht gefunden"));

        card.setDeck(deck);
        card.setRepetitionCount(0);
        card.setEasinessFactor(250); // Start: 2.5
        card.setNextReviewDate(LocalDateTime.now()); // Sofort verfügbar

        Flashcard saved = flashcardRepository.save(card);

        // Update Deck Statistics
        deckService.incrementCardCount(deckId);

        return saved;
    }

    public List<Flashcard> getCardsByDeck(Long deckId) {
        return flashcardRepository.findByStudyNoteId(deckId);
    }

    public List<Flashcard> getDueCards(Long userId) {
        return flashcardRepository.findDueCards(
                userId, LocalDateTime.now()
        );
    }

    public Flashcard getCardById(Long id) {
        return flashcardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Flashcard nicht gefunden"));
    }

    @Transactional
    public Flashcard updateCard(Long id, Flashcard updatedCard) {
        Flashcard card = getCardById(id);

        if (updatedCard.getQuestion() != null) {
            card.setQuestion(updatedCard.getQuestion());
        }
        if (updatedCard.getAnswer() != null) {
            card.setAnswer(updatedCard.getAnswer());
        }
        if (updatedCard.getCategory() != null) {
            card.setCategory(updatedCard.getCategory());
        }
        if (updatedCard.getDifficulty() != null) {
            card.setDifficulty(updatedCard.getDifficulty());
        }
        if (updatedCard.getTags() != null) {
            card.setTags(updatedCard.getTags());
        }

        return flashcardRepository.save(card);
    }

    /**
     * SPACED REPETITION ALGORITHMUS (SM-2)
     *
     * @param id Card ID
     * @param quality Qualität: "AGAIN" (0), "HARD" (1), "MEDIUM" (2), "EASY" (3)
     * @return Updated Flashcard
     */
    @Transactional
    public Flashcard reviewCard(Long id, String quality) {
        Flashcard card = getCardById(id);

        int q = convertQualityToNumber(quality);

        // SM-2 Algorithmus
        int repetitions = card.getRepetitionCount();
        double ef = card.getEasinessFactor() / 100.0; // Convert to 0-4 scale

        // Neue Easiness Factor berechnen
        ef = ef + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));

        if (ef < 1.3) ef = 1.3;
        if (ef > 2.5) ef = 2.5;

        // Interval berechnen
        int interval; // in Tagen

        if (q < 3) {
            // Falsch beantwortet - zurück auf Start
            repetitions = 0;
            interval = 0; // Sofort wieder
        } else {
            // Richtig beantwortet
            repetitions++;

            if (repetitions == 1) {
                interval = 1;
            } else if (repetitions == 2) {
                interval = 6;
            } else {
                // interval(n) = interval(n-1) * EF
                interval = (int) Math.round(getPreviousInterval(repetitions - 1, ef) * ef);
            }
        }

        // Update Card
        card.setRepetitionCount(repetitions);
        card.setEasinessFactor((int) (ef * 100));
        card.setLastReviewedAt(LocalDateTime.now());
        card.setNextReviewDate(LocalDateTime.now().plusDays(interval));

        Flashcard updated = flashcardRepository.save(card);

        // Update Deck Statistics
        deckService.updateDeckStatistics(card.getDeck().getId());

        return updated;
    }

    private int convertQualityToNumber(String quality) {
        switch (quality.toUpperCase()) {
            case "AGAIN": return 0;
            case "HARD": return 1;
            case "MEDIUM": return 2;
            case "EASY": return 3;
            default: return 2;
        }
    }

    private int getPreviousInterval(int repetition, double ef) {
        if (repetition == 1) return 1;
        if (repetition == 2) return 6;

        // Rekursive Berechnung
        return (int) Math.round(getPreviousInterval(repetition - 1, ef) * ef);
    }

    @Transactional
    public void deleteCard(Long id) {
        Flashcard card = getCardById(id);
        Long deckId = card.getDeck().getId();

        flashcardRepository.delete(card);

        // Update Deck Statistics
        deckService.decrementCardCount(deckId);
    }
}