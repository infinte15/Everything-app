package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.ExerciseSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExerciseSetRepository extends JpaRepository<ExerciseSet, Long> {

    List<ExerciseSet> findByWorkoutSessionIdOrderBySetNumberAsc(Long sessionId);

    List<ExerciseSet> findByExerciseId(Long exerciseId);

    List<ExerciseSet> findByWorkoutSessionIdAndIsCompletedTrue(Long sessionId);

    List<ExerciseSet> findByWorkoutSessionIdAndIsCompletedFalse(Long sessionId);

    List<ExerciseSet> findByExerciseIdAndWorkoutSessionId(Long exerciseId, Long sessionId);

    Long countByWorkoutSessionId(Long sessionId);
}