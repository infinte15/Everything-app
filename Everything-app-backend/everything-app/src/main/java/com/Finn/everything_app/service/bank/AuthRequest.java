package com.Finn.everything_app.service.bank;

import java.time.LocalDate;

/**
 * Anfrage zum Start der Zustimmung.
 *
 * @param state       verknuepft den spaeteren Browser-Callback mit dem Nutzer; wird unveraendert
 *                    zurueckgereicht. Entspricht {@code BankConnection.authState}.
 * @param validUntil  Ende der Zustimmung. Darf {@code AspspInfo.maxConsentDays} nicht ueberschreiten.
 */
public record AuthRequest(
        String aspspName,
        String aspspCountry,
        String state,
        String redirectUrl,
        LocalDate validUntil) {
}
