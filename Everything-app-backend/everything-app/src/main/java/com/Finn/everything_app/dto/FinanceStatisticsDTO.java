package com.Finn.everything_app.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FinanceStatisticsDTO {
    private Double totalIncome;
    private Double totalExpenses;
    private Double netBalance;

    private Double monthlyIncome;
    private Double monthlyExpenses;
    private Double monthlySavings;

    private Map<String, Double> expensesByCategory;
    private Map<String, Double> incomeByCategory;

    private Double averageDailyExpenses;
    private Double projectedMonthlyExpenses;

    private String topExpenseCategory;
    private Double topExpenseAmount;
}
