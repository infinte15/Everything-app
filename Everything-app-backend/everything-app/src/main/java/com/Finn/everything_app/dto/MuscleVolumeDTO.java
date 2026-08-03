package com.Finn.everything_app.dto;

/**
 * Belastung einer Muskelgruppe im gewaehlten Zeitraum.
 *
 * @param muscle       Slug aus {@code MuscleGroup}
 * @param label        deutsche Anzeige
 * @param volumeKg     bewegtes Volumen (Gewicht x Wiederholungen), Sekundaermuskeln zu 50%
 * @param weightedSets gewichtete Satzanzahl - traegt die Grafik auch bei reinem Koerpergewicht
 * @param sessionCount Anzahl Einheiten, in denen der Muskel beteiligt war
 * @param share        0..1 relativ zum staerkst belasteten Muskel; direkt die Einfaerbung
 */
public record MuscleVolumeDTO(
        String muscle,
        String label,
        double volumeKg,
        double weightedSets,
        long sessionCount,
        double share
) {
}
