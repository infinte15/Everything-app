package com.Finn.everything_app.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyFinanceReportDTO {
    private String month;

    private Double totalIncome;
    private Double totalExpenses;
    private Double netSavings;

    private Map<String, Double> expensesByCategory;
    private Map<String, Double> incomeByCategory;

    private Integer totalTransactions;
    private Integer incomeTransactions;
    private Integer expenseTransactions;

    private Double averageDailyExpenses;
    private Double savingsRate;
    private String topExpenseCategory;
    private Double topExpenseAmount;

    private String comparisonToPreviousMonth;
    private Double changePercentage;
}
