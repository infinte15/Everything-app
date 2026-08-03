package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.RoutineDetailDTO;
import com.Finn.everything_app.dto.RoutineExerciseDTO;
import com.Finn.everything_app.dto.RoutineSummaryDTO;
import com.Finn.everything_app.model.Exercise;
import com.Finn.everything_app.model.MuscleGroup;
import com.Finn.everything_app.model.Routine;
import com.Finn.everything_app.model.RoutineExercise;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RoutineMapper {

    private static final int PREVIEW_IMAGE_LIMIT = 4;

    public RoutineSummaryDTO toSummary(Routine routine) {
        if (routine == null) return null;
        RoutineSummaryDTO dto = new RoutineSummaryDTO();
        fillSummary(dto, routine);
        return dto;
    }

    public RoutineDetailDTO toDetail(Routine routine) {
        if (routine == null) return null;
        RoutineDetailDTO dto = new RoutineDetailDTO();
        fillSummary(dto, routine);
        dto.setExercises(routine.getExercises().stream().map(this::toExerciseDTO).toList());
        return dto;
    }

    public RoutineExerciseDTO toExerciseDTO(RoutineExercise entity) {
        if (entity == null) return null;
        RoutineExerciseDTO dto = new RoutineExerciseDTO();
        dto.setId(entity.getId());
        dto.setOrderIndex(entity.getOrderIndex());
        dto.setTargetSets(entity.getTargetSets());
        dto.setTargetRepsMin(entity.getTargetRepsMin());
        dto.setTargetRepsMax(entity.getTargetRepsMax());
        dto.setTargetWeight(entity.getTargetWeight());
        dto.setTargetDurationSeconds(entity.getTargetDurationSeconds());
        dto.setRestSeconds(entity.getRestSeconds());
        dto.setNotes(entity.getNotes());
        dto.setSupersetGroup(entity.getSupersetGroup());

        Exercise exercise = entity.getExercise();
        if (exercise != null) {
            dto.setExerciseId(exercise.getId());
            dto.setExerciseName(exercise.getName());
            dto.setImageUrl(exercise.getImageUrl());
            dto.setEquipment(exercise.getEquipment());
            dto.setPrimaryMuscles(exercise.getPrimaryMuscles().stream()
                    .map(MuscleGroup::getSlug).toList());
            dto.setSecondaryMuscles(exercise.getSecondaryMuscles().stream()
                    .map(MuscleGroup::getSlug).toList());
        }
        return dto;
    }

    private void fillSummary(RoutineSummaryDTO dto, Routine routine) {
        dto.setId(routine.getId());
        dto.setName(routine.getName());
        dto.setDescription(routine.getDescription());
        dto.setImageUrl(routine.getImageUrl());
        dto.setColorHex(routine.getColorHex());
        dto.setDayLabel(routine.getDayLabel());
        dto.setEstimatedDurationMinutes(routine.getEstimatedDurationMinutes());
        dto.setOrderIndex(routine.getOrderIndex());
        dto.setIsArchived(routine.getIsArchived());
        dto.setLastPerformedAt(routine.getLastPerformedAt());
        dto.setPerformCount(routine.getPerformCount());

        if (routine.getWorkoutPlan() != null) {
            dto.setWorkoutPlanId(routine.getWorkoutPlan().getId());
            dto.setWorkoutPlanName(routine.getWorkoutPlan().getName());
        }

        List<RoutineExercise> exercises = routine.getExercises();
        dto.setExerciseCount(exercises.size());
        dto.setTotalSets(exercises.stream()
                .mapToInt(re -> re.getTargetSets() != null ? re.getTargetSets() : 0)
                .sum());
        dto.setPrimaryMuscles(rankMuscles(exercises));
        dto.setPreviewImageUrls(previewImages(exercises));
    }

    /** Muskelgruppen nach geplanter Satzanzahl gewichtet - so steht vorne, was die Routine praegt. */
    private List<String> rankMuscles(List<RoutineExercise> exercises) {
        Map<MuscleGroup, Integer> weighted = new LinkedHashMap<>();
        for (RoutineExercise re : exercises) {
            if (re.getExercise() == null) continue;
            int sets = re.getTargetSets() != null ? re.getTargetSets() : 1;
            for (MuscleGroup muscle : re.getExercise().getPrimaryMuscles()) {
                weighted.merge(muscle, sets, Integer::sum);
            }
        }
        return weighted.entrySet().stream()
                .sorted(Map.Entry.<MuscleGroup, Integer>comparingByValue().reversed())
                .map(e -> e.getKey().getSlug())
                .toList();
    }

    private List<String> previewImages(List<RoutineExercise> exercises) {
        List<String> images = new ArrayList<>();
        for (RoutineExercise re : exercises) {
            if (images.size() >= PREVIEW_IMAGE_LIMIT) break;
            Exercise exercise = re.getExercise();
            if (exercise != null && exercise.getImageUrl() != null) {
                images.add(exercise.getImageUrl());
            }
        }
        return images;
    }
}
