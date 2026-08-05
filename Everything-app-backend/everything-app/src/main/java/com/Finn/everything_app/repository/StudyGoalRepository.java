package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.StudyGoal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyGoalRepository extends JpaRepository<StudyGoal, Long> {

    // Die EntityGraphs sind Pflicht, nicht Optimierung: in Tests ist open-in-view abgeschaltet,
    // und der Mapper fasst course.name und course.color an. Ohne sie fliegt dort eine
    // LazyInitializationException, während es in der laufenden App zufällig gutginge.

    @EntityGraph(attributePaths = {"course", "task"})
    Optional<StudyGoal> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"course", "task"})
    List<StudyGoal> findByUserIdOrderByIdAsc(Long userId);

    /** Ein Ziel je Modul — sonst konkurrierten zwei Brücken-Tasks um dieselben Stunden. */
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    @EntityGraph(attributePaths = {"task"})
    List<StudyGoal> findByCourseId(Long courseId);

    /**
     * Der Rückweg vom Brücken-Task zum Ziel; {@code StudyGoal.task} ist einseitig, {@code Task}
     * kennt sein Ziel nicht. Wird gebraucht, wenn ein Kalenderblock abgehakt wird und die
     * Minuten ans Ziel statt an den Task gehen sollen.
     *
     * {@code @EntityGraph} ist hier Pflicht, nicht Optimierung: {@code syncTask} fasst
     * {@code course.getName()} und {@code user.getId()} an, und die Tests laufen ohne
     * open-in-view.
     */
    @EntityGraph(attributePaths = {"course", "user", "task"})
    Optional<StudyGoal> findByTaskIdAndUserId(Long taskId, Long userId);
}
