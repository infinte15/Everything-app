package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GradeRepository extends JpaRepository<Grade, Long> {

    // Alle Noten
    List<Grade> findByUserId(Long userId);

    // Noten nach Kurs
    List<Grade> findByCourseId(Long courseId);

    // Noten eines Users für einen Kurs
    List<Grade> findByUserIdAndCourseId(Long userId, Long courseId);

    // Noten chronologisch
    List<Grade> findByUserIdOrderByDateDesc(Long userId);

    // Durchschnitt berechnen (gewichtet)
    @Query("SELECT SUM(g.score * g.weight) / SUM(g.weight) " +
            "FROM Grade g " +
            "WHERE g.course.id = :courseId")
    Double calculateWeightedAverage(@Param("courseId") Long courseId);

    // Gesamtgewichtung prüfen
    @Query("SELECT SUM(g.weight) FROM Grade g WHERE g.course.id = :courseId")
    Double getTotalWeight(@Param("courseId") Long courseId);

    // Beste Note
    @Query("SELECT MIN(g.score) FROM Grade g WHERE g.course.id = :courseId")
    Double getBestGrade(@Param("courseId") Long courseId);

    // Schlechteste Note
    @Query("SELECT MAX(g.score) FROM Grade g WHERE g.course.id = :courseId")
    Double getWorstGrade(@Param("courseId") Long courseId);

    // Noten pro Kurs
    Long countByCourseId(Long courseId);
}