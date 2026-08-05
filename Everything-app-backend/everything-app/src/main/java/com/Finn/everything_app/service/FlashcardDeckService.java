package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.DeckStatsDTO;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlashcardDeckService {

    private final FlashcardDeckRepository deckRepository;
    private final FlashcardRepository flashcardRepository;
    private final FlashcardReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    @Transactional
    public FlashcardDeck createDeck(Long userId, FlashcardDeck deck, Long courseId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        deck.setUser(user);
        deck.setTotalCards(0);
        deck.setCardsToReview(0);
        deck.setMasteredCards(0);

        if (courseId != null) {
            Course course = courseRepository.findByIdAndUserId(courseId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Kurs nicht gefunden"));
            deck.setCourse(course);
        }

        return deckRepository.save(deck);
    }

    @Transactional(readOnly = true)
    public List<FlashcardDeck> getUserDecks(Long userId) {
        return deckRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /** Einziges Eingangstor für Einzelzugriffe — userId in der Query, nicht als Nachprüfung. */
    @Transactional(readOnly = true)
    public FlashcardDeck getDeck(Long userId, Long id) {
        return deckRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deck nicht gefunden"));
    }

    @Transactional(readOnly = true)
    public List<FlashcardDeck> getDecksByCourse(Long userId, Long courseId) {
        return deckRepository.findByUserIdAndCourseId(userId, courseId);
    }

    @Transactional
    public FlashcardDeck updateDeck(Long userId, Long id, FlashcardDeck updatedDeck) {
        FlashcardDeck deck = getDeck(userId, id);

        if (updatedDeck.getName() != null) {
            deck.setName(updatedDeck.getName());
        }
        if (updatedDeck.getDescription() != null) {
            deck.setDescription(updatedDeck.getDescription());
        }

        return deckRepository.save(deck);
    }

    @Transactional
    public void deleteDeck(Long userId, Long id) {
        FlashcardDeck deck = getDeck(userId, id);
        // Das Löschen kaskadiert per JPA auf die Karten; deren Reviews hängen aber per
        // Fremdschlüssel daran und müssen vorher weg.
        reviewRepository.deleteByDeckId(id);
        deckRepository.delete(deck);
    }

    /**
     * Die Kennzahlen eines Decks, in vier Zählabfragen statt über eine geladene Kartenliste.
     *
     * Die Einteilung entspricht der des Schedulers: neu (nie bewertet), lernend (noch in den
     * Minutenschritten), gereift (der Rest). {@code due} lässt die neuen Karten aus — die
     * Lernwarteschlange addiert beide Töpfe.
     */
    @Transactional(readOnly = true)
    public DeckStatsDTO getDeckStats(Long userId, Long deckId) {
        getDeck(userId, deckId);   // Eigentumsprüfung; wirft bei fremdem Deck

        int total    = (int) flashcardRepository.countByDeckId(deckId);
        int newCards = (int) flashcardRepository.countNewByDeckId(deckId);
        int learning = (int) flashcardRepository.countLearningByDeckId(deckId);

        DeckStatsDTO dto = new DeckStatsDTO();
        dto.setDeckId(deckId);
        dto.setTotal(total);
        dto.setNewCards(newCards);
        dto.setLearning(learning);
        dto.setMature(Math.max(0, total - newCards - learning));
        dto.setDue((int) flashcardRepository.countDueExcludingNewByDeckId(deckId, LocalDateTime.now()));

        return dto;
    }

    /**
     * Zählt alle drei Kennzahlen aus der Datenbank neu.
     *
     * Vorher wurde nur totalCards gesetzt (aus der LAZY-Collection, also einmal komplett
     * geladen), cardsToReview und masteredCards blieben dauerhaft auf 0 — die Deck-Kacheln
     * im Frontend zeigten deshalb immer "0 fällig".
     *
     * Kein userId-Parameter: der Aufrufer hat den Besitz bereits über die Karte geprüft.
     */
    @Transactional
    public void updateDeckStatistics(Long deckId) {
        deckRepository.findById(deckId).ifPresent(deck -> {
            deck.setTotalCards((int) flashcardRepository.countByDeckId(deckId));
            deck.setCardsToReview((int) flashcardRepository.countDueByDeckId(deckId, LocalDateTime.now()));
            deck.setMasteredCards((int) flashcardRepository.countMasteredByDeckId(deckId));
            deck.setLastStudiedAt(LocalDateTime.now());
            deckRepository.save(deck);
        });
    }

    @Transactional
    public void incrementCardCount(Long deckId) {
        adjustCardCount(deckId, +1);
    }

    @Transactional
    public void decrementCardCount(Long deckId) {
        adjustCardCount(deckId, -1);
    }

    private void adjustCardCount(Long deckId, int delta) {
        deckRepository.findById(deckId).ifPresent(deck -> {
            deck.setTotalCards(Math.max(0, deck.getTotalCards() + delta));
            deckRepository.save(deck);
        });
    }
}
