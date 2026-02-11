package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Task;
import com.Finn.everything_app.model.TaskStatus;
import com.Finn.everything_app.model.SpaceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
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
}