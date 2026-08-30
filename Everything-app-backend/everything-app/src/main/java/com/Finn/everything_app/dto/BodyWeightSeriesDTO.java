package com.Finn.everything_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Der Verlauf plus die drei Zahlen, die der Startbildschirm daneben zeigt.
 *
 * <p>Die Kennzahlen kommen mit, statt sie im Client aus {@link #entries} zu rechnen: die Liste
 * ist auf einen Zeitraum beschnitten, {@link #latest} und {@link #previous} sollen aber immer
 * die tatsaechlich letzten Werte sein - auch wenn beide vor dem Fenster liegen.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BodyWeightSeriesDTO {

    private List<BodyWeightEntryDTO> entries = new ArrayList<>();

    /** Zuletzt gewogen, oder null wenn noch nie. */
    private BodyWeightEntryDTO latest;

    /** Der Wert davor - fuer die Veraenderung neben der grossen Zahl. */
    private BodyWeightEntryDTO previous;

    /** Zielgewicht aus den Nutzereinstellungen, oder null. */
    private Double targetWeightKg;
}
