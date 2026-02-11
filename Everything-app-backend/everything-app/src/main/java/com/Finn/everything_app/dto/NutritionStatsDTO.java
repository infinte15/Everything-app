package com.Finn.everything_app.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NutritionStatsDTO {
    private Integer totalCalories;
    private Double totalProtein;
    private Double totalCarbs;
    private Double totalFat;

    private Integer targetCalories;
    private Double targetProtein;
    private Double targetCarbs;
    private Double targetFat;

    private Integer mealsPlanned;
    private Integer mealsCompleted;
}
