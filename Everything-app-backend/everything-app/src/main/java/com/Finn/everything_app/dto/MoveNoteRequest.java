package com.Finn.everything_app.dto;

/**
 * Verschiebt eine Seite im Baum.
 *
 * {@code parentId == null} bedeutet Wurzelebene — deshalb ein Record mit ausdrücklich
 * nullbarem Feld und kein partielles Update: bei letzterem wäre „auf die Wurzel ziehen" nicht
 * von „Elternseite unverändert" zu unterscheiden.
 * {@code position} ist die Zielposition unter den Geschwistern; {@code null} heißt ans Ende.
 */
public record MoveNoteRequest(Long parentId, Integer position) {
}
