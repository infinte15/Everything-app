package com.Finn.everything_app.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BudgetProgressDTO {
    private Long budgetId;
    private String categoryName;

    private Double budgetLimit;
    private Double currentSpent;
    private Double remainingBudget;

    private Integer percentageUsed;
    private Boolean isOverBudget;

    private String status;
    private String color;
}
