package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.ExerciseSetDTO;
import com.Finn.everything_app.dto.SessionExerciseDTO;
import com.Finn.everything_app.dto.WorkoutSessionDetailDTO;
import com.Finn.everything_app.model.Exercise;
import com.Finn.everything_app.model.ExerciseSet;
import com.Finn.everything_app.model.MuscleGroup;
import com.Finn.everything_app.model.SetType;
import com.Finn.everything_app.model.WorkoutSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkoutLogMapper {

    private final WorkoutSessionMapper sessionMapper;

    public ExerciseSetDTO toSetDTO(ExerciseSet set) {
        if (set == null) return null;

        ExerciseSetDTO dto = new ExerciseSetDTO();
        dto.setId(set.getId());
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
        dto.setParentSetId(set.getParentSetId());
        dto.setCompletedAt(set.getCompletedAt());

        if (set.getExercise() != null) {
            dto.setExerciseId(set.getExercise().getId());
            dto.setExerciseName(set.getExercise().getName());
        }
        if (set.getWorkoutSession() != null) {
            dto.setWorkoutSessionId(set.getWorkoutSession().getId());
            dto.setPerformedAt(set.getWorkoutSession().getStartTime());
        }
        return dto;
    }

    /** Gruppiert die Saetze einer Einheit zu Uebungsbloecken in Trainingsreihenfolge. */
    public WorkoutSessionDetailDTO toDetail(WorkoutSession session) {
        if (session == null) return null;

        WorkoutSessionDetailDTO dto = new WorkoutSessionDetailDTO();
        List<ExerciseSet> sets = session.getExerciseSets() != null
                ? session.getExerciseSets()
                : List.of();

        sessionMapper.fill(dto, session, null);
        // Arbeitssaetze, nicht alle Zeilen: dieselbe Regel wie beim Volumen, sonst zaehlt die
        // automatische Aufwaermrampe drei Saetze mit, die niemand als Training empfindet.
        dto.setTotalSets((int) sets.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsCompleted()))
                .filter(s -> SetType.countsTowardVolume(s.getSetType()))
                .count());
        dto.setTotalVolumeKg(sets.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsCompleted()))
                .mapToDouble(this::volumeOf)
                .sum());
        dto.setExercises(groupByExercise(sets));
        return dto;
    }

    public List<SessionExerciseDTO> groupByExercise(List<ExerciseSet> sets) {
        Map<Long, SessionExerciseDTO> blocks = new LinkedHashMap<>();

        List<ExerciseSet> ordered = new ArrayList<>(sets);
        ordered.sort(Comparator
                .comparing((ExerciseSet s) -> s.getExerciseOrder() == null ? 0 : s.getExerciseOrder())
                .thenComparing(s -> s.getSetNumber() == null ? 0 : s.getSetNumber()));

        for (ExerciseSet set : ordered) {
            Exercise exercise = set.getExercise();
            if (exercise == null) continue;

            SessionExerciseDTO block = blocks.computeIfAbsent(exercise.getId(), id -> {
                SessionExerciseDTO created = new SessionExerciseDTO();
                created.setExerciseId(exercise.getId());
                created.setName(exercise.getName());
                created.setImageUrl(exercise.getImageUrl());
                created.setImageUrlEnd(exercise.getImageUrlEnd());
                created.setAnimationUrl(exercise.getAnimationUrl());
                created.setEquipment(exercise.getEquipment());
                created.setPrimaryMuscles(exercise.getPrimaryMuscles().stream()
                        .map(MuscleGroup::getSlug).toList());
                created.setSecondaryMuscles(exercise.getSecondaryMuscles().stream()
                        .map(MuscleGroup::getSlug).toList());
                created.setOrderIndex(set.getExerciseOrder());
                created.setRestSeconds(set.getRestSeconds() != null
                        ? set.getRestSeconds()
                        : exercise.getDefaultRestSeconds());
                return created;
            });

            block.getSets().add(toSetDTO(set));
            if (Boolean.TRUE.equals(set.getIsCompleted())) {
                block.setTotalVolumeKg(block.getTotalVolumeKg() + volumeOf(set));
                if (set.getWeight() != null
                        && (block.getBestSetWeight() == null || set.getWeight() > block.getBestSetWeight())) {
                    block.setBestSetWeight(set.getWeight());
                }
            }
        }
        return new ArrayList<>(blocks.values());
    }

    /**
     * Volumen eines Satzes. Aufwaermsaetze und Rest-Pause-Cluster zaehlen nicht mit - warum,
     * steht bei {@link SetType#countsTowardVolume(SetType)}.
     */
    public double volumeOf(ExerciseSet set) {
        if (!SetType.countsTowardVolume(set.getSetType())) return 0d;
        if (set.getWeight() == null || set.getReps() == null) return 0d;
        return set.getWeight() * set.getReps();
    }
}
