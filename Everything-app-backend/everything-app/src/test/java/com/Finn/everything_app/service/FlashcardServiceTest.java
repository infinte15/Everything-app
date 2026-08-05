package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.Flashcard;
import com.Finn.everything_app.model.FlashcardDeck;
import com.Finn.everything_app.model.FlashcardReview;
import com.Finn.everything_app.model.ReviewRating;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.FlashcardDeckRepository;
import com.Finn.everything_app.repository.FlashcardRepository;
import com.Finn.everything_app.repository.FlashcardReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlashcardServiceTest {

    @Mock FlashcardRepository flashcardRepository;
    @Mock FlashcardDeckRepository deckRepository;
    @Mock FlashcardReviewRepository reviewRepository;
    @Mock FlashcardDeckService deckService;
    @Mock CourseService courseService;

    // Kein Mock: der Planer ist reine Rechnung ohne Abhaengigkeiten und wird eigenstaendig in
    // AnkiSchedulerServiceTest geprueft. Als Attrappe wuerde er hier nur null liefern.
    @Spy AnkiSchedulerService scheduler = new AnkiSchedulerService();

    @InjectMocks FlashcardService service;

    private FlashcardDeck deck(long id) {
        FlashcardDeck d = new FlashcardDeck();
        d.setId(id);
        d.setName("Analysis");
        User u = new User();
        u.setId(1L);
        d.setUser(u);
        return d;
    }

    private Flashcard card(long id, int repetitions, double intervalDays, int ease, int learningStep) {
        Flashcard c = new Flashcard();
        c.setId(id);
        c.setQuestion("Was ist eine Ableitung?");
        c.setAnswer("Steigung");
        c.setDeck(deck(1L));
        c.setRepetitionCount(repetitions);
        c.setIntervalDays(intervalDays);
        c.setEasinessFactor(ease);
        c.setLearningStep(learningStep);
        c.setLapses(0);
        return c;
    }

    /** Eine gereifte Karte: aus der Lernphase heraus (Schritt 2). */
    private Flashcard card(long id, int repetitions, double intervalDays, int ease) {
        return card(id, repetitions, intervalDays, ease, 2);
    }

    // Regression: getCardsByDeck rief findByStudyNoteId(deckId) auf - eine voellig andere
    // Spalte. Der Endpunkt lieferte damit praktisch immer eine leere Liste.
    @Test
    void getCardsByDeckQueriesTheDeckColumnAndNotTheStudyNoteColumn() {
        when(flashcardRepository.findByDeckIdAndDeckUserId(4L, 1L))
                .thenReturn(List.of(card(1L, 0, 0, 250)));

        List<Flashcard> cards = service.getCardsByDeck(1L, 4L);

        assertEquals(1, cards.size());
        verify(flashcardRepository).findByDeckIdAndDeckUserId(4L, 1L);
        verify(flashcardRepository, never()).findByStudyNoteId(anyLong());
    }

    @Test
    void getCardOfAnotherUserIsNotFound() {
        when(flashcardRepository.findByIdAndDeckUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getCard(1L, 7L));
    }

    // DER Bug: das Frontend sendet seit jeher GOOD. Die alte Abbildung kannte nur
    // AGAIN/HARD/MEDIUM/EASY und fiel mit "default: return 2" auf q=2, was wegen q < 3 als
    // falsch beantwortet galt - "Gut" setzte die Karte also jedes Mal zurueck.
    @Test
    void goodDoesNotResetTheCard() {
        Flashcard c = card(2L, 3, 10.0, 250);
        when(flashcardRepository.findByIdAndDeckUserId(2L, 1L)).thenReturn(Optional.of(c));
        when(flashcardRepository.save(any(Flashcard.class))).thenAnswer(i -> i.getArgument(0));

        Flashcard reviewed = service.reviewCard(1L, 2L, ReviewRating.GOOD);

        assertEquals(4, reviewed.getRepetitionCount(), "GOOD zaehlt als richtig beantwortet");
        assertTrue(reviewed.getIntervalDays() > 10.0, "das Intervall muss wachsen, nicht fallen");
        assertTrue(reviewed.getNextReviewDate().isAfter(LocalDateTime.now().plusDays(1)),
                "die Karte darf nicht sofort wieder faellig sein");
        assertEquals(0, reviewed.getLapses(), "GOOD ist kein Vergessen");
    }

    // Mit der alten 0..3-Skala senkte selbst EASY den Easiness-Faktor:
    // 0.1 - (5-3)*(0.08 + (5-3)*0.02) = -0.14. Er konnte also nur fallen.
    @Test
    void easyRaisesTheEasinessFactor() {
        Flashcard c = card(3L, 3, 10.0, 240);
        when(flashcardRepository.findByIdAndDeckUserId(3L, 1L)).thenReturn(Optional.of(c));
        when(flashcardRepository.save(any(Flashcard.class))).thenAnswer(i -> i.getArgument(0));

        Flashcard reviewed = service.reviewCard(1L, 3L, ReviewRating.EASY);

        assertTrue(reviewed.getEasinessFactor() > 240,
                "EASY muss den Faktor anheben, er lag vorher bei " + reviewed.getEasinessFactor());
    }

    @Test
    void againResetsTheCardAndCountsALapse() {
        Flashcard c = card(4L, 5, 30.0, 250);
        when(flashcardRepository.findByIdAndDeckUserId(4L, 1L)).thenReturn(Optional.of(c));
        when(flashcardRepository.save(any(Flashcard.class))).thenAnswer(i -> i.getArgument(0));

        Flashcard reviewed = service.reviewCard(1L, 4L, ReviewRating.AGAIN);

        assertEquals(0, reviewed.getRepetitionCount());
        assertEquals(0.0, reviewed.getIntervalDays());
        assertEquals(1, reviewed.getLapses(), "Vergessen wird gezaehlt");
        assertTrue(reviewed.getNextReviewDate().isBefore(LocalDateTime.now().plusHours(1)),
                "eine vergessene Karte kommt in derselben Sitzung wieder");
    }

    // Regression: das Intervall wurde aus repetitionCount rekursiv nachgerechnet statt aus der
    // Spalte gelesen. Eine Karte mit gespeicherten 100 Tagen bekam dadurch das Intervall, das
    // sich aus ihrer Wiederholungszahl ERGEBEN haette - die tatsaechliche Historie war egal.
    @Test
    void theNextIntervalIsBasedOnTheStoredOneNotOnARecomputedChain() {
        Flashcard c = card(5L, 4, 100.0, 200);   // EF 2.0
        when(flashcardRepository.findByIdAndDeckUserId(5L, 1L)).thenReturn(Optional.of(c));
        when(flashcardRepository.save(any(Flashcard.class))).thenAnswer(i -> i.getArgument(0));

        Flashcard reviewed = service.reviewCard(1L, 5L, ReviewRating.GOOD);

        // GOOD laesst EF unveraendert (q=4), also 100 * 2.0 = 200.
        assertEquals(200.0, reviewed.getIntervalDays(),
                "100 gespeicherte Tage * EF 2.0; eine nachgerechnete Kette laege weit darunter");
    }

    @Test
    void theIntervalIsCappedAtOneYear() {
        Flashcard c = card(6L, 9, 300.0, 250);
        when(flashcardRepository.findByIdAndDeckUserId(6L, 1L)).thenReturn(Optional.of(c));
        when(flashcardRepository.save(any(Flashcard.class))).thenAnswer(i -> i.getArgument(0));

        Flashcard reviewed = service.reviewCard(1L, 6L, ReviewRating.EASY);

        assertEquals(365.0, reviewed.getIntervalDays());
    }

    @Test
    void theEasinessFactorNeverFallsBelow1_3() {
        Flashcard c = card(7L, 2, 6.0, 130);
        when(flashcardRepository.findByIdAndDeckUserId(7L, 1L)).thenReturn(Optional.of(c));
        when(flashcardRepository.save(any(Flashcard.class))).thenAnswer(i -> i.getArgument(0));

        Flashcard reviewed = service.reviewCard(1L, 7L, ReviewRating.AGAIN);

        assertEquals(130, reviewed.getEasinessFactor());
    }

    // Ohne Protokoll gibt es kein "heute X Karten gelernt" und keine Retention-Kurve - die Karte
    // selbst kennt nur ihren aktuellen Zustand, nicht den Weg dorthin.
    @Test
    void everyReviewIsLogged() {
        Flashcard c = card(8L, 3, 10.0, 250);
        when(flashcardRepository.findByIdAndDeckUserId(8L, 1L)).thenReturn(Optional.of(c));
        when(flashcardRepository.save(any(Flashcard.class))).thenAnswer(i -> i.getArgument(0));

        service.reviewCard(1L, 8L, ReviewRating.GOOD);

        ArgumentCaptor<FlashcardReview> captor = ArgumentCaptor.forClass(FlashcardReview.class);
        verify(reviewRepository).save(captor.capture());
        FlashcardReview logged = captor.getValue();

        assertEquals(ReviewRating.GOOD, logged.getRating());
        assertEquals(8L, logged.getFlashcard().getId());
        assertEquals(1L, logged.getUser().getId(), "der Besitz haengt am Deck");
        assertEquals(10.0, logged.getIntervalDaysBefore(), "das Intervall VOR der Bewertung");
        assertEquals(25.0, logged.getIntervalDaysAfter(), "und danach");
        assertNotNull(logged.getReviewedAt());
    }

    // Das Protokoll haengt per Fremdschluessel an der Karte: erst die Reviews, dann die Karte.
    @Test
    void deletingACardRemovesItsReviewLogFirst() {
        Flashcard c = card(9L, 1, 1.0, 250);
        when(flashcardRepository.findByIdAndDeckUserId(9L, 1L)).thenReturn(Optional.of(c));

        service.deleteCard(1L, 9L);

        InOrder order = inOrder(reviewRepository, flashcardRepository);
        order.verify(reviewRepository).deleteByFlashcardId(9L);
        order.verify(flashcardRepository).delete(c);
    }

    @Test
    void createCardRejectsAForeignDeck() {
        when(deckRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createCard(1L, card(0L, 0, 0, 250), 99L));
        verify(flashcardRepository, never()).save(any());
    }
}
