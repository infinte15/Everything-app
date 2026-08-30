package com.Finn.everything_app.dto;

import java.time.LocalDateTime;

/**
 * Erholungsstand einer Muskelgruppe.
 *
 * @param muscle        Slug, passend zu den Flaechen der Koerper-Grafik
 * @param label         deutscher Name
 * @param fatigue       0 = ausgeruht, 1 = so belastet wie nach der haertesten Einheit
 * @param readiness     {@code 1 - fatigue}, damit die UI nicht selbst rechnen muss
 * @param lastTrainedAt letzte Belastung, oder {@code null}
 * @param hoursToReady  geschaetzte Stunden bis erholt; 0 wenn es schon so weit ist
 */
public record MuscleRecoveryDTO(
        String muscle,
        String label,
        double fatigue,
        double readiness,
        LocalDateTime lastTrainedAt,
        int hoursToReady) {
}
