package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.CourseSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

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

    // Einziges Eingangstor für Einzelzugriffe: der Besitz hängt am Kurs, CourseSchedule hat
    // bewusst keine eigene user-Spalte.
    //
    // Der EntityGraph zieht course.semesterRef mit: der Mapper liest daraus das Semester-Label,
    // und spring.jpa.open-in-view ist in Tests false — ohne das fliegt beim Mappen eine
    // LazyInitializationException.
    @EntityGraph(attributePaths = {"course", "course.semesterRef"})
    Optional<CourseSchedule> findByIdAndCourseUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"course", "course.semesterRef"})
    List<CourseSchedule> findByCourseIdAndCourseUserIdOrderByDayOfWeekAscStartTimeAsc(
            Long courseId, Long userId);

    // Alle Schedules eines Users. Signatur bleibt — SmartSchedulerService hängt daran.
    // Die Fetch-Joins sind neu: der Scheduler liest seit den Semestergrenzen course.semesterRef,
    // und der Mapper den Kursnamen. LEFT, weil ein Kurs ohne Semester der Normalfall ist.
    @Query("SELECT cs FROM CourseSchedule cs " +
            "JOIN FETCH cs.course c LEFT JOIN FETCH c.semesterRef " +
            "WHERE c.user.id = :userId " +
            "ORDER BY cs.dayOfWeek, cs.startTime")
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