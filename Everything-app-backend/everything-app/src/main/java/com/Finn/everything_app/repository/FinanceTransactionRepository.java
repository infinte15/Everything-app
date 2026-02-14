package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.FinanceTransaction;
import com.Finn.everything_app.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface FinanceTransactionRepository extends JpaRepository<FinanceTransaction, Long> {


    // Alle Transaktionen
    List<FinanceTransaction> findByUserId(Long userId);

    // Transaktionen nach Typ
    List<FinanceTransaction> findByUserIdAndType(Long userId, String type);

    // Transaktionen in Zeitraum
    List<FinanceTransaction> findByUserIdAndTransactionDateBetween(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    // Transaktionen chronologisch
    List<FinanceTransaction> findByUserIdOrderByTransactionDateDesc(Long userId);

    // Transaktionen nach Kategorie
    List<FinanceTransaction> findByUserIdAndCategory(Long userId, String category);

    // Transaktionen nach Tag (vereinfacht)
    @Query("SELECT ft FROM FinanceTransaction ft " +
            "WHERE ft.user.id = :userId AND ft.tags LIKE %:tag%")
    List<FinanceTransaction> findByUserIdAndTag(
            @Param("userId") Long userId,
            @Param("tag") String tag
    );

    // Gesamte Einnahmen in Zeitraum
    @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinanceTransaction ft " +
            "WHERE ft.user.id = :userId " +
            "AND ft.type = 'EINNAHME' " +
            "AND ft.transactionDate BETWEEN :startDate AND :endDate")
    Double getTotalIncome(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Gesamte Ausgaben in Zeitraum
    @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinanceTransaction ft " +
            "WHERE ft.user.id = :userId " +
            "AND ft.type = 'AUSGABE' " +
            "AND ft.transactionDate BETWEEN :startDate AND :endDate")
    Double getTotalExpenses(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Ausgaben nach Kategorie
    @Query("SELECT ft.category, SUM(ft.amount) FROM FinanceTransaction ft " +
            "WHERE ft.user.id = :userId " +
            "AND ft.type = 'AUSGABE' " +
            "AND ft.transactionDate BETWEEN :startDate AND :endDate " +
            "GROUP BY ft.category " +
            "ORDER BY SUM(ft.amount) DESC")
    List<Object[]> getExpensesByCategory(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Top Ausgaben-Kategorien
    @Query("SELECT ft.category FROM FinanceTransaction ft " +
            "WHERE ft.user.id = :userId " +
            "AND ft.type = 'AUSGABE' " +
            "GROUP BY ft.category " +
            "ORDER BY SUM(ft.amount) DESC")
    List<String> getTopExpenseCategories(@Param("userId") Long userId);

    // Anzahl Transaktionen in Zeitraum
    Long countByUserIdAndTransactionDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

}