package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {

    List<MealPlan> findByUserIdAndDateBetween(Long userId, LocalDate start, LocalDate end);

    List<MealPlan> findByUserIdAndDate(Long userId, LocalDate date);

    List<MealPlan> findByUserIdAndMealType(Long userId, String mealType);

    List<MealPlan> findByUserIdAndIsCompletedTrue(Long userId);

    List<MealPlan> findByUserIdAndIsCompletedFalse(Long userId);
}