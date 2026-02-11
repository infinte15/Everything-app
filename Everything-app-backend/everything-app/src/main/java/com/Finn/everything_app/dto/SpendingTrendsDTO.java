package com.Finn.everything_app.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpendingTrendsDTO {
    private List<TrendDataPoint> expenseTrend;
    private List<TrendDataPoint> incomeTrend;

    private Double averageMonthlyExpense;
    private Double averageMonthlyIncome;

    private String trendDirection;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrendDataPoint {
        private LocalDate date;
        private String period;
        private Double amount;
        private Integer transactionCount;
    }
}