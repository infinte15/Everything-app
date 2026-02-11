package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    // Übungen einer Session
    List<Exercise> findByWorkoutSessionId(Long sessionId);

    // Abgeschlossene Übungen
    List<Exercise> findByWorkoutSessionIdAndCompletedTrue(Long sessionId);

    // Offene Übungen
    List<Exercise> findByWorkoutSessionIdAndCompletedFalse(Long sessionId);

    // Übungen nach Name
    @Query("SELECT e FROM Exercise e " +
            "WHERE e.workoutSession.user.id = :userId " +
            "AND LOWER(e.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Exercise> findByUserIdAndNameContaining(
            @Param("userId") Long userId,
            @Param("name") String name
    );

    // Maximales Gewicht
    @Query("SELECT MAX(e.weight) FROM Exercise e " +
            "WHERE e.workoutSession.user.id = :userId " +
            "AND e.name = :exerciseName " +
            "AND e.completed = true")
    Double getMaxWeight(
            @Param("userId") Long userId,
            @Param("exerciseName") String exerciseName
    );

    // Progress-Tracking
    @Query("SELECT e FROM Exercise e " +
            "WHERE e.workoutSession.user.id = :userId " +
            "AND e.name = :exerciseName " +
            "AND e.completed = true " +
            "ORDER BY e.workoutSession.completedAt ASC")
    List<Exercise> getExerciseHistory(
            @Param("userId") Long userId,
            @Param("exerciseName") String exerciseName
    );

    List<Exercise> findByMuscleGroup(String muscleGroup);
    List<Exercise> findByDifficulty(String difficulty);

}