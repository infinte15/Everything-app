package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * Eine Buchung - von Hand eingetippt oder aus dem Bankimport.
 *
 * <p>Die Bank-Felder sind eine Erweiterung, kein Ersatz: manuelle Buchungen bleiben unveraendert
 * gueltig und tragen {@code externalId = null}, {@code bankAccount = null} und
 * {@link TransactionSource#MANUAL}.
 *
 * <p>{@link #type} bleibt ein {@code String} mit "EINNAHME"/"AUSGABE" - siehe
 * {@link TransactionType} fuer die Bruecke ins typsichere Enum.
 *
 * <p>Die hier deklarierten UNIQUE- und Index-Bedingungen wirken nur auf frisch erzeugten
 * Datenbanken (und in den H2-Tests). Auf einer gewachsenen Datenbank traegt ddl-auto=update sie
 * nicht mehr nach; dafuer gibt es db/manual/2026-08-06-finance-bank-import.sql.
 */
@Entity
@Table(name = "finance_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_finance_tx_user_external",
                columnNames = {"user_id", "external_id"}),
        indexes = {
                @Index(name = "idx_finance_tx_user_date", columnList = "user_id, transaction_date"),
                @Index(name = "idx_finance_tx_bank_account", columnList = "bank_account_id"),
                @Index(name = "idx_finance_tx_contract", columnList = "contract_id")
        })
@Data
public class FinanceTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 100)
    private String category;
    @Column(length = 100)
    private String subcategory;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(length = 500)
    private String tags;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    @Column(name = "is_recurring")
    private Boolean isRecurring = false;

    @Column(name = "recurring_frequency", length = 50)
    private String recurringFrequency;

    // ==================== Bankimport ====================

    /**
     * Dedup-Schluessel des Imports; {@code null} bei manuellen Buchungen.
     *
     * <p>Enthaelt die {@code entry_reference} von Enable Banking - die PSD2 aber nur als optional
     * vorschreibt. Liefert eine Bank keine, muss der Sync einen berechneten Ersatz in dieselbe
     * Spalte schreiben (z.B. {@code "h:" + sha256(...)}, hex = 64 Zeichen); die Laenge 200 deckt
     * beide Faelle, und der eine UNIQUE-Index deckt beide Pfade.
     */
    @Column(name = "external_id", length = 200)
    private String externalId;

    /** Name der Gegenpartei (creditor/debtor) - Grundlage der Kategorisierung und Vertragserkennung. */
    @Column(length = 300)
    private String counterparty;

    /** Wertstellung. {@link #transactionDate} bleibt das Buchungsdatum. */
    @Column(name = "value_date")
    private LocalDate valueDate;

    /**
     * Herkunft der Buchung.
     *
     * <p>Trotz konzeptioneller Pflicht ohne {@code nullable = false}: ddl-auto=update setzt fuer
     * eine neue Spalte ein nacktes {@code ALTER TABLE ... ADD COLUMN ... NOT NULL} ohne Default ab.
     * Auf einer Tabelle mit Bestandszeilen scheitert das und bricht den gesamten
     * Schema-Update-Durchlauf ab - inklusive der neuen Tabellen. Lesende Stellen behandeln
     * {@code null} deshalb wie {@link TransactionSource#MANUAL}; die Handmigration zieht die
     * Altbestaende einmalig nach. Gleiches Muster wie bei {@link #isRecurring}.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TransactionSource source = TransactionSource.MANUAL;

    /**
     * Der Nutzer hat die Kategorie selbst gesetzt - die Auto-Kategorisierung fasst sie nicht mehr an.
     * Nullable aus demselben Grund wie {@link #source}; immer mit {@code Boolean.TRUE.equals(...)} lesen.
     */
    @Column(name = "category_locked")
    private Boolean categoryLocked = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_category_id")
    private BudgetCategory budgetCategory;

    /** Null bei manuellen Buchungen. Bewusst ohne cascade - vgl. Project.tasks. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id")
    private BankAccount bankAccount;

    /** Von der Vertragserkennung gesetzt; parallel dazu wird {@link #isRecurring} auf true gezogen. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

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
