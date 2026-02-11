package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.BudgetCategoryDTO;
import com.Finn.everything_app.model.BudgetCategory;
import org.springframework.stereotype.Component;

@Component
public class BudgetCategoryMapper {

    public BudgetCategoryDTO toDTO(BudgetCategory category) {
        if (category == null) return null;

        BudgetCategoryDTO dto = new BudgetCategoryDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setBudgetLimit(category.getBudgetLimit());
        dto.setPeriod(category.getPeriod());
        dto.setPeriodStart(category.getPeriodStart());
        dto.setPeriodEnd(category.getPeriodEnd());
        dto.setCurrentSpent(category.getCurrentSpent());
        dto.setRemainingBudget(category.getRemainingBudget());
        dto.setPercentageUsed(category.getPercentageUsed());
        dto.setColor(category.getColor());
        dto.setIcon(category.getIcon());
        dto.setIsActive(category.getIsActive());
        dto.setCreatedAt(category.getCreatedAt());
        dto.setUpdatedAt(category.getUpdatedAt());

        return dto;
    }

    public BudgetCategory toEntity(BudgetCategoryDTO dto) {
        if (dto == null) return null;

        BudgetCategory category = new BudgetCategory();
        category.setId(dto.getId());
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setBudgetLimit(dto.getBudgetLimit());
        category.setPeriod(dto.getPeriod());
        category.setPeriodStart(dto.getPeriodStart());
        category.setPeriodEnd(dto.getPeriodEnd());
        category.setColor(dto.getColor());
        category.setIcon(dto.getIcon());
        category.setIsActive(dto.getIsActive());

        return category;
    }
}