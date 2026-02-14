package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    // Karteikarten nach Deck
    List<Flashcard> findByDeckId(Long deckId);

    // Karteikarten nach Notiz
    List<Flashcard> findByStudyNoteId(Long studyNoteId);

    // Karteikarten fällig
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
}