package com.Finn.everything_app.repository.projection;

/** Aggregat je Trainingseinheit: Anzahl abgeschlossener Saetze und bewegtes Volumen. */
public interface SessionAggregateRow {
    Long getSessionId();

    Long getSetCount();

    Double getVolume();
}
