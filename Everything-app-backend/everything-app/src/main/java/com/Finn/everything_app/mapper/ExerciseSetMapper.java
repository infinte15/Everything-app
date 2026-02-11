package com.Finn.everything_app.mapper;


import com.Finn.everything_app.dto.ExerciseSetDTO;
import com.Finn.everything_app.model.ExerciseSet;
import org.springframework.stereotype.Component;

@Component
public class ExerciseSetMapper {

    public ExerciseSetDTO toDTO(ExerciseSet set) {
        if (set == null) return null;

        ExerciseSetDTO dto = new ExerciseSetDTO();
        dto.setId(set.getId());
        dto.setExerciseId(set.getExercise() != null ? set.getExercise().getId() : null);
        dto.setExerciseName(set.getExercise() != null ? set.getExercise().getName() : null);
        dto.setWorkoutSessionId(set.getWorkoutSession() != null ? set.getWorkoutSession().getId() : null);
        dto.setSetNumber(set.getSetNumber());
        dto.setReps(set.getReps());
        dto.setWeight(set.getWeight());
        dto.setDurationSeconds(set.getDurationSeconds());
        dto.setNotes(set.getNotes());
        dto.setIsCompleted(set.getIsCompleted());

        return dto;
    }

    public ExerciseSet toEntity(ExerciseSetDTO dto) {
        if (dto == null) return null;

        ExerciseSet set = new ExerciseSet();
        set.setId(dto.getId());
        set.setSetNumber(dto.getSetNumber());
        set.setReps(dto.getReps());
        set.setWeight(dto.getWeight());
        set.setDurationSeconds(dto.getDurationSeconds());
        set.setNotes(dto.getNotes());
        set.setIsCompleted(dto.getIsCompleted());

        return set;
    }
}