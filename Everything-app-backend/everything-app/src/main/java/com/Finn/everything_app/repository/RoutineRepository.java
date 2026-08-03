package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Routine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoutineRepository extends JpaRepository<Routine, Long> {

    Optional<Routine> findByIdAndUserId(Long id, Long userId);

    List<Routine> findByUserIdOrderByOrderIndexAscIdAsc(Long userId);

    List<Routine> findByUserIdAndIsArchivedFalseOrderByOrderIndexAscIdAsc(Long userId);

    List<Routine> findByUserIdAndWorkoutPlanIdOrderByOrderIndexAscIdAsc(Long userId, Long workoutPlanId);

    /** Rotationsreihenfolge fuer die Platzhalter-Erzeugung des Schedulers. */
    List<Routine> findByWorkoutPlanIdAndIsArchivedFalseOrderByOrderIndexAscIdAsc(Long workoutPlanId);

    /**
     * Loest die Plan-Zuordnung, bevor ein Plan geloescht wird. Routinen sind wiederverwendbar
     * und duerfen deshalb nicht mit dem Programm verschwinden.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Routine r set r.workoutPlan = null where r.workoutPlan.id = :planId")
    void detachFromPlan(@Param("planId") Long planId);

    @Query("select coalesce(max(r.orderIndex), -1) from Routine r where r.user.id = :userId")
    int findMaxOrderIndex(@Param("userId") Long userId);
}
