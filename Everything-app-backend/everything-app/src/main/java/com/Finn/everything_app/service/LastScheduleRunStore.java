package com.Finn.everything_app.service;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Was beim letzten Scheduler-Lauf herauskam — je Nutzer, im Speicher.
 *
 * <p>Der Grund: {@code ScheduleResult.atRisk} war bisher flüchtig. Die Liste entstand bei JEDEM
 * Lauf, überlebte aber nur den einen, der über {@code POST /generate-schedule} angestoßen wurde —
 * und den ruft die App genau einmal pro Sitzung. Jede entprellte Neuplanung im Hintergrund rechnete
 * also aus, dass eine Aufgabe ihren Termin nicht mehr schafft, und warf das Ergebnis weg. Der
 * Nutzer erfuhr davon nie; die Aufgabe blieb einfach liegen.
 *
 * <p>Bewusst im Speicher und nicht in der Datenbank: es ist der Zustand des letzten Laufs, kein
 * Bestand. Nach einem Neustart ist er leer, und der nächste Lauf füllt ihn wieder — für eine
 * Einzelinstanz ist das die richtige Abwägung gegen eine weitere Tabelle. Sollte die Anwendung
 * jemals mehrfach laufen, muss das hier ersetzt werden, nicht ergänzt.
 */
@Component
public class LastScheduleRunStore {

    @Data
    public static class Entry {
        private final LocalDateTime finishedAt;
        private final String solverStatus;
        private final List<AtRiskItem> atRisk;
        private final int scheduledBlocks;
    }

    private final Map<Long, Entry> perUser = new ConcurrentHashMap<>();

    public void record(Long userId, String solverStatus, List<AtRiskItem> atRisk, int scheduledBlocks) {
        perUser.put(userId, new Entry(LocalDateTime.now(), solverStatus,
                List.copyOf(atRisk), scheduledBlocks));
    }

    /** Null, solange in dieser Laufzeit noch nicht für den Nutzer geplant wurde. */
    public Entry get(Long userId) {
        return perUser.get(userId);
    }

    public void clear(Long userId) {
        perUser.remove(userId);
    }
}
