package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.WorkoutSession;
import com.Finn.everything_app.repository.projection.ExerciseOneRmRow;
import com.Finn.everything_app.repository.projection.MuscleVolumeRow;
import com.Finn.everything_app.repository.projection.RecoverySetRow;
import com.Finn.everything_app.repository.projection.WeekBucketRow;
import com.Finn.everything_app.repository.projection.WeekVolumeRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reine Auswertungsabfragen des Gym-Bereichs - bewusst von den CRUD-Repositories getrennt.
 *
 * <p>Nativ, weil JPQL weder {@code date_trunc} noch {@code union all} kennt. Postgres rechnet
 * {@code date_trunc('week', ...)} ab Montag, das passt genau zu {@code targetWeekStart}.
 */
public interface WorkoutStatsRepository extends Repository<WorkoutSession, Long> {

    /**
     * Einheiten und Minuten je Woche.
     *
     * <p>Bewusst <em>ohne</em> Join auf exercise_sets: jeder Satz wuerde die Zeile der Einheit
     * vervielfachen und die Minutensumme aufblaehen.
     */
    @Query(value = """
            SELECT CAST(date_trunc('week', ws.start_time) AS date) AS weekStart,
                   COUNT(*)                                        AS workouts,
                   COALESCE(SUM(ws.duration_minutes), 0)           AS minutes
            FROM workout_sessions ws
            WHERE ws.user_id = :userId
              AND ws.is_completed = TRUE
              AND ws.start_time >= :since
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<WeekBucketRow> weeklySessionBuckets(@Param("userId") Long userId,
                                             @Param("since") LocalDateTime since);

    /**
     * Volumen und Satzanzahl je Woche - hier ist der Join auf die Saetze richtig.
     *
     * <p>{@code WARMUP} und {@code RESTPAUSE} bleiben draussen. Die Begruendung steht bei
     * {@code SetType.countsTowardVolume(SetType)}; die Bedingung wiederholt sich in jeder
     * Abfrage dieser Datei, weil Postgres das Java-Enum nicht kennt.
     */
    @Query(value = """
            SELECT CAST(date_trunc('week', ws.start_time) AS date) AS weekStart,
                   COALESCE(SUM(s.weight * s.reps), 0)             AS volume,
                   COUNT(s.id)                                     AS setCount
            FROM exercise_sets s
            JOIN workout_sessions ws ON ws.id = s.workout_session_id
            WHERE ws.user_id = :userId
              AND ws.is_completed = TRUE
              AND s.is_completed = TRUE
              AND (s.set_type IS NULL OR s.set_type NOT IN ('WARMUP', 'RESTPAUSE'))
              AND ws.start_time >= :since
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<WeekVolumeRow> weeklyVolumeBuckets(@Param("userId") Long userId,
                                            @Param("since") LocalDateTime since);

    /**
     * Belastung je Muskelgruppe. Primaermuskeln zaehlen voll, Sekundaermuskeln zur Haelfte -
     * so faerbt sich bei einem Bankdruecken auch der Trizeps mit ein, aber schwaecher.
     */
    @Query(value = """
            SELECT muscle                     AS muscle,
                   SUM(volume)                AS volume,
                   SUM(w)                     AS weightedSets,
                   COUNT(DISTINCT session_id) AS sessionCount
            FROM (
                SELECT pm.muscle AS muscle,
                       COALESCE(s.weight, 0) * COALESCE(s.reps, 0) * 1.0 AS volume,
                       1.0 AS w,
                       ws.id AS session_id
                FROM exercise_sets s
                JOIN workout_sessions ws ON ws.id = s.workout_session_id
                JOIN exercise_primary_muscles pm ON pm.exercise_id = s.exercise_id
                WHERE ws.user_id = :userId
                  AND ws.is_completed = TRUE
                  AND s.is_completed = TRUE
                  AND (s.set_type IS NULL OR s.set_type NOT IN ('WARMUP', 'RESTPAUSE'))
                  AND ws.start_time >= :from
                  AND ws.start_time < :to
                UNION ALL
                SELECT sm.muscle,
                       COALESCE(s.weight, 0) * COALESCE(s.reps, 0) * 0.5,
                       0.5,
                       ws.id
                FROM exercise_sets s
                JOIN workout_sessions ws ON ws.id = s.workout_session_id
                JOIN exercise_secondary_muscles sm ON sm.exercise_id = s.exercise_id
                WHERE ws.user_id = :userId
                  AND ws.is_completed = TRUE
                  AND s.is_completed = TRUE
                  AND (s.set_type IS NULL OR s.set_type NOT IN ('WARMUP', 'RESTPAUSE'))
                  AND ws.start_time >= :from
                  AND ws.start_time < :to
            ) t
            GROUP BY muscle
            """, nativeQuery = true)
    List<MuscleVolumeRow> muscleVolume(@Param("userId") Long userId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    /**
     * Arbeitssaetze der letzten Wochen, je Muskel aufgeschluesselt - Grundlage des
     * Erholungsmodells.
     *
     * <p>Bewusst roh statt aggregiert: die Ermuedung braucht Gewicht, Wiederholungen und
     * Zeitpunkt jedes einzelnen Satzes, weil sie mit der Intensitaet ueberproportional und
     * mit dem Alter exponentiell rechnet. Beides laesst sich nicht vorsummieren.
     */
    @Query(value = """
            SELECT pm.muscle    AS muscle,
                   1.0          AS factor,
                   s.exercise_id AS exerciseId,
                   ws.id         AS sessionId,
                   s.weight      AS weight,
                   s.reps        AS reps,
                   ws.start_time AS performedAt
            FROM exercise_sets s
            JOIN workout_sessions ws ON ws.id = s.workout_session_id
            JOIN exercise_primary_muscles pm ON pm.exercise_id = s.exercise_id
            WHERE ws.user_id = :userId
              AND ws.is_completed = TRUE
              AND s.is_completed = TRUE
              AND (s.set_type IS NULL OR s.set_type NOT IN ('WARMUP', 'RESTPAUSE'))
              AND ws.start_time >= :since
            UNION ALL
            SELECT sm.muscle, 0.5, s.exercise_id, ws.id, s.weight, s.reps, ws.start_time
            FROM exercise_sets s
            JOIN workout_sessions ws ON ws.id = s.workout_session_id
            JOIN exercise_secondary_muscles sm ON sm.exercise_id = s.exercise_id
            WHERE ws.user_id = :userId
              AND ws.is_completed = TRUE
              AND s.is_completed = TRUE
              AND (s.set_type IS NULL OR s.set_type NOT IN ('WARMUP', 'RESTPAUSE'))
              AND ws.start_time >= :since
            """, nativeQuery = true)
    List<RecoverySetRow> recoverySets(@Param("userId") Long userId,
                                      @Param("since") LocalDateTime since);

    /**
     * Bestes geschaetztes 1RM je Uebung ueber die gesamte Historie (Epley).
     *
     * <p>Bewusst ohne Zeitfenster: die Intensitaet eines Satzes misst sich an dem, was
     * ueberhaupt moeglich ist, nicht an dem, was zufaellig in den letzten Wochen stand.
     */
    @Query(value = """
            SELECT s.exercise_id                            AS exerciseId,
                   MAX(s.weight * (1 + s.reps / 30.0))      AS bestOneRm
            FROM exercise_sets s
            JOIN workout_sessions ws ON ws.id = s.workout_session_id
            WHERE ws.user_id = :userId
              AND ws.is_completed = TRUE
              AND s.is_completed = TRUE
              AND s.weight > 0
              AND s.reps > 0
            GROUP BY s.exercise_id
            """, nativeQuery = true)
    List<ExerciseOneRmRow> bestOneRepMaxPerExercise(@Param("userId") Long userId);
}
