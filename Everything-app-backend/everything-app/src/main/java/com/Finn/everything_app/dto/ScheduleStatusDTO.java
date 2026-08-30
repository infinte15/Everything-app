package com.Finn.everything_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ergebnis des letzten Scheduler-Laufs — kleine Antwort, die das Frontend häufig abfragen kann.
 *
 * Der Kalender pollt nach jeder Änderung, bis die Neuplanung durch ist. Dafür einen ganzen Monat
 * an Terminen zu holen, ist Verschwendung: hier stehen ein paar hundert Byte, und erst wenn sich
 * {@code lastRunAt} ändert, lohnt sich der teure Abruf.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleStatusDTO {

    /** Null, solange in dieser Laufzeit noch nicht geplant wurde. */
    private LocalDateTime lastRunAt;
    private String solverStatus;
    private Integer scheduledBlocks;

    /**
     * Angelegte + geänderte + gelöschte Blöcke in diesem Lauf.
     *
     * Bei {@code 0} darf sich das Frontend den Monatsabruf sparen — der häufige Fall "Block gezogen
     * und gepinnt, der Löser hat sonst nichts bewegt". {@code null} heißt ausdrücklich
     * <b>unbekannt</b> und nicht "null Blöcke": der Client lädt dann wie bisher nach. Damit ist ein
     * Schreibweg, der versehentlich nicht mitzählt, ein Leistungsverlust und kein falscher Kalender.
     */
    private Integer changedBlocks;

    private List<AtRiskItemDTO> atRisk = new ArrayList<>();
}
