package com.Finn.everything_app.service.bank;

/**
 * Ergebnis von {@link BankDataProvider#startAuth}.
 *
 * @param authUrl         Adresse, auf die der Browser des Nutzers geschickt wird.
 * @param authorizationId Kennung des Autorisierungsvorgangs. Nicht mit der Sitzungs-ID verwechseln -
 *                        die entsteht erst beim Einloesen des Codes.
 */
public record AuthStart(String authUrl, String authorizationId) {
}
