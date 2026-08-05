package com.Finn.everything_app.dto;

import lombok.Data;

/**
 * Die Kennzahlen eines Decks, serverseitig gezählt.
 *
 * Die Einteilung ist dieselbe wie in {@code lib/utils/anki_scheduler.dart}: neu (nie
 * bewertet), lernend (noch in den Minutenschritten) und gereift (alles übrige). {@code due}
 * zählt bewusst OHNE die neuen Karten — eine neue Karte ist nicht „überfällig", sie war nur
 * noch nie dran; die Lern-Schaltfläche addiert beides.
 */
@Data
public class DeckStatsDTO {
    private Long deckId;
    private Integer total;
    private Integer due;
    private Integer newCards;
    private Integer learning;
    private Integer mature;
}
