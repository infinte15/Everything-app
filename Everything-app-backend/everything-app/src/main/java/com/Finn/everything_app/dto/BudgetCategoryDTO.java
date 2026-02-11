package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BudgetCategoryDTO {
    private Long id;

    @NotBlank(message = "Kategoriename erforderlich")
    @Size(max = 100, message = "Name darf maximal 100 Zeichen lang sein")
    private String name;

    private String description;

    @NotNull(message = "Budget-Limit erforderlich")
    @Min(value = 0, message = "Budget kann nicht negativ sein")
    private Double budgetLimit;

    private String period;

    private LocalDate periodStart;
    private LocalDate periodEnd;


    private Double currentSpent;
    private Double remainingBudget;
    private Integer percentageUsed;

    private String color;
    private String icon;

    private Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
