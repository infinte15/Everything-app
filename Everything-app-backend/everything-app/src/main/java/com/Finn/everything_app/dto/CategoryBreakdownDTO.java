package com.Finn.everything_app.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryBreakdownDTO {
    private Map<String, Double> expensesByCategory;
    private Map<String, Double> incomeByCategory;
    private Map<String, Integer> transactionCountByCategory;

    private String topExpenseCategory;
    private Double topExpenseAmount;

    private String topIncomeCategory;
    private Double topIncomeAmount;
}
