package com.Finn.everything_app.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Eine Bankverbindung, wie die App sie anzeigt.
 *
 * <p>{@link #status} und {@link #lastSyncError} sind kein Beiwerk: eine abgelaufene Zustimmung
 * laesst den Sync ansonsten stumm versiegen, und der Nutzer haelt veraltete Zahlen fuer aktuelle.
 */
@Data
public class BankConnectionDTO {

    private Long id;
    private String aspspName;
    private String aspspCountry;
    private String status;
    private LocalDateTime validUntil;
    private LocalDateTime lastSyncAt;
    private String lastSyncError;

    /** Tage bis zum Ablauf der Zustimmung; negativ bedeutet abgelaufen. Eine Verlaengerung gibt es nicht. */
    private Long daysUntilExpiry;

    private LocalDateTime createdAt;
}
