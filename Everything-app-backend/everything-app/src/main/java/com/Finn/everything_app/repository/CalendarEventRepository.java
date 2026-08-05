package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.CalendarEvent;
import com.Finn.everything_app.model.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    // Events in Zeitraum
    List<CalendarEvent> findByUserIdAndStartTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);

    // Fixe Events. Bewusst eine Überlappungs-Prüfung statt "startTime BETWEEN": ein Termin, der
    // vor dem Fenster beginnt und hineinragt (Nachtschicht, mehrtägiger Block), muss den Scheduler
    // ebenfalls blockieren — sonst plant er mitten hinein.
    @Query("SELECT e FROM CalendarEvent e WHERE e.user.id = :userId " +
            "AND e.isFixed = true " +
            "AND e.startTime < :end AND e.endTime > :start " +
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

    // Events nach Typ und isFixed Status (für Auto-Scheduling)
    List<CalendarEvent> findByUserIdAndEventTypeAndIsFixed(Long userId, EventType eventType, Boolean isFixed);

    // Scheduler-generierte Events (mehrere Typen) in einem Zeitraum, für sauberes Regenerieren
    List<CalendarEvent> findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
            Long userId, List<EventType> eventTypes, Boolean isFixed, LocalDateTime start, LocalDateTime end);

    // Events, die von diesen Quell-Entitäten übrig bleiben würden, wenn sie gelöscht werden —
    // ohne dieses Aufräumen verletzt das Löschen eine Foreign-Key-Constraint (500 statt 204).
    List<CalendarEvent> findByRelatedTaskId(Long taskId);
    List<CalendarEvent> findByRelatedHabitId(Long habitId);
    List<CalendarEvent> findByRelatedWorkoutId(Long workoutSessionId);
    List<CalendarEvent> findByRelatedProjectId(Long projectId);

    /** Kommende Projektbloecke fuer den Detail-Screen. */
    List<CalendarEvent> findByUserIdAndRelatedProjectIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long userId, Long projectId, LocalDateTime start, LocalDateTime end);
}