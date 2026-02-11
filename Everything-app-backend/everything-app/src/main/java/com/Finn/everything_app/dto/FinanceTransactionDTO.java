package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FinanceTransactionDTO {
    private Long id;

    @NotNull(message = "Betrag erforderlich")
    private Double amount;

    @NotBlank(message = "Typ erforderlich")
    private String type;

    @NotBlank(message = "Kategorie erforderlich")
    private String category;

    private String subcategory;

    @NotBlank(message = "Beschreibung erforderlich")
    @Size(max = 500, message = "Beschreibung darf maximal 500 Zeichen lang sein")
    private String description;

    @NotNull(message = "Datum erforderlich")
    private LocalDate transactionDate;

    private String paymentMethod;

    private Long budgetCategoryId;

    private String tags;

    private String receiptUrl;

    private Boolean isRecurring;
    private String recurringFrequency;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}