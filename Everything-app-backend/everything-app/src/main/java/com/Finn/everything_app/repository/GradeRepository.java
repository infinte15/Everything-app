package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Grade;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    Optional<Grade> findByIdAndUserId(Long id, Long userId);

    // Alle Noten
    @EntityGraph(attributePaths = "course")
    List<Grade> findByUserId(Long userId);

    // Noten nach Kurs
    List<Grade> findByCourseId(Long courseId);

    // Noten eines Users für einen Kurs
    @EntityGraph(attributePaths = "course")
    List<Grade> findByUserIdAndCourseId(Long userId, Long courseId);

    // Noten chronologisch
    List<Grade> findByUserIdOrderByExamDateDesc(Long userId);

    // Die Aggregat-Queries (gewichteter Schnitt, Gesamtgewichtung, beste/schlechteste Note) sind
    // entfallen. Die Notenmathematik lebt ausschließlich in lib/utils/study_grade_calculator.dart:
    // der Zielschnitt-Slider rechnet bei jedem Tick neu, das verträgt keinen Roundtrip. Zwei
    // Implementierungen derselben Formel wären zudem zwangsläufig irgendwann uneinig.

    // Noten pro Kurs
    Long countByCourseId(Long courseId);
}