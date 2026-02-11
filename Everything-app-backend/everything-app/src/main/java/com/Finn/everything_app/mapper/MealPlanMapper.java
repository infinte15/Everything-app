package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.MealPlanDTO;
import com.Finn.everything_app.model.MealPlan;
import org.springframework.stereotype.Component;

@Component
public class MealPlanMapper {

    public MealPlanDTO toDTO(MealPlan mealPlan) {
        if (mealPlan == null) return null;

        MealPlanDTO dto = new MealPlanDTO();
        dto.setId(mealPlan.getId());
        dto.setDate(mealPlan.getDate());
        dto.setMealType(mealPlan.getMealType());
        dto.setRecipeId(mealPlan.getRecipe() != null ? mealPlan.getRecipe().getId() : null);
        dto.setRecipeName(mealPlan.getRecipe() != null ? mealPlan.getRecipe().getName() : null);
        dto.setPlannedServings(mealPlan.getPlannedServings());
        dto.setIsCompleted(mealPlan.getIsCompleted());
        dto.setCompletedAt(mealPlan.getCompletedAt());
        dto.setNotes(mealPlan.getNotes());
        dto.setCreatedAt(mealPlan.getCreatedAt());

        return dto;
    }

    public MealPlan toEntity(MealPlanDTO dto) {
        if (dto == null) return null;

        MealPlan mealPlan = new MealPlan();
        mealPlan.setId(dto.getId());
        mealPlan.setDate(dto.getDate());
        mealPlan.setMealType(dto.getMealType());
        mealPlan.setPlannedServings(dto.getPlannedServings());
        mealPlan.setIsCompleted(dto.getIsCompleted());
        mealPlan.setCompletedAt(dto.getCompletedAt());
        mealPlan.setNotes(dto.getNotes());

        return mealPlan;
    }
}