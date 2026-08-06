package com.Finn.everything_app.model;

/**
 * Welches Feld einer Buchung eine {@link CategoryRule} prueft.
 *
 * <p>Der mitgelieferte Regelsatz benutzt durchgaengig {@code BOTH}: deutsche Bankdaten legen den
 * Haendler mal in den Namen der Gegenpartei ("REWE SAGT DANKE 12345"), mal in den Verwendungszweck,
 * und Gehalt steht fast immer im Verwendungszweck. COUNTERPARTY allein verfehlt die Haelfte.
 */
public enum RuleMatchField {
    COUNTERPARTY,
    DESCRIPTION,
    BOTH
}
