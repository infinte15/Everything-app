package com.Finn.everything_app.dto;

import java.time.LocalDate;

/** Ein Wochenpunkt der Volumen-Kurve. */
public record VolumePointDTO(
        LocalDate weekStart,
        double volumeKg,
        int workouts,
        int minutes
) {
}
