package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Eine per PSD2 erteilte Zustimmung fuer ein Bankinstitut (Enable Banking).
 *
 * <p>Bewusst ohne Inverse-Collection auf {@link BankAccount}: mit Lombok {@code @Data} erzeugt eine
 * bidirektionale Beziehung gegenseitige {@code toString()}/{@code equals()}-Rekursion. Die Konten
 * einer Verbindung holt {@code BankAccountRepository.findByConnectionId(...)}.
 */
@Entity
@Table(name = "bank_connections",
        indexes = @Index(name = "idx_bank_connections_user", columnList = "user_id"))
@Data
public class BankConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Enable Banking identifiziert ein Institut ueber Name + Land, nicht ueber eine ID. */
    @Column(name = "aspsp_name", nullable = false, length = 200)
    private String aspspName;

    @Column(name = "aspsp_country", nullable = false, length = 2)
    private String aspspCountry = "DE";

    /** Erst nach dem Einloesen des Callbacks gesetzt. */
    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BankConnectionStatus status = BankConnectionStatus.PENDING;

    /** Ablauf der PSD2-Zustimmung (90-180 Tage je nach Bank). */
    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    /**
     * Verknuepft den Browser-Callback mit dem Nutzer - der Redirect bringt kein JWT mit.
     *
     * <p>Bewusst {@code String} und nicht {@code java.util.UUID}: der Wert kommt als ungeprueftes
     * Query-Argument aus dem Browser zurueck, und {@code UUID.fromString("muell")} wuerde noch vor
     * jedem Lookup werfen - eine Muell-Eingabe waere dann eine 500 statt eines sauberen
     * "unbekannter State".
     *
     * <p>Einmalig verwendbar: nach erfolgreichem Callback auf {@code null} setzen. Die
     * UNIQUE-Bedingung stoert das nicht, weil PostgreSQL NULL-Werte als verschieden behandelt.
     * Die Gueltigkeitsdauer wird gegen {@link #createdAt} geprueft, dafuer braucht es keine
     * eigene Spalte.
     */
    @Column(name = "auth_state", length = 36, unique = true)
    private String authState;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    /** Grund fuer {@link BankConnectionStatus#FAILED} - ein Fehlerzustand ohne Text ist nicht bedienbar. */
    @Column(name = "last_sync_error", length = 500)
    private String lastSyncError;

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
