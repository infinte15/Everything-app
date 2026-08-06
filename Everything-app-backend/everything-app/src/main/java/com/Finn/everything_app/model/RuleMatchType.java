package com.Finn.everything_app.model;

/**
 * Wie das Muster einer {@link CategoryRule} ausgewertet wird.
 *
 * <p>{@code CONTAINS} vergleicht kleingeschrieben als Teilzeichenkette. {@code REGEX} wird in
 * Phase 1 nirgends validiert - der Kategorisierer muss jedes Muster einzeln kompilieren und die
 * Regel bei {@code PatternSyntaxException} deaktivieren, sonst reisst eine kaputte Nutzerregel
 * die Kategorisierung des ganzen Imports mit.
 */
public enum RuleMatchType {
    CONTAINS,
    REGEX
}
