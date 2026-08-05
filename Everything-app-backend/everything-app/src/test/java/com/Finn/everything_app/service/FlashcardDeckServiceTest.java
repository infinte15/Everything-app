package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.FlashcardDeck;
import com.Finn.everything_app.repository.CourseRepository;
import com.Finn.everything_app.repository.FlashcardDeckRepository;
import com.Finn.everything_app.dto.DeckStatsDTO;
import com.Finn.everything_app.repository.FlashcardRepository;
import com.Finn.everything_app.repository.FlashcardReviewRepository;
import com.Finn.everything_app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlashcardDeckServiceTest {

    @Mock FlashcardDeckRepository deckRepository;
    @Mock FlashcardRepository flashcardRepository;
    @Mock FlashcardReviewRepository reviewRepository;
    @Mock UserRepository userRepository;
    @Mock CourseRepository courseRepository;

    @InjectMocks FlashcardDeckService service;

    private FlashcardDeck deck(long id) {
        FlashcardDeck d = new FlashcardDeck();
        d.setId(id);
        d.setName("Analysis");
        d.setTotalCards(0);
        d.setCardsToReview(0);
        d.setMasteredCards(0);
        return d;
    }

    @Test
    void getDeckOfAnotherUserIsNotFound() {
        when(deckRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getDeck(1L, 7L));
    }

    // Regression: updateDeckStatistics setzte nur totalCards. cardsToReview und masteredCards
    // blieben dauerhaft auf 0, weshalb jede Deck-Kachel im Frontend "0 faellig" anzeigte.
    @Test
    void updateDeckStatisticsFillsAllThreeCounters() {
        when(deckRepository.findById(4L)).thenReturn(Optional.of(deck(4L)));
        when(flashcardRepository.countByDeckId(4L)).thenReturn(12L);
        when(flashcardRepository.countDueByDeckId(eq(4L), any(LocalDateTime.class))).thenReturn(5L);
        when(flashcardRepository.countMasteredByDeckId(4L)).thenReturn(3L);

        service.updateDeckStatistics(4L);

        ArgumentCaptor<FlashcardDeck> saved = ArgumentCaptor.forClass(FlashcardDeck.class);
        verify(deckRepository).save(saved.capture());
        assertEquals(12, saved.getValue().getTotalCards());
        assertEquals(5,  saved.getValue().getCardsToReview(), "faellige Karten wurden nie gesetzt");
        assertEquals(3,  saved.getValue().getMasteredCards(), "gemeisterte Karten wurden nie gesetzt");
    }

    @Test
    void deckStatsOfAnotherUserIsNotFound() {
        when(deckRepository.findByIdAndUserId(4L, 2L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getDeckStats(2L, 4L));
        verify(flashcardRepository, never()).countByDeckId(anyLong());
    }

    // "Gereift" ist der Rest: was weder neu noch in der Lernphase ist. Damit gehen die drei
    // Toepfe immer auf die Gesamtzahl auf, egal wie die Einzelabfragen ausfallen.
    @Test
    void deckStatsDeriveMatureFromTheRemainder() {
        when(deckRepository.findByIdAndUserId(4L, 1L)).thenReturn(Optional.of(deck(4L)));
        when(flashcardRepository.countByDeckId(4L)).thenReturn(12L);
        when(flashcardRepository.countNewByDeckId(4L)).thenReturn(5L);
        when(flashcardRepository.countLearningByDeckId(4L)).thenReturn(3L);
        when(flashcardRepository.countDueExcludingNewByDeckId(eq(4L), any(LocalDateTime.class)))
                .thenReturn(2L);

        DeckStatsDTO stats = service.getDeckStats(1L, 4L);

        assertEquals(12, stats.getTotal());
        assertEquals(5,  stats.getNewCards());
        assertEquals(3,  stats.getLearning());
        assertEquals(4,  stats.getMature(), "12 - 5 neu - 3 lernend");
        assertEquals(2,  stats.getDue(), "faellig zaehlt ohne die neuen Karten");
    }

    // Deck loeschen kaskadiert per JPA auf die Karten; das Review-Protokoll haengt per
    // Fremdschluessel daran und wird von keiner Kaskade erfasst.
    @Test
    void deletingADeckRemovesTheReviewLogFirst() {
        FlashcardDeck d = deck(4L);
        when(deckRepository.findByIdAndUserId(4L, 1L)).thenReturn(Optional.of(d));

        service.deleteDeck(1L, 4L);

        InOrder order = inOrder(reviewRepository, deckRepository);
        order.verify(reviewRepository).deleteByDeckId(4L);
        order.verify(deckRepository).delete(d);
    }

    @Test
    void createDeckRejectsAForeignCourse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new com.Finn.everything_app.model.User()));
        when(courseRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createDeck(1L, deck(0L), 99L));
        verify(deckRepository, never()).save(any());
    }
}
