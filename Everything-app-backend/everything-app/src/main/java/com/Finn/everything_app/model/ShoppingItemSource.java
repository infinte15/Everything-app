package com.Finn.everything_app.model;

/**
 * Woher ein Eintrag der Einkaufsliste stammt.
 *
 * <p>Die Unterscheidung traegt die ganze Aktualisierungslogik: beim Neuaufbau aus dem
 * Wochenplan werden nur {@link #MEAL_PLAN}-Zeilen angefasst, die noch nicht abgehakt sind.
 * Selbst hinzugefuegte Zeilen und alles, was man im Laden schon eingesammelt hat, ueberlebt.
 */
public enum ShoppingItemSource {

    /** Von Hand eingetragen. */
    MANUAL,

    /** Aus dem Wochenplan erzeugt. */
    MEAL_PLAN
}
