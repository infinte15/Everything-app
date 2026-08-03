package com.Finn.everything_app.mapper;


import com.Finn.everything_app.dto.ExerciseSetDTO;
import com.Finn.everything_app.model.ExerciseSet;
import com.Finn.everything_app.model.SetType;
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
        dto.setSetType(SetType.orDefault(set.getSetType()));
        dto.setRestSeconds(set.getRestSeconds());
        dto.setRpe(set.getRpe());
        dto.setExerciseOrder(set.getExerciseOrder());
        dto.setRoutineExerciseId(set.getRoutineExerciseId());
        dto.setCompletedAt(set.getCompletedAt());

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
        set.setSetType(SetType.orDefault(dto.getSetType()));
        set.setRestSeconds(dto.getRestSeconds());
        set.setRpe(dto.getRpe());
        set.setExerciseOrder(dto.getExerciseOrder());
        set.setRoutineExerciseId(dto.getRoutineExerciseId());

        return set;
    }
}