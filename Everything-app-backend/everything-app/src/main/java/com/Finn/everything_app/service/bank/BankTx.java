package com.Finn.everything_app.service.bank;

import java.time.LocalDate;

/**
 * Eine Buchung, wie sie von der Bank kommt - noch ohne Kategorie und ohne Bezug zum eigenen Modell.
 *
 * @param entryReference Kennung der Bank. <strong>Darf {@code null} sein</strong>, kann bei manchen
 *                       Instituten doppelt vorkommen und ist nicht kontouebergreifend eindeutig.
 *                       Der Sync baut daraus deshalb keinen Schluessel, sondern ergaenzt das Konto
 *                       und faellt notfalls auf einen berechneten Hash zurueck.
 * @param booked         {@code true} bei gebuchten Umsaetzen. Vorgemerkte werden nicht importiert:
 *                       sie tragen meist keine {@code entryReference}, aendern sich noch und kaemen
 *                       beim Buchen ein zweites Mal an.
 * @param income         Richtung aus dem Indikator der Bank. Der Betrag selbst ist immer positiv.
 * @param amount         Betrag ohne Vorzeichen, auf zwei Nachkommastellen gerundet.
 */
public record BankTx(
        String entryReference,
        boolean booked,
        boolean income,
        double amount,
        String currency,
        LocalDate bookingDate,
        LocalDate valueDate,
        String counterparty,
        String description) {
}
