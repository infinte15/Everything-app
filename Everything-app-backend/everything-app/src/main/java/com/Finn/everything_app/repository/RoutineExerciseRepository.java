package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.RoutineExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoutineExerciseRepository extends JpaRepository<RoutineExercise, Long> {

    List<RoutineExercise> findByRoutineIdOrderByOrderIndexAsc(Long routineId);

    /**
     * Loest den Routinen-Bezug bereits trainierter Einheiten, bevor die Routine geloescht wird.
     * Die Historie selbst bleibt unangetastet.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WorkoutSession ws set ws.routine = null where ws.routine.id = :routineId")
    void detachSessionsFromRoutine(@Param("routineId") Long routineId);
}
