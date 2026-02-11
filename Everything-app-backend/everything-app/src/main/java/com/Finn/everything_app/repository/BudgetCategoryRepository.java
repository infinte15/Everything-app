package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.BudgetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetCategoryRepository extends JpaRepository<BudgetCategory, Long> {

    List<BudgetCategory> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<BudgetCategory> findByUserIdAndIsActiveTrue(Long userId);

    List<BudgetCategory> findByUserIdAndIsActiveFalse(Long userId);

    Optional<BudgetCategory> findByUserIdAndName(Long userId, String name);

    List<BudgetCategory> findByUserIdAndPeriod(Long userId, String period);

    List<BudgetCategory> findByUserIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
            Long userId,
            LocalDate date1,
            LocalDate date2
    );

    boolean existsByUserIdAndName(Long userId, String name);
}