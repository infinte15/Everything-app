package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Exercise;
import com.Finn.everything_app.model.RoutineExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface RoutineExerciseRepository extends JpaRepository<RoutineExercise, Long> {

    List<RoutineExercise> findByRoutineIdOrderByOrderIndexAsc(Long routineId);

    /**
     * Loest den Routinen-Bezug bereits trainierter Einheiten, bevor die Routine geloescht wird.
     * Die Historie selbst bleibt unangetastet.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WorkoutSession ws set ws.routine = null where ws.routine.id = :routineId")
    void detachSessionsFromRoutine(@Param("routineId") Long routineId);

    /** Biegt geplante Uebungen auf eine andere Katalog-Zeile um (Katalog-Wechsel). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RoutineExercise re set re.exercise = :target where re.exercise = :source")
    int repointExercise(@Param("source") Exercise source, @Param("target") Exercise target);

    /**
     * Die primaer beanspruchten Muskeln je Routine - eine Zeile je Paar (Routine, Muskel).
     *
     * <p>In einer Abfrage statt ueber {@code routine.getExercises()}: der Planer braucht das fuer
     * jede Routine im Horizont, und ein Lazy-Durchgriff je Routine waere genau das N+1, das die
     * Laufzeit des Laufs bestimmt.
     *
     * <p>Nur die primaeren Muskeln. Unterstuetzende Muskeln kommen in fast jeder Uebung vor -
     * naehme man sie mit, ueberlappte jede Routine mit jeder, und der Abstand waere wieder
     * ueberall gleich.
     */
    @Query("select re.routine.id, m from RoutineExercise re "
            + "join re.exercise e join e.primaryMuscles m "
            + "where re.routine.id in :routineIds")
    List<Object[]> findPrimaryMusclesByRoutineIds(@Param("routineIds") Collection<Long> routineIds);

    /** Jede Uebung, an der noch eine Routine haengt. */
    @Query("select distinct re.exercise.id from RoutineExercise re where re.exercise is not null")
    Set<Long> findReferencedExerciseIds();
}
