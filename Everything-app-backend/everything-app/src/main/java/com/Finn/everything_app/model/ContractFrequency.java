package com.Finn.everything_app.model;

/**
 * Zahlungsrhythmus eines erkannten oder manuell angelegten Vertrags.
 *
 * <p>Deutlich mehr Werte als die Vertragserkennung anfangs erzeugt - und das mit Absicht. Deutsche
 * Zahlungsrealitaet kennt halbjaehrliche Versicherungspraemien und zweimonatliche Abschlaege, und
 * jeder spaeter ergaenzte Enum-Wert kostet eine Handmigration der CHECK-Bedingung (siehe
 * {@link BankConnectionStatus}).
 *
 * <p>{@code IRREGULAR} ist der ehrliche Eimer fuer "wiederkehrend, aber der Abstand wandert" -
 * besser als eine Buchungsreihe faelschlich als MONTHLY zu etikettieren und die Prognose damit
 * auf ein Datum festzunageln, das nie stimmt.
 */
public enum ContractFrequency {
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    BIMONTHLY,
    QUARTERLY,
    SEMIANNUAL,
    YEARLY,
    IRREGULAR
}
