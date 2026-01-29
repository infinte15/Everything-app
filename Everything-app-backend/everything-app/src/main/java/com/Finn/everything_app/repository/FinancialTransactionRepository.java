package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.FinancialTransaction;
import com.Finn.everything_app.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    // Alle Transaktionen
    List<FinancialTransaction> findByUserId(Long userId);

    // Transaktionen nach Typ
    List<FinancialTransaction> findByUserIdAndType(Long userId, TransactionType type);

    // Transaktionen in Zeitraum
    List<FinancialTransaction> findByUserIdAndDateBetween(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    // Transaktionen chronologisch
    List<FinancialTransaction> findByUserIdOrderByDateDesc(Long userId);

    // Transaktionen nach Kategorie
    List<FinancialTransaction> findByUserIdAndCategory(Long userId, String category);

    // Transaktionen nach Tag
    @Query("SELECT ft FROM FinancialTransaction ft JOIN ft.tags t " +
            "WHERE ft.user.id = :userId AND t = :tag")
    List<FinancialTransaction> findByUserIdAndTag(
            @Param("userId") Long userId,
            @Param("tag") String tag
    );

    // Gesamte Einnahmen in Zeitraum
    @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinancialTransaction ft " +
            "WHERE ft.user.id = :userId " +
            "AND ft.type = 'INCOME' " +
            "AND ft.date BETWEEN :startDate AND :endDate")
    BigDecimal getTotalIncome(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Gesamte Ausgaben in Zeitraum
    @Query("SELECT COALESCE(SUM(ft.amount), 0) FROM FinancialTransaction ft " +
            "WHERE ft.user.id = :userId " +
            "AND ft.type = 'EXPENSE' " +
            "AND ft.date BETWEEN :startDate AND :endDate")
    BigDecimal getTotalExpenses(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Ausgaben nach Kategorie
    @Query("SELECT ft.category, SUM(ft.amount) FROM FinancialTransaction ft " +
            "WHERE ft.user.id = :userId " +
            "AND ft.type = 'EXPENSE' " +
            "AND ft.date BETWEEN :startDate AND :endDate " +
            "GROUP BY ft.category " +
            "ORDER BY SUM(ft.amount) DESC")
    List<Object[]> getExpensesByCategory(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Top Ausgaben-Kategorien
    @Query("SELECT ft.category FROM FinancialTransaction ft " +
            "WHERE ft.user.id = :userId " +
            "AND ft.type = 'EXPENSE' " +
            "GROUP BY ft.category " +
            "ORDER BY SUM(ft.amount) DESC")
    List<String> getTopExpenseCategories(@Param("userId") Long userId);

    // Anzahl Transaktionen in Zeitraum
    Long countByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
}