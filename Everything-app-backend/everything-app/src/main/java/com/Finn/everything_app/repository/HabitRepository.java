package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Habit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface HabitRepository extends JpaRepository<Habit, Long> {
    List<Habit> findByUserId(Long userId);

    @Query("SELECT h FROM Habit h WHERE h.user.id = :userId " +
            "AND (h.endDate IS NULL OR h.endDate >= :date) " +
            "AND h.startDate <= :date")
    List<Habit> findActiveHabits(Long userId, LocalDate date);
}