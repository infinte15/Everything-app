package com.Finn.everything_app.service.bank;

import java.util.List;

/**
 * Ein Saldo eines Kontos.
 *
 * <p>Banken liefern mehrere Salden nebeneinander und jede unterstuetzt nur eine Teilmenge der
 * Typen. Welcher davon dem entspricht, was der Nutzer in seiner Banking-App sieht, steht in keiner
 * Dokumentation - deshalb eine Praeferenzkette statt einer festen Wahl.
 *
 * @param type Code nach ISO 20022, z.B. XPCD (erwartet, inkl. Vormerkungen), ITAV (verfuegbar im
 *             Tagesverlauf), CLAV (verfuegbar zum Tagesabschluss), CLBD (gebucht zum Tagesabschluss).
 */
public record BankBalance(String type, String name, double amount, String currency) {

    /**
     * Reihenfolge der Bevorzugung: was am ehesten dem angezeigten "verfuegbaren Betrag" entspricht,
     * steht vorn. CLBD als letzter Eintrag ist der reine Buchsaldo ohne Vormerkungen - korrekt,
     * aber meist niedriger als das, was die Bank dem Nutzer zeigt.
     */
    private static final List<String> PREFERENCE = List.of("XPCD", "ITAV", "CLAV", "CLBD", "ITBD");

    /** Kleiner ist besser; unbekannte Typen landen hinter allen bekannten. */
    public int preferenceRank() {
        int index = PREFERENCE.indexOf(type == null ? "" : type.toUpperCase());
        return index < 0 ? PREFERENCE.size() : index;
    }
}
