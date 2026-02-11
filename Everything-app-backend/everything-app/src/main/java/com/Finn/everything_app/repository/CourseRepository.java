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
    Optional<Course> findByUserIdAndCourseCode(Long userId, String courseCode);

    // Kurse nach Name
    List<Course> findByUserIdAndNameContainingIgnoreCase(Long userId, String name);

    // Kurse mit Professor
    List<Course> findByUserIdAndProfessor(Long userId, String professor);

    // Aktive Kurse
    @Query("SELECT DISTINCT c FROM Course c " +
            "JOIN c.schedules s " +
            "WHERE c.user.id = :userId")
    List<Course> findActiveCourses(@Param("userId") Long userId);

    // Kurse alphabetisch
    List<Course> findByUserIdOrderByNameAsc(Long userId);
}