package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;

    public List<Exercise> getAllExercises() {
        return exerciseRepository.findAll();
    }


    public Exercise getExerciseById(Long id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Übung nicht gefunden"));
    }

    public List<Exercise> getExercisesByMuscleGroup(String muscleGroup) {
        return exerciseRepository.findByMuscleGroup(muscleGroup);
    }


    @Transactional
    public Exercise createExercise(Long userId, Exercise exercise) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        exercise.setCreatedBy(user); // User-eigene Übung

        return exerciseRepository.save(exercise);
    }

    @Transactional
    public Exercise updateExercise(Long id, Exercise updatedExercise) {
        Exercise exercise = getExerciseById(id);

        if (updatedExercise.getName() != null) {
            exercise.setName(updatedExercise.getName());
        }
        if (updatedExercise.getDescription() != null) {
            exercise.setDescription(updatedExercise.getDescription());
        }
        if (updatedExercise.getInstructions() != null) {
            exercise.setInstructions(updatedExercise.getInstructions());
        }
        if (updatedExercise.getMuscleGroup() != null) {
            exercise.setMuscleGroup(updatedExercise.getMuscleGroup());
        }
        if (updatedExercise.getEquipment() != null) {
            exercise.setEquipment(updatedExercise.getEquipment());
        }
        if (updatedExercise.getDifficulty() != null) {
            exercise.setDifficulty(updatedExercise.getDifficulty());
        }
        if (updatedExercise.getVideoUrl() != null) {
            exercise.setVideoUrl(updatedExercise.getVideoUrl());
        }
        if (updatedExercise.getImageUrl() != null) {
            exercise.setImageUrl(updatedExercise.getImageUrl());
        }

        return exerciseRepository.save(exercise);
    }

    @Transactional
    public void deleteExercise(Long id) {
        Exercise exercise = getExerciseById(id);

        // Nur User-eigene Übungen dürfen gelöscht werden
        if (exercise.getCreatedBy() == null) {
            throw new RuntimeException("System-Übungen können nicht gelöscht werden");
        }

        exerciseRepository.delete(exercise);
    }
}