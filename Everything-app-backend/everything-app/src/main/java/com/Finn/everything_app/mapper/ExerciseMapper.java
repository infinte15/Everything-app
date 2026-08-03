package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.ExerciseDTO;
import com.Finn.everything_app.model.Exercise;
import com.Finn.everything_app.model.MuscleGroup;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        dto.setPrimaryMuscles(toSlugs(exercise.getPrimaryMuscles()));
        dto.setSecondaryMuscles(toSlugs(exercise.getSecondaryMuscles()));
        dto.setEquipment(exercise.getEquipment());
        dto.setDifficulty(exercise.getDifficulty());
        dto.setCategory(exercise.getCategory());
        dto.setForce(exercise.getForce());
        dto.setMechanic(exercise.getMechanic());
        dto.setVideoUrl(exercise.getVideoUrl());
        dto.setImageUrl(exercise.getImageUrl());
        dto.setImageUrlEnd(exercise.getImageUrlEnd());
        dto.setExternalId(exercise.getExternalId());
        dto.setDefaultRestSeconds(exercise.getDefaultRestSeconds());
        dto.setIsSystem(exercise.getIsSystem());
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
        exercise.setPrimaryMuscles(toMuscles(dto.getPrimaryMuscles()));
        exercise.setSecondaryMuscles(toMuscles(dto.getSecondaryMuscles()));
        exercise.setEquipment(dto.getEquipment());
        exercise.setDifficulty(dto.getDifficulty());
        exercise.setCategory(dto.getCategory());
        exercise.setForce(dto.getForce());
        exercise.setMechanic(dto.getMechanic());
        exercise.setVideoUrl(dto.getVideoUrl());
        exercise.setImageUrl(dto.getImageUrl());
        exercise.setImageUrlEnd(dto.getImageUrlEnd());
        exercise.setDefaultRestSeconds(dto.getDefaultRestSeconds());

        // externalId/source/isSystem sind bewusst nicht uebernehmbar - die vergibt nur der Seeder.
        return exercise;
    }

    private List<String> toSlugs(Set<MuscleGroup> muscles) {
        if (muscles == null) return List.of();
        return muscles.stream().map(MuscleGroup::getSlug).collect(Collectors.toList());
    }

    private Set<MuscleGroup> toMuscles(List<String> slugs) {
        if (slugs == null) return new LinkedHashSet<>();
        return slugs.stream()
                .map(MuscleGroup::fromSlug)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
