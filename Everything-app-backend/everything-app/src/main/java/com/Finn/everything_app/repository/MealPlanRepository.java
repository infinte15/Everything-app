package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.MealPlan;
import com.Finn.everything_app.model.MealType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {

    /** Ein Eintrag, aber nur der eigene - siehe {@link RecipeRepository#findByIdAndUserId}. */
    Optional<MealPlan> findByIdAndUserId(Long id, Long userId);

    List<MealPlan> findByUserIdAndDateBetween(Long userId, LocalDate start, LocalDate end);

    List<MealPlan> findByUserIdAndDate(Long userId, LocalDate date);

    List<MealPlan> findByUserIdAndMealType(Long userId, MealType mealType);

    List<MealPlan> findByUserIdAndIsCompletedTrue(Long userId);

    List<MealPlan> findByUserIdAndIsCompletedFalse(Long userId);

    /** Fuer die Wochenplanung: was in diesem Zeitraum schon steht, wird nicht doppelt geplant. */
    List<MealPlan> findByUserIdAndDateBetweenAndMealType(
            Long userId, LocalDate start, LocalDate end, MealType mealType);
}
