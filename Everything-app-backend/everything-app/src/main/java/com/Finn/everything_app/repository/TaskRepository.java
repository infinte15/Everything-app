package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Task;
import com.Finn.everything_app.model.TaskStatus;
import com.Finn.everything_app.model.SpaceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // Alle Tasks
    List<Task> findByUserId(Long userId);

    // Tasks nach Status
    List<Task> findByUserIdAndStatus(Long userId, TaskStatus status);

    // Offene Tasks mit Deadline
    List<Task> findByUserIdAndStatusAndDeadlineBefore(Long userId, TaskStatus status, LocalDateTime deadline);

    // Tasks nach Space
    List<Task> findByUserIdAndSpaceType(Long userId, SpaceType spaceType);

    //  ungeplante Tasks
    List<Task> findByUserIdAndScheduledStartTimeIsNull(Long userId);

    @Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.status = 'TODO' AND NOT EXISTS (SELECT e FROM CalendarEvent e WHERE e.relatedTask = t AND e.isFixed = true)")
    List<Task> findTasksForAutoScheduling(@Param("userId") Long userId);

    // Quelle für den Scheduler. Bewusst OHNE den "kein gepinnter Termin"-Ausschluss von
    // findTasksForAutoScheduling: seit dem Chunking soll das Pinnen eines einzelnen Blocks die
    // übrigen Blöcke beweglich lassen. Die gepinnte Zeit zieht der Scheduler selbst ab.
    // findTasksForAutoScheduling bleibt unverändert — /api/tasks/unscheduled hängt daran.
    @Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.status = 'TODO'")
    List<Task> findSchedulableTasks(@Param("userId") Long userId);

    // Custom JPQL Query
    @Query("SELECT t FROM Task t WHERE t.user.id = :userId " +
            "AND t.status = :status " +
            "AND t.deadline BETWEEN :startDate AND :endDate " +
            "ORDER BY t.priority DESC, t.deadline ASC")
    List<Task> findTasksForScheduling(
            @Param("userId") Long userId,
            @Param("status") TaskStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // Tasks nach Projekt
    List<Task> findByProjectId(Long projectId);

    // Anzahl offener Tasks
    @Query("SELECT COUNT(t) FROM Task t WHERE t.user.id = :userId AND t.status = 'OPEN'")
    Long countOpenTasksByUserId(@Param("userId") Long userId);

    /** Für den einmaligen Aufräumer der alten Lernziel-Brücke (siehe StudyGoalLegacyTaskCleanup). */
    List<Task> findByCategoryStartingWith(String prefix);
}