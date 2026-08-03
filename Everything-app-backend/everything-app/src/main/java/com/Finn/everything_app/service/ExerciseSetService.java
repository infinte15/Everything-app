package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import com.Finn.everything_app.repository.projection.SessionAggregateRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseSetService {

    private final ExerciseSetRepository exerciseSetRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutSessionRepository workoutSessionRepository;

    @Transactional
    public ExerciseSet createSet(ExerciseSet set, Long exerciseId, Long sessionId) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new RuntimeException("Übung nicht gefunden"));

        WorkoutSession session = workoutSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Workout-Session nicht gefunden"));

        set.setExercise(exercise);
        set.setWorkoutSession(session);
        set.setIsCompleted(set.getIsCompleted() != null ? set.getIsCompleted() : false);

        return exerciseSetRepository.save(set);
    }

    public List<ExerciseSet> getSetsBySession(Long sessionId) {
        return exerciseSetRepository.findByWorkoutSessionIdOrderBySetNumberAsc(sessionId);
    }

    /** Wie {@link #getSetsBySession(Long)}, aber nur fuer den Besitzer der Einheit. */
    public List<ExerciseSet> getSetsBySessionForUser(Long sessionId, Long userId) {
        return exerciseSetRepository.findBySessionForUser(sessionId, userId);
    }

    /** Satzanzahl und Volumen fuer mehrere Einheiten in einer Abfrage, indiziert nach Session-ID. */
    public Map<Long, SessionAggregateRow> aggregateBySessionIds(Collection<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        return exerciseSetRepository.aggregateBySessionIds(sessionIds).stream()
                .collect(Collectors.toMap(SessionAggregateRow::getSessionId, Function.identity()));
    }

    public ExerciseSet getSetById(Long id) {
        return exerciseSetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Satz nicht gefunden"));
    }

    @Transactional
    public ExerciseSet updateSet(Long id, ExerciseSet updatedSet) {
        ExerciseSet set = getSetById(id);

        if (updatedSet.getSetNumber() != null) {
            set.setSetNumber(updatedSet.getSetNumber());
        }
        if (updatedSet.getReps() != null) {
            set.setReps(updatedSet.getReps());
        }
        if (updatedSet.getWeight() != null) {
            set.setWeight(updatedSet.getWeight());
        }
        if (updatedSet.getDurationSeconds() != null) {
            set.setDurationSeconds(updatedSet.getDurationSeconds());
        }
        if (updatedSet.getNotes() != null) {
            set.setNotes(updatedSet.getNotes());
        }
        if (updatedSet.getIsCompleted() != null) {
            set.setIsCompleted(updatedSet.getIsCompleted());
        }

        return exerciseSetRepository.save(set);
    }

    @Transactional
    public void deleteSet(Long id) {
        ExerciseSet set = getSetById(id);
        exerciseSetRepository.delete(set);
    }
}