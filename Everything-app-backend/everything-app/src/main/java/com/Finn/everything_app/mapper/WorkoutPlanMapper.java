package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.WorkoutPlanDTO;
import com.Finn.everything_app.model.WorkoutPlan;
import org.springframework.stereotype.Component;

@Component
public class WorkoutPlanMapper {

    public WorkoutPlanDTO toDTO(WorkoutPlan plan) {
        if (plan == null) return null;

        WorkoutPlanDTO dto = new WorkoutPlanDTO();
        dto.setId(plan.getId());
        dto.setName(plan.getName());
        dto.setDescription(plan.getDescription());
        dto.setGoal(plan.getGoal());
        dto.setDifficulty(plan.getDifficulty());
        dto.setDurationWeeks(plan.getDurationWeeks());
        dto.setWorkoutsPerWeek(plan.getWorkoutsPerWeek());
        dto.setStartDate(plan.getStartDate());
        dto.setEndDate(plan.getEndDate());
        dto.setIsActive(plan.getIsActive());
        dto.setTotalWorkouts(plan.getTotalWorkouts());
        dto.setCompletedWorkouts(plan.getCompletedWorkouts());
        dto.setCreatedAt(plan.getCreatedAt());
        dto.setUpdatedAt(plan.getUpdatedAt());

        return dto;
    }

    public WorkoutPlan toEntity(WorkoutPlanDTO dto) {
        if (dto == null) return null;

        WorkoutPlan plan = new WorkoutPlan();
        plan.setId(dto.getId());
        plan.setName(dto.getName());
        plan.setDescription(dto.getDescription());
        plan.setGoal(dto.getGoal());
        plan.setDifficulty(dto.getDifficulty());
        plan.setDurationWeeks(dto.getDurationWeeks());
        plan.setWorkoutsPerWeek(dto.getWorkoutsPerWeek());
        plan.setStartDate(dto.getStartDate());
        plan.setEndDate(dto.getEndDate());
        plan.setIsActive(dto.getIsActive());

        return plan;
    }
}