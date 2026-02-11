package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.StudyNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StudyNoteRepository extends JpaRepository<StudyNote, Long> {

    // Alle Notizen
    List<StudyNote> findByUserId(Long userId);

    // Notizen nach Kurs
    List<StudyNote> findByUserIdAndCourseId(Long userId, Long courseId);

    List<StudyNote> findByUserIdAndTitleContainingOrContentContaining(
            Long userId,
            String titleQuery,
            String contentQuery
    );


    // Notizen mit Dateien
    @Query("SELECT n FROM StudyNote n WHERE n.user.id = :userId AND n.filePath IS NOT NULL")
    List<StudyNote> findNotesWithFiles(@Param("userId") Long userId);

    // Neueste Notizen
    List<StudyNote> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Notizen nach Tag
    @Query("SELECT n FROM StudyNote n JOIN n.tags t WHERE n.user.id = :userId AND t = :tag")
    List<StudyNote> findByUserIdAndTag(@Param("userId") Long userId, @Param("tag") String tag);

    // Notizen pro Kurs
    @Query("SELECT COUNT(n) FROM StudyNote n WHERE n.course.id = :courseId")
    Long countByCourseId(@Param("courseId") Long courseId);

    List<StudyNote> findByUserIdAndIsFavoriteTrue(Long userId);

    List<StudyNote> findByUserIdAndLastReviewedAtIsNotNullOrderByLastReviewedAtDesc(Long userId);

    List<StudyNote> findByUserIdAndLastReviewedAtIsNull(Long userId);

    List<StudyNote> findByUserIdAndCreatedAtAfter(Long userId, LocalDateTime date);

    Long countByUserIdAndCourseId(Long userId, Long courseId);

    List<StudyNote> findByUserIdAndCategory(Long userId, String category);

    @Query("SELECT n FROM StudyNote n WHERE n.user.id = :userId AND " +
            "(n.title LIKE %:query% OR n.content LIKE %:query% OR n.tags LIKE %:query%)")
    List<StudyNote> searchNotes(@Param("userId") Long userId, @Param("query") String query);
}

