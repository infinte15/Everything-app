package com.Finn.everything_app.model;

/**
 * Woher eine {@link CategoryRule} stammt.
 *
 * <p>{@code SEEDED}-Regeln haben immer {@code user == null} und werden vom CategoryRuleSeeder
 * angelegt; {@code LEARNED} entsteht, wenn der Nutzer die Kategorie einer Bank-Buchung korrigiert;
 * {@code MANUAL} legt der Nutzer selbst an.
 */
public enum RuleSource {
    SEEDED,
    LEARNED,
    MANUAL
}
