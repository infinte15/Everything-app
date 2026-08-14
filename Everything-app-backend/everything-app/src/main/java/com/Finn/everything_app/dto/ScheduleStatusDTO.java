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
    private List<AtRiskItemDTO> atRisk = new ArrayList<>();
}
