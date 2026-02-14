package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.WorkoutSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    // Alle Sessions
    List<WorkoutSession> findByUserId(Long userId);

    // Sessions nach Plan
    List<WorkoutSession> findByWorkoutPlanId(Long planId);

    // Sessions in Zeitraum
    List<WorkoutSession> findByUserIdAndStartTimeBetween(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );

    // Abgeschlossene Sessions
    @Query("SELECT ws FROM WorkoutSession ws " +
            "WHERE ws.user.id = :userId " +
            "AND ws.isCompleted = true " +
            "ORDER BY ws.endTime DESC")
    List<WorkoutSession> findCompletedSessions(@Param("userId") Long userId);

    // Offene Sessions
    @Query("SELECT ws FROM WorkoutSession ws " +
            "WHERE ws.user.id = :userId " +
            "AND ws.isCompleted = false " +
            "ORDER BY ws.startTime ASC")
    List<WorkoutSession> findOpenSessions(@Param("userId") Long userId);

    // Anzahl abgeschlossener Sessions
    @Query("SELECT COUNT(ws) FROM WorkoutSession ws " +
            "WHERE ws.user.id = :userId " +
            "AND ws.isCompleted = true")
    Long countCompletedSessions(@Param("userId") Long userId);

    // Gesamte Trainingszeit
    @Query("SELECT SUM(ws.durationMinutes) FROM WorkoutSession ws " +
            "WHERE ws.user.id = :userId " +
            "AND ws.isCompleted = true")
    Long getTotalTrainingMinutes(@Param("userId") Long userId);

    // Sessions nach Plan sortiert
    List<WorkoutSession> findByWorkoutPlanIdOrderByStartTimeAsc(Long planId);
}