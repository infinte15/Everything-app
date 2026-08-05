package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.FlashcardReview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface FlashcardReviewRepository extends JpaRepository<FlashcardReview, Long> {

    // Der Mapper fasst flashcard und dessen deck an — ohne EntityGraph ist das ein N+1 auf
    // jeder Auswertung (und ohne open-in-view eine LazyInitializationException).
    @EntityGraph(attributePaths = {"flashcard", "flashcard.deck"})
    List<FlashcardReview> findByUserIdAndReviewedAtAfterOrderByReviewedAtDesc(
            Long userId, LocalDateTime since);

    long countByUserIdAndReviewedAtAfter(Long userId, LocalDateTime since);

    // --- Aufräumen. Das Protokoll hängt per FK an der Karte; ohne diese drei Löschwege
    // scheitert jedes Karten-, Deck- oder Kurs-Löschen an der Fremdschlüsselbedingung.
    // Als Unterabfrage statt als Pfad-Join, weil Bulk-Deletes keine impliziten Joins dulden. ---

    @Modifying
    @Query("DELETE FROM FlashcardReview r WHERE r.flashcard.id = :flashcardId")
    void deleteByFlashcardId(@Param("flashcardId") Long flashcardId);

    @Modifying
    @Query("DELETE FROM FlashcardReview r WHERE r.flashcard.id IN " +
            "(SELECT f.id FROM Flashcard f WHERE f.deck.id = :deckId)")
    void deleteByDeckId(@Param("deckId") Long deckId);

    @Modifying
    @Query("DELETE FROM FlashcardReview r WHERE r.flashcard.id IN " +
            "(SELECT f.id FROM Flashcard f WHERE f.deck.course.id = :courseId)")
    void deleteByCourseId(@Param("courseId") Long courseId);
}
