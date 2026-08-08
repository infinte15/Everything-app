package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.HabitCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, Long> {
    List<HabitCompletion> findByHabitId(Long habitId);

    List<HabitCompletion> findByHabitIdAndCompletionDateBetween(Long habitId, LocalDate start, LocalDate end);

    /**
     * Sammelabfrage für den Scheduler. Die Einzelabfrage oben in einer Schleife über alle Habits
     * aufzurufen kostet eine Abfrage je Gewohnheit — bei jedem Neuplanen, und neu geplant wird
     * nach jeder Änderung am Kalender.
     */
    List<HabitCompletion> findByHabitIdInAndCompletionDateBetween(
            Collection<Long> habitIds, LocalDate start, LocalDate end);

    Optional<HabitCompletion> findByHabitIdAndCompletionDate(Long habitId, LocalDate date);
}