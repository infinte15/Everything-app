package com.Finn.everything_app.model;

/**
 * Wie sich die Vorgabe einer Uebung von Training zu Training weiterentwickelt.
 *
 * <p>Die Regel steht an der Routinen-Zeile, nicht an der Uebung: dieselbe Kniebeuge kann in
 * einem Kraftplan linear steigen und im Beiprogramm auf {@link #OFF} stehen.
 *
 * <p>Ausgewertet wird sie in {@code ProgressionService}, und zwar bei jeder Abfrage neu aus
 * dem Verlauf. Es gibt bewusst keinen gespeicherten Zaehler, der auseinanderlaufen koennte,
 * wenn eine Einheit nachtraeglich korrigiert oder geloescht wird.
 */
public enum ProgressionPolicy {

    /** Keine Automatik - die Vorgabe bleibt, was in der Routine steht. */
    OFF,

    /** Alle Saetze geschafft, dann beim naechsten Mal mehr Gewicht. */
    LINEAR,

    /**
     * Wie {@link #LINEAR}, aber der letzte Satz geht bis zum Anschlag: das Doppelte der
     * Sollwiederholungen gibt einen doppelten Sprung, ein Fehlschlag sofort einen Deload.
     */
    GREYSKULL,

    /**
     * Doppelte Progression: erst innerhalb der Wiederholungsspanne nach oben arbeiten,
     * erst am oberen Ende der Spanne das Gewicht erhoehen.
     */
    DOUBLE,

    /** Fuer Uebungen auf Zeit - gesteigert wird die Dauer, nicht die Last. */
    TIME;

    public static ProgressionPolicy orDefault(ProgressionPolicy value) {
        return value != null ? value : OFF;
    }
}
