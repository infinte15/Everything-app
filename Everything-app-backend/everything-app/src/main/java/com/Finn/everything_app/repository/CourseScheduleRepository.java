package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.CourseSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface CourseScheduleRepository extends JpaRepository<CourseSchedule, Long> {

    // Alle Schedules
    List<CourseSchedule> findByCourseId(Long courseId);

    // Schedules für Wochentag
    @Query("SELECT cs FROM CourseSchedule cs " +
            "WHERE cs.course.user.id = :userId " +
            "AND cs.dayOfWeek = :dayOfWeek " +
            "ORDER BY cs.startTime")
    List<CourseSchedule> findByUserIdAndDayOfWeek(
            @Param("userId") Long userId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek
    );

    // Alle Schedules eines Users
    @Query("SELECT cs FROM CourseSchedule cs WHERE cs.course.user.id = :userId")
    List<CourseSchedule> findByUserId(@Param("userId") Long userId);

    // Prüfe Überschneidungen
    @Query("SELECT COUNT(cs) FROM CourseSchedule cs " +
            "WHERE cs.course.user.id = :userId " +
            "AND cs.dayOfWeek = :dayOfWeek " +
            "AND ((cs.startTime <= :startTime AND cs.endTime > :startTime) " +
            "OR (cs.startTime < :endTime AND cs.endTime >= :endTime))")
    Long countOverlappingSchedules(
            @Param("userId") Long userId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );
}