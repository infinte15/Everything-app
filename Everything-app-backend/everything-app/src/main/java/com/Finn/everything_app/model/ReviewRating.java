package com.Finn.everything_app.model;

/**
 * Bewertung einer Karteikarte im Review.
 *
 * Spiegelt ReviewRating aus lib/utils/anki_scheduler.dart. Bewusst ein Enum und kein String:
 * vorher nahm der Controller einen freien Query-Parameter entgegen und der Service bildete
 * alles Unbekannte still auf "MEDIUM" ab. Das Frontend sendet seit jeher GOOD — ein Wert, den
 * die alte Abbildung nicht kannte, weshalb "Gut" als falsch beantwortet galt und die Karte
 * zurücksetzte. Mit dem Enum lehnt die Bean-Validation Unbekanntes mit 400 ab.
 */
public enum ReviewRating {
    AGAIN,
    HARD,
    GOOD,
    EASY
}
