package com.Finn.everything_app.service.bank;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ergebnis des Code-Einloesens: eine Sitzung und die Konten, auf die sie Zugriff gibt.
 *
 * <p>Eine <em>leere</em> Kontoliste ist kein Fehlerfall der Schnittstelle: im eingeschraenkten
 * Produktionsbetrieb entfernt Enable Banking alle nicht im Control Panel verlinkten Konten
 * stillschweigend und antwortet trotzdem mit 200. Der Aufrufer muss diesen Fall eigens erklaeren,
 * sonst steht der Nutzer vor einem "keine Konten gefunden" ohne Handlungsanweisung.
 */
public record SessionResult(
        String sessionId,
        LocalDateTime validUntil,
        List<ProviderAccount> accounts) {

    /**
     * @param uid                sitzungsgebunden und nur zum Abrufen von Salden und Umsaetzen
     *                           brauchbar; {@code null} bei Konten, die die Bank nicht freigibt.
     * @param identificationHash stabil ueber Sitzungen hinweg - der Schluessel, unter dem das Konto
     *                           gespeichert wird.
     */
    public record ProviderAccount(
            String uid,
            String identificationHash,
            String iban,
            String displayName,
            String currency) {
    }
}
