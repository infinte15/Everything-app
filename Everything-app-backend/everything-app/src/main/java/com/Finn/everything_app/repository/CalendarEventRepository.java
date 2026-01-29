package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.CalendarEvent;
import com.Finn.everything_app.model.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    // Events in Zeitraum
    List<CalendarEvent> findByUserIdAndStartTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);

    // Fixe Events
    @Query("SELECT e FROM CalendarEvent e WHERE e.user.id = :userId " +
            "AND e.isFixed = true " +
            "AND e.startTime BETWEEN :start AND :end " +
            "ORDER BY e.startTime ASC")
    List<CalendarEvent> findFixedEvents(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // Prüfen ob Zeitraum frei ist
    @Query("SELECT COUNT(e) FROM CalendarEvent e WHERE e.user.id = :userId " +
            "AND ((e.startTime <= :start AND e.endTime > :start) " +
            "OR (e.startTime < :end AND e.endTime >= :end) " +
            "OR (e.startTime >= :start AND e.endTime <= :end))")
    Long countOverlappingEvents(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // Events nach Typ
    List<CalendarEvent> findByUserIdAndEventType(Long userId, EventType eventType);
}