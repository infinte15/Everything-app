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
public class FlashcardDeckService {

    private final FlashcardDeckRepository deckRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    @Transactional
    public FlashcardDeck createDeck(Long userId, FlashcardDeck deck, Long courseId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        deck.setUser(user);
        deck.setTotalCards(0);
        deck.setCardsToReview(0);
        deck.setMasteredCards(0);

        if (courseId != null) {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new RuntimeException("Kurs nicht gefunden"));
            deck.setCourse(course);
        }

        return deckRepository.save(deck);
    }

    public List<FlashcardDeck> getUserDecks(Long userId) {
        return deckRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public FlashcardDeck getDeckById(Long id) {
        return deckRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Deck nicht gefunden"));
    }

    public List<FlashcardDeck> getDecksByCourse(Long userId, Long courseId) {
        return deckRepository.findByUserIdAndCourseId(userId, courseId);
    }

    @Transactional
    public FlashcardDeck updateDeck(Long id, FlashcardDeck updatedDeck) {
        FlashcardDeck deck = getDeckById(id);

        if (updatedDeck.getName() != null) {
            deck.setName(updatedDeck.getName());
        }
        if (updatedDeck.getDescription() != null) {
            deck.setDescription(updatedDeck.getDescription());
        }

        return deckRepository.save(deck);
    }

    @Transactional
    public void deleteDeck(Long id) {
        FlashcardDeck deck = getDeckById(id);
        deckRepository.delete(deck);
    }

    @Transactional
    public void updateDeckStatistics(Long deckId) {
        FlashcardDeck deck = getDeckById(deckId);

        int totalCards = deck.getFlashcards() != null ? deck.getFlashcards().size() : 0;

        deck.setTotalCards(totalCards);
        deck.setLastStudiedAt(LocalDateTime.now());

        deckRepository.save(deck);
    }

    @Transactional
    public void incrementCardCount(Long deckId) {
        FlashcardDeck deck = getDeckById(deckId);
        deck.setTotalCards(deck.getTotalCards() + 1);
        deckRepository.save(deck);
    }

    @Transactional
    public void decrementCardCount(Long deckId) {
        FlashcardDeck deck = getDeckById(deckId);
        deck.setTotalCards(Math.max(0, deck.getTotalCards() - 1));
        deckRepository.save(deck);
    }
}