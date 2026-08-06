package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ein erkannter oder von Hand gepflegter Vertrag.
 *
 * <p>{@link #frequency} ist das Etikett fuer die Gruppierung, {@link #intervalDays} der gemessene
 * Abstand - jede Datumsrechnung laeuft ueber letzteren (siehe {@code Contract}).
 */
@Data
public class ContractDTO {

    private Long id;

    @NotBlank(message = "Name erforderlich")
    private String name;

    @NotBlank(message = "Kategorie erforderlich")
    private String category;

    private String subcategory;

    /** {@code INCOME} oder {@code EXPENSE}. */
    @NotBlank(message = "Richtung erforderlich")
    private String direction;

    @NotNull(message = "Betrag erforderlich")
    private Double amount;

    @NotBlank(message = "Rhythmus erforderlich")
    private String frequency;

    private Integer intervalDays;
    private LocalDate lastBookingDate;
    private LocalDate nextDueDate;

    /** "Erkannt aus N Buchungen" - macht nachvollziehbar, woher der Vertrag kommt. */
    private Integer occurrenceCount;

    private Boolean active;
    private LocalDateTime cancelledAt;

    /** {@code false} heisst: vom Nutzer angelegt oder korrigiert - die Erkennung fasst ihn nicht an. */
    private Boolean detectedAutomatically;

    /** Auf einen Monat normalisierter Betrag, damit die Oberflaeche Summen bilden kann. */
    private Double monthlyAmount;
}
