package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    List<Exercise> findByCreatedById(Long userId);
    List<Exercise> findByMuscleGroup(String muscleGroup);
    List<Exercise> findByDifficulty(String difficulty);

}