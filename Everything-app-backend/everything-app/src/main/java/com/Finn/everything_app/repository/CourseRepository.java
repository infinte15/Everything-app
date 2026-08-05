package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Course;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByIdAndUserId(Long id, Long userId);

    // Alle Kurse. Mit semesterRef im Graph, weil der CourseMapper das Label mitgibt — sonst
    // eine Lazy-Abfrage je Kurs (und in Tests ohne open-in-view eine LazyInitializationException).
    @EntityGraph(attributePaths = "semesterRef")
    List<Course> findByUserId(Long userId);

    // Module eines Semesters — für Zähler, Umbenennung und das Auflösen beim Löschen.
    @EntityGraph(attributePaths = "semesterRef")
    List<Course> findByUserIdAndSemesterRefId(Long userId, Long semesterId);

    // Kurs nach Code
    Optional<Course> findByUserIdAndCode(Long userId, String code);

    // Kurse nach Name
    List<Course> findByUserIdAndNameContainingIgnoreCase(Long userId, String name);

    // Kurse mit Professor
    List<Course> findByUserIdAndInstructor(Long userId, String instructor);

    // Kurse alphabetisch
    List<Course> findByUserIdOrderByNameAsc(Long userId);
}