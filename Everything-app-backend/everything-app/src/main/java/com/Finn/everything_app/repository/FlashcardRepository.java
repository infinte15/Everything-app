package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Flashcard;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    // Flashcard hat bewusst keine eigene user-Spalte — der Besitz hängt am Deck. Deshalb geht
    // die Eigentumsprüfung hier über deck.user statt über ein user_id-Feld.
    Optional<Flashcard> findByIdAndDeckUserId(Long id, Long userId);

    // Karteikarten nach Deck
    List<Flashcard> findByDeckId(Long deckId);

    @EntityGraph(attributePaths = "deck")
    List<Flashcard> findByDeckIdAndDeckUserId(Long deckId, Long userId);

    // Alle Karten des Nutzers in EINER Abfrage. Der Provider holte vorher pro Deck einzeln
    // (1 + N Requests); das hier ist die Gegenseite dazu.
    @Query("SELECT f FROM Flashcard f JOIN FETCH f.deck d WHERE d.user.id = :userId")
    List<Flashcard> findAllByUserId(@Param("userId") Long userId);

    // Karteikarten nach Notiz
    List<Flashcard> findByStudyNoteId(Long studyNoteId);

    // Karteikarten fällig
    @EntityGraph(attributePaths = "deck")
    @Query("SELECT f FROM Flashcard f " +
            "WHERE f.deck.user.id = :userId " +
            "AND f.nextReviewDate <= :today " +
            "ORDER BY f.nextReviewDate ASC")
    List<Flashcard> findDueCards(
            @Param("userId") Long userId,
            @Param("today") LocalDateTime today
    );

    // Anzahl fälliger Karten
    @Query("SELECT COUNT(f) FROM Flashcard f " +
            "WHERE f.deck.user.id = :userId " +
            "AND f.nextReviewDate <= :today")
    Long countDueCards(
            @Param("userId") Long userId,
            @Param("today") LocalDateTime today
    );

    // --- Zähler für FlashcardDeck. Vorher wurden cardsToReview und masteredCards nie gesetzt
    // und standen dauerhaft auf 0. ---

    long countByDeckId(Long deckId);

    @Query("SELECT COUNT(f) FROM Flashcard f WHERE f.deck.id = :deckId AND f.nextReviewDate <= :now")
    long countDueByDeckId(@Param("deckId") Long deckId, @Param("now") LocalDateTime now);

    // 21 Tage ist Ankis Grenze zwischen "young" und "mature". Bestandskarten haben nach
    // ddl-auto=update NULL in interval_days; NULL >= 21 ist unbekannt und zählt damit nicht mit,
    // was genau richtig ist — ungelernt ist nicht gemeistert.
    @Query("SELECT COUNT(f) FROM Flashcard f WHERE f.deck.id = :deckId AND f.intervalDays >= 21")
    long countMasteredByDeckId(@Param("deckId") Long deckId);

    // --- Kennzahlen für GET /decks/{id}/stats. Die Einteilung ist wortgleich zu
    // AnkiScheduler.isNew / isLearning / isDue in lib/utils/anki_scheduler.dart.
    //
    // COALESCE ist hier Pflicht und kein Zierrat: die SRS-Spalten kamen erst mit diesem Umbau
    // dazu, Bestandszeilen haben NULL darin, und in SQL ist NULL = 0 weder wahr noch falsch —
    // ohne COALESCE fiele jede Altkarte aus allen drei Töpfen und die Summen gingen nicht auf. ---

    @Query("SELECT COUNT(f) FROM Flashcard f WHERE f.deck.id = :deckId " +
            "AND COALESCE(f.repetitionCount, 0) = 0 AND COALESCE(f.learningStep, 0) = 0")
    long countNewByDeckId(@Param("deckId") Long deckId);

    @Query("SELECT COUNT(f) FROM Flashcard f WHERE f.deck.id = :deckId " +
            "AND NOT (COALESCE(f.repetitionCount, 0) = 0 AND COALESCE(f.learningStep, 0) = 0) " +
            "AND (COALESCE(f.repetitionCount, 0) = 0 " +
            "     OR (COALESCE(f.learningStep, 0) < 2 AND COALESCE(f.intervalDays, 0) < 1))")
    long countLearningByDeckId(@Param("deckId") Long deckId);

    // Fällig, aber ohne die nie bewerteten Karten: die zählen als "neu", nicht als "überfällig".
    @Query("SELECT COUNT(f) FROM Flashcard f WHERE f.deck.id = :deckId " +
            "AND f.nextReviewDate <= :now " +
            "AND NOT (COALESCE(f.repetitionCount, 0) = 0 AND COALESCE(f.learningStep, 0) = 0)")
    long countDueExcludingNewByDeckId(@Param("deckId") Long deckId, @Param("now") LocalDateTime now);
}