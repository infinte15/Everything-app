package com.Finn.everything_app.service;

import com.Finn.everything_app.event.ScheduleRunFinishedEvent;
import lombok.Data;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
 *
 * <p><b>Zugleich die Quelle des Fertig-Signals.</b> {@link #record} ist der eine Punkt im ganzen
 * System, an dem "ein Lauf ist durch" entsteht; deshalb hängt das
 * {@link ScheduleRunFinishedEvent} hier und nicht im {@link SmartSchedulerService}. Dessen
 * Konstruktor-Argumentliste ist über {@code @InjectMocks} an {@code SmartSchedulerServiceTest} und
 * {@code SmartSchedulerSzenarienTest} gekoppelt — diese Klasse ist dort ein {@code @Mock}, ein
 * zusätzliches Feld hier ist für beide Tests unsichtbar.
 */
@Component
public class LastScheduleRunStore {

    @Data
    public static class Entry {
        private final LocalDateTime finishedAt;
        private final String solverStatus;
        private final List<AtRiskItem> atRisk;
        private final int scheduledBlocks;
        /** Angelegte + geänderte + gelöschte Blöcke in diesem Lauf; 0 heißt "nichts zu holen". */
        private final int changedBlocks;
    }

    private final Map<Long, Entry> perUser = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    public LastScheduleRunStore(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void record(Long userId, String solverStatus, List<AtRiskItem> atRisk,
                       int scheduledBlocks, int changedBlocks) {
        // Auf Millisekunden kürzen. LocalDateTime.now() hat unter Java 17 Nanosekundenauflösung,
        // Jackson schreibt sie mit, und Darts DateTime.tryParse schneidet auf MIKROsekunden ab.
        // Der Client schickt den Zeitstempel als "since" zurück; käme er dabei durch eine
        // Parse-Format-Runde, wäre er minimal kleiner als der gespeicherte Wert und
        // "finishedAt.isAfter(since)" sofort wahr — die Long-Poll-Anfrage käme augenblicklich leer
        // zurück und der Client forderte in einer Schleife neu an. Der Client echot zusätzlich den
        // Rohstring; beide Absicherungen kosten je eine Zeile und sind unabhängig voneinander.
        perUser.put(userId, new Entry(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
                solverStatus, List.copyOf(atRisk), scheduledBlocks, changedBlocks));

        // Erst eintragen, dann melden: wer geweckt wird, liest sofort den Store aus.
        eventPublisher.publishEvent(new ScheduleRunFinishedEvent(this, userId));
    }

    /** Null, solange in dieser Laufzeit noch nicht für den Nutzer geplant wurde. */
    public Entry get(Long userId) {
        return perUser.get(userId);
    }

    public void clear(Long userId) {
        perUser.remove(userId);
    }
}
