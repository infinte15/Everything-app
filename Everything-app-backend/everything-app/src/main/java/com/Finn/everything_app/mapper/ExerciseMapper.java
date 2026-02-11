package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.ExerciseDTO;
import com.Finn.everything_app.model.Exercise;
import org.springframework.stereotype.Component;

@Component
public class ExerciseMapper {

    public ExerciseDTO toDTO(Exercise exercise) {
        if (exercise == null) return null;

        ExerciseDTO dto = new ExerciseDTO();
        dto.setId(exercise.getId());
        dto.setName(exercise.getName());
        dto.setDescription(exercise.getDescription());
        dto.setInstructions(exercise.getInstructions());
        dto.setMuscleGroup(exercise.getMuscleGroup());
        dto.setEquipment(exercise.getEquipment());
        dto.setDifficulty(exercise.getDifficulty());
        dto.setVideoUrl(exercise.getVideoUrl());
        dto.setImageUrl(exercise.getImageUrl());
        dto.setCreatedAt(exercise.getCreatedAt());
        dto.setUpdatedAt(exercise.getUpdatedAt());

        return dto;
    }

    public Exercise toEntity(ExerciseDTO dto) {
        if (dto == null) return null;

        Exercise exercise = new Exercise();
        if (dto.getId() != null) {
            exercise.setId(dto.getId());
        }
        exercise.setName(dto.getName());
        exercise.setDescription(dto.getDescription());
        exercise.setInstructions(dto.getInstructions());
        exercise.setMuscleGroup(dto.getMuscleGroup());
        exercise.setEquipment(dto.getEquipment());
        exercise.setDifficulty(dto.getDifficulty());
        exercise.setVideoUrl(dto.getVideoUrl());
        exercise.setImageUrl(dto.getImageUrl());

        return exercise;
    }
}