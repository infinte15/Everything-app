package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.FinanceTransaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    // ==================== Bankimport ====================

    // Dedup einer einzelnen Buchung
    boolean existsByUserIdAndExternalId(Long userId, String externalId);

    Optional<FinanceTransaction> findByUserIdAndExternalId(Long userId, String externalId);

    /** Ein Sync holt bis zu 90 Tage auf einmal - ein SELECT statt eines pro Buchung. */
    @Query("SELECT ft.externalId FROM FinanceTransaction ft " +
            "WHERE ft.user.id = :userId " +
            "AND ft.externalId IS NOT NULL " +
            "AND ft.transactionDate >= :from")
    Set<String> findExternalIdsSince(@Param("userId") Long userId, @Param("from") LocalDate from);

    /**
     * Buchungen, deren Kategorie die Auto-Kategorisierung anfassen darf.
     *
     * <p>Bewusst als JPQL und nicht als abgeleitetes {@code ...AndCategoryLockedFalse}: Zeilen aus
     * der Zeit vor der Spalte tragen {@code category_locked = NULL}, und die wuerde eine
     * abgeleitete Abfrage uebersehen.
     */
    @Query("SELECT ft FROM FinanceTransaction ft " +
            "WHERE ft.user.id = :userId " +
            "AND (ft.categoryLocked IS NULL OR ft.categoryLocked = false) " +
            "ORDER BY ft.transactionDate DESC")
    List<FinanceTransaction> findRecategorizable(@Param("userId") Long userId);

    // Vertragserkennung: Buchungshistorie chronologisch, um Abstaende zu bilden
    List<FinanceTransaction> findByUserIdAndTransactionDateAfterOrderByTransactionDateAsc(
            Long userId,
            LocalDate from
    );

    List<FinanceTransaction> findByContractId(Long contractId);

    // Beim Trennen einer Verbindung: Kontobezug loesen, Buchungen behalten
    List<FinanceTransaction> findByBankAccountId(Long bankAccountId);

}