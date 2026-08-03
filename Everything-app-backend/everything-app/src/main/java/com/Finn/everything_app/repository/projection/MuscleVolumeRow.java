package com.Finn.everything_app.repository.projection;

/** Rohes Aggregat je Muskelgruppe aus der Auswertungsabfrage. */
public interface MuscleVolumeRow {
    String getMuscle();

    Double getVolume();

    Double getWeightedSets();

    Long getSessionCount();
}
