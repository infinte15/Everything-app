package com.Finn.everything_app.service;

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
public class FlashcardService {

    private final FlashcardRepository flashcardRepository;
    private final FlashcardDeckRepository deckRepository;
    private final FlashcardReviewRepository reviewRepository;
    private final FlashcardDeckService deckService;
    private final CourseService courseService;
    private final AnkiSchedulerService scheduler;

    /** Der Ease-Faktor liegt in der Spalte als Ganzzahl ×100 (250 = 2.5). */
    private static final double EASE_SCALE = 100.0;

    @Transactional
    public Flashcard createCard(Long userId, Flashcard card, Long deckId) {
        FlashcardDeck deck = deckRepository.findByIdAndUserId(deckId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deck nicht gefunden"));

        card.setDeck(deck);
        card.setRepetitionCount(0);
        card.setEasinessFactor(250);        // 2.5
        card.setIntervalDays(0.0);
        card.setLearningStep(0);
        card.setLapses(0);
        card.setNextReviewDate(LocalDateTime.now());   // sofort verfügbar

        Flashcard saved = flashcardRepository.save(card);

        deckService.incrementCardCount(deckId);
        // Course.totalFlashcards stand dauerhaft auf 0, weil dieser Aufruf fehlte.
        if (deck.getCourse() != null) {
            courseService.incrementFlashcardCount(deck.getCourse().getId(), 1);
        }

        return saved;
    }

    /**
     * Karten eines Decks. Rief vorher findByStudyNoteId(deckId) auf — eine komplett andere
     * Spalte, der Endpunkt lieferte deshalb praktisch immer eine leere Liste.
     */
    @Transactional(readOnly = true)
    public List<Flashcard> getCardsByDeck(Long userId, Long deckId) {
        return flashcardRepository.findByDeckIdAndDeckUserId(deckId, userId);
    }

    /** Alle Karten des Nutzers in einer Abfrage — Gegenstück zum N+1 im Frontend-Provider. */
    @Transactional(readOnly = true)
    public List<Flashcard> getAllCards(Long userId) {
        return flashcardRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Flashcard> getDueCards(Long userId) {
        return flashcardRepository.findDueCards(userId, LocalDateTime.now());
    }

    /** Besitz hängt am Deck — Flashcard hat bewusst keine eigene user-Spalte. */
    @Transactional(readOnly = true)
    public Flashcard getCard(Long userId, Long id) {
        return flashcardRepository.findByIdAndDeckUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Flashcard nicht gefunden"));
    }

    @Transactional
    public Flashcard updateCard(Long userId, Long id, Flashcard updatedCard) {
        Flashcard card = getCard(userId, id);

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
     * Bewertet eine Karte und schreibt das Ergebnis fort.
     *
     * Gerechnet wird in {@link AnkiSchedulerService} — hier stehen nur Besitzprüfung,
     * Persistenz und das Protokoll. Der frühere SM-2 an dieser Stelle ist ersatzlos entfallen:
     * er rechnete die Intervallkette rekursiv aus repetitionCount nach, statt das tatsächlich
     * zuletzt vergebene Intervall zu benutzen, wodurch jede Ease-Änderung die gesamte Historie
     * rückwirkend umschrieb.
     */
    @Transactional
    public Flashcard reviewCard(Long userId, Long id, ReviewRating rating) {
        Flashcard card = getCard(userId, id);

        double intervalBefore = card.getIntervalDays();
        AnkiSchedulerService.ScheduledReview scheduled = scheduler.schedule(
                new AnkiSchedulerService.CardState(
                        card.getRepetitionCount(),
                        intervalBefore,
                        card.getLearningStep(),
                        card.getEasinessFactor() / EASE_SCALE,
                        card.getLapses()),
                rating);

        AnkiSchedulerService.CardState next = scheduled.state();
        LocalDateTime now = LocalDateTime.now();

        card.setRepetitionCount(next.repetitions());
        card.setIntervalDays(next.intervalDays());
        card.setLearningStep(next.learningStep());
        card.setLapses(next.lapses());
        card.setEasinessFactor((int) Math.round(next.ease() * EASE_SCALE));
        card.setLastReviewedAt(now);
        card.setNextReviewDate(now.plus(scheduled.nextInterval()));

        Flashcard updated = flashcardRepository.save(card);

        logReview(card, rating, intervalBefore, next, now);
        deckService.updateDeckStatistics(card.getDeck().getId());

        return updated;
    }

    /** Ein Insert pro Bewertung — die Grundlage jeder Lernstatistik. */
    private void logReview(Flashcard card, ReviewRating rating, double intervalBefore,
                           AnkiSchedulerService.CardState next, LocalDateTime reviewedAt) {
        FlashcardReview review = new FlashcardReview();
        review.setFlashcard(card);
        // Der Besitz hängt am Deck; einen eigenen user_id-Weg gibt es auf der Karte bewusst nicht.
        review.setUser(card.getDeck().getUser());
        review.setRating(rating);
        review.setReviewedAt(reviewedAt);
        review.setIntervalDaysBefore(intervalBefore);
        review.setIntervalDaysAfter(next.intervalDays());
        review.setEaseAfter(next.ease());

        reviewRepository.save(review);
    }

    /** Das Protokoll des Nutzers ab einem Zeitpunkt, neueste zuerst. */
    @Transactional(readOnly = true)
    public List<FlashcardReview> getReviewsSince(Long userId, LocalDateTime since) {
        return reviewRepository.findByUserIdAndReviewedAtAfterOrderByReviewedAtDesc(userId, since);
    }

    @Transactional
    public void deleteCard(Long userId, Long id) {
        Flashcard card = getCard(userId, id);
        FlashcardDeck deck = card.getDeck();

        // Zuerst das Protokoll: es hängt per Fremdschlüssel an der Karte.
        reviewRepository.deleteByFlashcardId(id);
        flashcardRepository.delete(card);

        deckService.decrementCardCount(deck.getId());
        if (deck.getCourse() != null) {
            courseService.incrementFlashcardCount(deck.getCourse().getId(), -1);
        }
    }
}
