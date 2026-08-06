package com.Finn.everything_app.service;

import com.Finn.everything_app.model.BankConnection;
import com.Finn.everything_app.model.BankConnectionStatus;
import com.Finn.everything_app.repository.BankConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Naechtlicher Abruf ueber alle aktiven Bankverbindungen.
 *
 * <p>Ohne PSU-Kontext: der Nutzer sitzt um sechs Uhr morgens nicht davor, also ist der Abruf per
 * Definition unbeaufsichtigt und faellt unter das Limit der Bank (typisch vier pro Tag und Konto).
 * Einmal taeglich bleibt komfortabel darunter und laesst dem vom Nutzer ausgeloesten Abruf Luft.
 *
 * <p>Jede Verbindung laeuft in ihrem eigenen {@code try/catch}: eine Bank, die gerade nicht mag,
 * darf die uebrigen nicht mitreissen. Dass der aeussere Rahmen ebenfalls faengt, hat denselben
 * Grund wie in {@link ScheduleRegenerationCoordinator}: eine Exception aus einem
 * {@code @Scheduled}-Aufruf verschwindet sonst spurlos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.bank-sync.enabled", havingValue = "true", matchIfMissing = true)
public class BankSyncScheduler {

    private final BankConnectionRepository connectionRepository;
    private final BankSyncService bankSyncService;

    @Scheduled(cron = "${app.bank-sync.cron:0 0 6 * * *}")
    public void syncAll() {
        try {
            List<BankConnection> connections = connectionRepository.findByStatus(BankConnectionStatus.ACTIVE);
            if (connections.isEmpty()) {
                return;
            }

            int imported = 0;
            for (BankConnection connection : connections) {
                try {
                    // psu = null: unbeaufsichtigt. Der Zustand landet in der Verbindung selbst,
                    // syncConnection faengt seine Fehler bereits ab.
                    imported += bankSyncService.syncConnection(connection.getId(), null, false).imported();
                } catch (Exception e) {
                    log.error("Nächtlicher Sync für Verbindung {} fehlgeschlagen", connection.getId(), e);
                }
            }
            log.info("Nächtlicher Bank-Sync: {} Verbindungen, {} neue Buchungen", connections.size(), imported);

        } catch (Exception e) {
            log.error("Nächtlicher Bank-Sync abgebrochen", e);
        }
    }
}
