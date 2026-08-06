package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Ein einzelnes Konto hinter einer {@link BankConnection}.
 *
 * <p>{@link #user} ist gegenueber {@code connection.getUser()} denormalisiert. Jede Abfrage in
 * diesem Projekt ist nutzer-skopiert ({@code findByUserId...}); der Umweg ueber die Verbindung
 * waere ein zusaetzlicher Join auf genau dem Pfad, den der Import am haeufigsten geht.
 *
 * <p><strong>Identitaet haengt an {@link #identificationHash}, nicht an {@link #accountUid}.</strong>
 * Der UID gilt laut Enable-Banking-Spezifikation nur, solange die Sitzung autorisiert ist - nach
 * einer Neu-Autorisierung traegt dasselbe Konto einen anderen UID. Wer darauf dedupliziert, legt
 * bei jeder erneuten Zustimmung ein zweites Konto samt kompletter Buchungshistorie an.
 */
@Entity
@Table(name = "bank_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_bank_accounts_user_hash",
                columnNames = {"user_id", "identification_hash"}),
        indexes = {
                @Index(name = "idx_bank_accounts_user", columnList = "user_id"),
                @Index(name = "idx_bank_accounts_connection", columnList = "bank_connection_id")
        })
@Data
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stabiler Kontoschluessel ueber Sitzungen hinweg ({@code identification_hash}). Der Upsert
     * beim Sync laeuft ueber dieses Feld, und die ersten acht Zeichen stecken im
     * {@code externalId} jeder importierten Buchung - {@code entry_reference} allein ist laut
     * Spezifikation nicht kontouebergreifend eindeutig.
     */
    @Column(name = "identification_hash", nullable = false, length = 128)
    private String identificationHash;

    /**
     * Sitzungsgebundener UID von Enable Banking; nur damit lassen sich Salden und Umsaetze
     * abrufen. Wird bei jedem Sitzungsstart neu gesetzt und ist deshalb <em>kein</em>
     * Identitaetsmerkmal (siehe Klassenkommentar).
     *
     * <p>Nullable, weil die Spezifikation Konten ohne UID kennt - etwa gesperrte oder aufgeloeste.
     * Solche Konten werden beim Sync uebersprungen.
     */
    @Column(name = "account_uid", length = 100)
    private String accountUid;

    @Column(length = 34)
    private String iban;

    @Column(name = "display_name", length = 200)
    private String displayName;

    /**
     * Waehrung lebt nur hier - Enable Banking meldet sie pro Konto, und die Prognose hat nirgends
     * einen Umrechnungspfad. Ein Nicht-EUR-Konto muss der Sync deshalb an dieser Stelle ablehnen.
     */
    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(name = "current_balance")
    private Double currentBalance;

    @Column(name = "balance_updated_at")
    private LocalDateTime balanceUpdatedAt;

    /**
     * Ein Sparkassen-Consent liefert typischerweise Giro + Tagesgeld + Kreditkarte. Ohne diesen
     * Schalter kann der Sync nicht auf das Girokonto beschraenkt werden und die Prognose zaehlt
     * interne Umbuchungen doppelt.
     */
    @Column(name = "sync_enabled")
    private Boolean syncEnabled = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_connection_id", nullable = false)
    private BankConnection connection;

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
