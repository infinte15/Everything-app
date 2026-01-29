package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.WorkoutPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {

    // Alle Pläne
    List<WorkoutPlan> findByUserId(Long userId);

    // Aktiver Plan
    Optional<WorkoutPlan> findByUserIdAndIsActiveTrue(Long userId);

    // Pläne nach Name
    List<WorkoutPlan> findByUserIdAndNameContainingIgnoreCase(Long userId, String name);

    // Aktuelle Pläne
    @Query("SELECT wp FROM WorkoutPlan wp " +
            "WHERE wp.user.id = :userId " +
            "AND wp.startDate <= :today " +
            "AND (wp.endDate IS NULL OR wp.endDate >= :today)")
    List<WorkoutPlan> findCurrentPlans(
            @Param("userId") Long userId,
            @Param("today") LocalDate today
    );

    // Abgeschlossene Pläne
    @Query("SELECT wp FROM WorkoutPlan wp " +
            "WHERE wp.user.id = :userId " +
            "AND wp.endDate < :today " +
            "ORDER BY wp.endDate DESC")
    List<WorkoutPlan> findCompletedPlans(
            @Param("userId") Long userId,
            @Param("today") LocalDate today
    );

    // Neueste Pläne
    List<WorkoutPlan> findByUserIdOrderByStartDateDesc(Long userId);
}