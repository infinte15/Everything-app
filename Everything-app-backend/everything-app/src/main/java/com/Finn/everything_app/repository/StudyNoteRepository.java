package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.StudyNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StudyNoteRepository extends JpaRepository<StudyNote, Long> {

    // Alle Notizen
    List<StudyNote> findByUserId(Long userId);

    // Notizen nach Kurs
    List<StudyNote> findByUserIdAndCourseId(Long userId, Long courseId);

    // Notizen nach Titel
    List<StudyNote> findByUserIdAndTitleContainingIgnoreCase(Long userId, String title);

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
}