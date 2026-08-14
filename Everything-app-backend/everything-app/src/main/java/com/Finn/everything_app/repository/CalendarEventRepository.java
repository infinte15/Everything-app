package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.CalendarEvent;
import com.Finn.everything_app.model.EventType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    // Events in Zeitraum.
    //
    // relatedTask wird mitgeladen, weil CalendarEventMapper daraus Deadline und Priorität ans
    // Frontend gibt (Dringlichkeits-Optik im Kalender). Ohne den EntityGraph wäre relatedTask ein
    // LAZY-Proxy und jeder Task-Block löste beim Mappen eine eigene Abfrage aus — bei einem
    // Monat sind das schnell dreistellig viele.
    @EntityGraph(attributePaths = "relatedTask")
    List<CalendarEvent> findByUserIdAndStartTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);

    // Fixe Events. Bewusst eine Überlappungs-Prüfung statt "startTime BETWEEN": ein Termin, der
    // vor dem Fenster beginnt und hineinragt (Nachtschicht, mehrtägiger Block), muss den Scheduler
    // ebenfalls blockieren — sonst plant er mitten hinein.
    //
    // Die vier related-Kanten werden mitgeladen, weil der Scheduler sie durchgehend
    // dereferenziert (welcher Task hat schon Minuten, welche Habit ihren Tag verbraucht,
    // wo lag ein Block zuletzt). Alle vier sind @ManyToOne, es gibt also keine
    // Ergebnisvervielfachung — ohne die Joins löste stattdessen jede einzelne Kante ihre
    // eigene Abfrage aus.
    @Query("SELECT e FROM CalendarEvent e " +
            "LEFT JOIN FETCH e.relatedTask " +
            "LEFT JOIN FETCH e.relatedHabit " +
            "LEFT JOIN FETCH e.relatedWorkout " +
            "LEFT JOIN FETCH e.relatedProject " +
            "WHERE e.user.id = :userId " +
            "AND e.isFixed = true " +
            "AND e.startTime < :end AND e.endTime > :start " +
            "ORDER BY e.startTime ASC")
    List<CalendarEvent> findFixedEvents(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Alle gepinnten Scheduler-Blöcke ab einem Zeitpunkt — bewusst OHNE obere Grenze.
     *
     * Der Solver muss wissen, was für ein Item bereits festliegt, auch wenn der Nutzer den Block
     * per Drag-and-Drop weit über den Planungshorizont hinaus gezogen hat. Sonst gilt das Item als
     * ungeplant und bekommt im Horizont einen zweiten Block — der verschobene bleibt daneben stehen.
     *
     * Anders als {@link #findFixedEvents} reicht hier die Startzeit als Filter: gebucht wird ein
     * Block über seinen Beginn, nicht über die Zeit, die er blockiert.
     */
    @EntityGraph(attributePaths = {"relatedTask", "relatedHabit", "relatedWorkout", "relatedProject"})
    List<CalendarEvent> findByUserIdAndEventTypeInAndIsFixedTrueAndStartTimeGreaterThanEqual(
            Long userId, List<EventType> eventTypes, LocalDateTime start);

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

    // Scheduler-generierte Events (mehrere Typen) in einem Zeitraum, für sauberes Regenerieren.
    // Mit denselben Fetch-Joins wie oben: aus dieser Liste baut der Scheduler seine
    // Stabilitätsanker, und die hängen genau an den related-Kanten.
    @EntityGraph(attributePaths = {"relatedTask", "relatedHabit", "relatedWorkout", "relatedProject"})
    List<CalendarEvent> findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
            Long userId, List<EventType> eventTypes, Boolean isFixed, LocalDateTime start, LocalDateTime end);

    /**
     * Räumt die generierten Blöcke eines Zeitraums in EINER Anweisung weg.
     *
     * Vorher wurde die Liste geladen, in Java gefiltert und dann Zeile für Zeile gelöscht — bei
     * einem vollen Horizont mehrere hundert Anweisungen pro Lauf, und das bei jeder Neuplanung.
     *
     * Die beiden NULL-Prüfungen sind fachlich tragend und dürfen nicht wegfallen: erledigte Blöcke
     * sind Protokoll und keine Planung, übersprungene sind der einzige Beleg dafür, dass die
     * Ausführung bereits vom Wochenpensum abgezogen wurde. Würde man sie mitlöschen, stünde die
     * Woche wieder unter Pensum und bekäme sofort Ersatz.
     *
     * {@code clearAutomatically}: der Persistenzkontext weiß von der Massenlöschung nichts, hätte
     * die Zeilen aber unter Umständen noch im ersten Level-Cache. Ohne das Leeren arbeitete der
     * restliche Lauf mit Objekten weiter, die es in der Datenbank nicht mehr gibt.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CalendarEvent e WHERE e.user.id = :userId "
            + "AND e.eventType IN :types AND e.isFixed = false "
            + "AND e.startTime BETWEEN :start AND :end "
            + "AND e.completedAt IS NULL AND e.skippedAt IS NULL")
    int deleteGeneratedEvents(@Param("userId") Long userId,
                              @Param("types") List<EventType> types,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    /**
     * Wie oben, aber für die aus dem Stundenplan abgeleiteten Termine.
     *
     * Ohne die Prüfung auf erledigt/übersprungen: eine Vorlesung ist eine Abbildung des
     * Stundenplans, kein Pensum — sie wird bei jedem Abgleich ohnehin neu erzeugt.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CalendarEvent e WHERE e.user.id = :userId "
            + "AND e.eventType = :type AND e.isFixed = false "
            + "AND e.startTime BETWEEN :start AND :end")
    int deleteGeneratedEventsOfType(@Param("userId") Long userId,
                                    @Param("type") EventType type,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

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