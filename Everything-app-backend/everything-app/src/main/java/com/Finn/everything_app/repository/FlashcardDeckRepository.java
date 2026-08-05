package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.FlashcardDeck;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlashcardDeckRepository extends JpaRepository<FlashcardDeck, Long> {

    Optional<FlashcardDeck> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "course")
    List<FlashcardDeck> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<FlashcardDeck> findByUserIdAndCourseId(Long userId, Long courseId);

    List<FlashcardDeck> findByUserIdAndCourseIsNull(Long userId);

    List<FlashcardDeck> findByUserIdAndLastStudiedAtIsNotNullOrderByLastStudiedAtDesc(Long userId);

    List<FlashcardDeck> findByUserIdAndLastStudiedAtIsNull(Long userId);

    List<FlashcardDeck> findByUserIdAndCardsToReviewGreaterThan(Long userId, Integer minCards);

    List<FlashcardDeck> findByUserIdAndNameContaining(Long userId, String query);

    List<FlashcardDeck> findByUserIdOrderByTotalCardsDesc(Long userId);

    Long countByUserIdAndCourseId(Long userId, Long courseId);
}