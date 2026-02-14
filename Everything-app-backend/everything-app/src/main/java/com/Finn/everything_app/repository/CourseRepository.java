package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    // Alle Kurse
    List<Course> findByUserId(Long userId);

    // Kurs nach Code
    Optional<Course> findByUserIdAndCode(Long userId, String code);

    // Kurse nach Name
    List<Course> findByUserIdAndNameContainingIgnoreCase(Long userId, String name);

    // Kurse mit Professor
    List<Course> findByUserIdAndInstructor(Long userId, String instructor);

    // Kurse alphabetisch
    List<Course> findByUserIdOrderByNameAsc(Long userId);
}