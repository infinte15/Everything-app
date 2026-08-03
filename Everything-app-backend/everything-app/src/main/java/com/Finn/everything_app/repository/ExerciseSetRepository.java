package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.ExerciseSet;
import com.Finn.everything_app.repository.projection.SessionAggregateRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ExerciseSetRepository extends JpaRepository<ExerciseSet, Long> {

    List<ExerciseSet> findByWorkoutSessionIdOrderBySetNumberAsc(Long sessionId);

    List<ExerciseSet> findByExerciseId(Long exerciseId);

    List<ExerciseSet> findByWorkoutSessionIdAndIsCompletedTrue(Long sessionId);

    List<ExerciseSet> findByWorkoutSessionIdAndIsCompletedFalse(Long sessionId);

    List<ExerciseSet> findByExerciseIdAndWorkoutSessionId(Long exerciseId, Long sessionId);

    Long countByWorkoutSessionId(Long sessionId);

    /** Nur der Besitzer der Einheit darf die Saetze sehen. */
    @Query("""
            select s from ExerciseSet s
            where s.workoutSession.id = :sessionId
              and s.workoutSession.user.id = :userId
            order by s.exerciseOrder asc nulls first, s.setNumber asc
            """)
    List<ExerciseSet> findBySessionForUser(@Param("sessionId") Long sessionId,
                                           @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ExerciseSet s where s.workoutSession.id = :sessionId")
    void deleteByWorkoutSessionId(@Param("sessionId") Long sessionId);

    /**
     * Satzanzahl und Volumen je Einheit in einer Abfrage - damit die Listen-Endpunkte mit
     * insgesamt zwei Queries auskommen statt mit einer pro Einheit.
     */
    @Query("""
            select s.workoutSession.id as sessionId,
                   count(s) as setCount,
                   coalesce(sum(s.weight * s.reps), 0) as volume
            from ExerciseSet s
            where s.workoutSession.id in :ids and s.isCompleted = true
            group by s.workoutSession.id
            """)
    List<SessionAggregateRow> aggregateBySessionIds(@Param("ids") Collection<Long> ids);

    /** IDs der Einheiten, in denen diese Uebung zuletzt trainiert wurde (neueste zuerst). */
    @Query("""
            select ws.id from WorkoutSession ws
            where ws.user.id = :userId
              and ws.isCompleted = true
              and exists (
                    select 1 from ExerciseSet s
                    where s.workoutSession = ws and s.exercise.id = :exerciseId)
            order by ws.startTime desc
            """)
    List<Long> findRecentSessionIdsForExercise(@Param("exerciseId") Long exerciseId,
                                               @Param("userId") Long userId,
                                               Pageable pageable);

    @Query("""
            select s from ExerciseSet s
            join fetch s.workoutSession ws
            where s.exercise.id = :exerciseId and ws.id in :sessionIds
            order by ws.startTime desc, s.setNumber asc
            """)
    List<ExerciseSet> findSetsForExerciseInSessions(@Param("exerciseId") Long exerciseId,
                                                    @Param("sessionIds") Collection<Long> sessionIds);

    /**
     * Letzte Leistung je Uebung fuer eine Menge von Uebungen - eine Abfrage fuer den
     * kompletten Trainingsstart.
     */
    @Query("""
            select s from ExerciseSet s
            join fetch s.workoutSession ws
            where s.exercise.id in :exerciseIds
              and ws.user.id = :userId
              and ws.isCompleted = true
              and s.isCompleted = true
            order by ws.startTime desc, s.setNumber asc
            """)
    List<ExerciseSet> findCompletedSetsForExercises(@Param("exerciseIds") Collection<Long> exerciseIds,
                                                    @Param("userId") Long userId);
}
