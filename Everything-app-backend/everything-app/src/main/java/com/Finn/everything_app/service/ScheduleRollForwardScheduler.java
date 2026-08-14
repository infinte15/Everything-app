package com.Finn.everything_app.service;

import com.Finn.everything_app.event.ScheduleChangedEvent;
import com.Finn.everything_app.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Schiebt das rollierende Planungsfenster weiter.
 *
 * <p>Seit {@code scheduler.horizon-days=28} plant der Scheduler nur noch ein kurzes Fenster fein
 * durch. Das ist der Grund, warum ein Lauf überhaupt schnell sein kann — aber es hält nur, solange
 * das Fenster mitwandert. Ohne diese Komponente bliebe es an dem Tag stehen, an dem der Nutzer
 * zuletzt etwas geändert hat: {@link ScheduleChangedEvent} kommt nur aus Änderungen, und wer drei
 * Tage nichts anfasst, hätte am Ende eine leerlaufende letzte Woche im Kalender.
 *
 * <p>Zwei Auslöser, weil einer allein nicht reicht:
 * <ul>
 *   <li>der nächtliche Cron für den Normalfall,</li>
 *   <li>ein Sweep alle Stunde für den häufigeren Fall, dass der Rechner um drei Uhr aus war.
 *       Er stützt sich auf {@code UserPreferences.lastScheduleRunDate} und ist damit unabhängig
 *       davon, wie lange die Anwendung stand.</li>
 * </ul>
 *
 * <p>Beide gehen über {@link ScheduleRegenerationCoordinator#request(Long)} statt direkt in den
 * Scheduler: dort sitzt die Entprellung, und nur so kann der Sweep nicht mit einer gerade
 * laufenden Neuplanung kollidieren. Dass hier trotzdem gefangen wird, hat denselben Grund wie in
 * {@link BankSyncScheduler}: eine Exception aus einem {@code @Scheduled}-Aufruf verschwindet sonst
 * spurlos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleRollForwardScheduler {

    private final UserPreferencesRepository preferencesRepository;
    private final ScheduleRegenerationCoordinator coordinator;

    @Value("${scheduler.roll-forward-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${scheduler.roll-forward-cron:0 15 3 * * *}")
    public void rollForwardNightly() {
        rollForward("nächtlich");
    }

    @Scheduled(fixedDelayString = "${scheduler.roll-forward-sweep-ms:3600000}",
               initialDelayString = "${scheduler.roll-forward-sweep-ms:3600000}")
    public void rollForwardSweep() {
        rollForward("Nachzügler");
    }

    private void rollForward(String anlass) {
        if (!enabled) return;
        try {
            List<Long> userIds = preferencesRepository.findUserIdsNeedingRollForward(LocalDate.now());
            if (userIds.isEmpty()) return;

            userIds.forEach(coordinator::request);
            log.info("Planungsfenster weitergeschoben ({}): {} Nutzer angestoßen", anlass, userIds.size());

        } catch (Exception e) {
            log.error("Weiterschieben des Planungsfensters ({}) fehlgeschlagen", anlass, e);
        }
    }
}
