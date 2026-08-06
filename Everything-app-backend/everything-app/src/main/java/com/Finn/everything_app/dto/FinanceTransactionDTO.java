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

    // ==================== Bankimport ====================

    /**
     * Gegenpartei - in der Liste die grosse Zeile, der Verwendungszweck die kleine. Bei manuellen
     * Buchungen {@code null}; dann traegt die Beschreibung allein.
     */
    private String counterparty;

    /** {@code MANUAL} oder {@code BANK}. Altbestaende koennen {@code null} tragen. */
    private String source;

    /** Der Nutzer hat die Kategorie selbst gesetzt - kein Vorschlag der Automatik. */
    private Boolean categoryLocked;

    /** Gesetzt, wenn die Buchung zu einem erkannten Vertrag gehoert. */
    private Long contractId;

    private LocalDate valueDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}