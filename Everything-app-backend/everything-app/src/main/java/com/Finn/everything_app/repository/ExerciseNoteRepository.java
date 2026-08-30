package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.ExerciseNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExerciseNoteRepository extends JpaRepository<ExerciseNote, Long> {

    Optional<ExerciseNote> findByUserIdAndExerciseId(Long userId, Long exerciseId);

    /** Alle Notizen zu einer Menge von Uebungen - eine Abfrage fuer den Trainingsstart. */
    List<ExerciseNote> findByUserIdAndExerciseIdIn(Long userId, Collection<Long> exerciseIds);

    void deleteByUserIdAndExerciseId(Long userId, Long exerciseId);
}
