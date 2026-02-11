package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.FlashcardDeck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FlashcardDeckRepository extends JpaRepository<FlashcardDeck, Long> {

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