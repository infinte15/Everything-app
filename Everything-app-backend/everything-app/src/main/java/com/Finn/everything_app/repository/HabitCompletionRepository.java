package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.HabitCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, Long> {
    List<HabitCompletion> findByHabitId(Long habitId);

    List<HabitCompletion> findByHabitIdAndCompletionDateBetween(Long habitId, LocalDate start, LocalDate end);

    Optional<HabitCompletion> findByHabitIdAndCompletionDate(Long habitId, LocalDate date);
}