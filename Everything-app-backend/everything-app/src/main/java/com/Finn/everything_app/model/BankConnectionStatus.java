package com.Finn.everything_app.model;

/**
 * Lebenszyklus einer Bankverbindung (Enable Banking).
 *
 * <p>{@code FAILED} ist bewusst dabei, obwohl es der Ablauf nicht zwingend braucht: ohne einen
 * terminalen Fehlerzustand ist eine abgebrochene Anmeldung nicht von "der Nutzer steht noch im
 * Browser vor der TAN-Abfrage" zu unterscheiden, und die Verbindung bliebe fuer immer PENDING.
 *
 * <p>Alle Werte, die je gebraucht werden koennten, stehen hier von Anfang an. Hibernate friert die
 * CHECK-Bedingung einer {@code @Enumerated(STRING)}-Spalte beim ERSTELLEN der Tabelle ein und
 * ddl-auto=update fasst sie nie wieder an - ein spaeter ergaenzter Wert kostet also eine
 * Handmigration (vgl. db/manual/2026-08-05-project-enum-constraints.sql), ein heute ungenutzter
 * Wert kostet nichts.
 */
public enum BankConnectionStatus {
    /** Auth-Link erzeugt, Nutzer war noch nicht (erfolgreich) bei der Bank. */
    PENDING,
    /** Session eingeloest, Sync laeuft. */
    ACTIVE,
    /** PSD2-Zustimmung abgelaufen (90-180 Tage je nach Bank) - Nutzer muss erneut zustimmen. */
    EXPIRED,
    /** Vom Nutzer in der App getrennt. */
    REVOKED,
    /** Anmeldung abgebrochen oder Token-Tausch fehlgeschlagen; Grund steht in lastSyncError. */
    FAILED
}
