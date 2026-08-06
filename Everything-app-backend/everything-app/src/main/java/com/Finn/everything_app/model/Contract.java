package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Eine wiederkehrende Zahlung - Abo, Miete, Versicherung, aber auch das Gehalt.
 *
 * <p>Loest mittelfristig den Behelf ab, "Vertrag" als Transaktion mit {@code isRecurring = true}
 * zu fuehren. Bis die Oberflaeche umgestellt ist, existieren beide Begriffe nebeneinander.
 *
 * <h2>frequency vs. intervalDays</h2>
 * Die beiden Felder sind redundant, und wer sie verwechselt, baut eine Prognose, die um Wochen
 * danebenliegt:
 * <ul>
 *   <li>{@link #intervalDays} ist der <em>gemessene</em> Median-Abstand zwischen zwei Buchungen
 *       und damit massgeblich fuer jede Datumsrechnung, insbesondere {@link #nextDueDate}.</li>
 *   <li>{@link #frequency} ist das <em>klassifizierte</em> Etikett und massgeblich fuer die
 *       Gruppierung in der Oberflaeche sowie fuer die Monatsnormalisierung der Prognose
 *       (QUARTERLY entspricht {@code amount / 3} pro Monat).</li>
 * </ul>
 *
 * <p>Bewusst ohne Waehrung: {@link BankAccount} fuehrt sie, und eine Waehrung hier wuerde
 * Summenbildungen einen Umrechnungspfad vorgaukeln, den es nirgends gibt.
 */
@Entity
@Table(name = "contracts",
        indexes = {
                @Index(name = "idx_contracts_user", columnList = "user_id"),
                @Index(name = "idx_contracts_user_key", columnList = "user_id, counterparty_key")
        })
@Data
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Anzeigename, vom Nutzer aenderbar. */
    @Column(nullable = false, length = 200)
    private String name;

    /** Normalisierte Gegenpartei (klein, ohne Filialnummern und Datumsfragmente) - der Gruppierungsschluessel der Erkennung. */
    @Column(name = "counterparty_key", nullable = false, length = 200)
    private String counterpartyKey;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(length = 100)
    private String subcategory;

    /**
     * Ausgabe oder Einnahme. Ohne dieses Feld kann die Prognose "Miete 850" nicht von
     * "Gehalt 2400" unterscheiden - und ist damit keine.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType direction = TransactionType.EXPENSE;

    /** Immer ein positiver Betrag; das Vorzeichen traegt {@link #direction}. */
    @Column(nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractFrequency frequency = ContractFrequency.MONTHLY;

    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(name = "last_booking_date")
    private LocalDate lastBookingDate;

    /** Null bei gekuendigten Vertraegen - Sortierungen muessen das aushalten. */
    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    /** Anzahl der Buchungen, aus denen der Vertrag erkannt wurde - das Konfidenzsignal der Oberflaeche. */
    @Column(name = "occurrence_count")
    private Integer occurrenceCount = 0;

    @Column
    private Boolean active = true;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** Manuell angelegte oder korrigierte Vertraege ({@code false}) darf die Erkennung nicht ueberschreiben. */
    @Column(name = "detected_automatically")
    private Boolean detectedAutomatically = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
