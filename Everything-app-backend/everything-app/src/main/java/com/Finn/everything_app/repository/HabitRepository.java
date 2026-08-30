package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Habit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitRepository extends JpaRepository<Habit, Long> {
    List<Habit> findByUserId(Long userId);

    /** Die aus einer Gym-Routine erzeugte Gewohnheit, falls es sie gibt. */
    Optional<Habit> findByRoutineId(Long routineId);

    @Query("SELECT h FROM Habit h WHERE h.user.id = :userId " +
            "AND (h.endDate IS NULL OR h.endDate >= :date) " +
            "AND h.startDate <= :date")
    List<Habit> findActiveHabits(Long userId, LocalDate date);

    @Query("SELECT h FROM Habit h WHERE h.user.id = :userId " +
            "AND (h.endDate IS NULL OR h.endDate >= :startDate) " +
            "AND h.startDate <= :endDate")
    List<Habit> findHabitsActiveInRange(Long userId, LocalDate startDate, LocalDate endDate);
}