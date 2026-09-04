package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Habit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitRepository extends JpaRepository<Habit, Long> {

    /**
     * Laedt die Erledigungen gleich mit.
     *
     * <p>Ohne den EntityGraph war das ein 500er: {@code completions} ist wie jede
     * {@code @OneToMany} traege, {@code spring.jpa.open-in-view=false} schliesst die Session am
     * Ende der Service-Methode, und der {@code HabitMapper} liest die Sammlung erst danach im
     * Controller - also mit einer {@code LazyInitializationException} statt einer Antwort.
     * Betroffen war jeder Abruf von {@code GET /api/habits}, sobald eine Gewohnheit ueberhaupt
     * eine Erledigung hatte.
     *
     * <p>Ein Fetch-Join statt eines nachgereichten Ladens auch deshalb, weil die Alternative
     * eine Abfrage pro Gewohnheit waere.
     */
    @EntityGraph(attributePaths = "completions")
    List<Habit> findByUserId(Long userId);

    /**
     * Wie {@link #findById}, aber mit den Erledigungen - fuer alles, was die Gewohnheit
     * anschliessend als DTO herausgibt.
     */
    @EntityGraph(attributePaths = "completions")
    Optional<Habit> findWithCompletionsById(Long id);

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