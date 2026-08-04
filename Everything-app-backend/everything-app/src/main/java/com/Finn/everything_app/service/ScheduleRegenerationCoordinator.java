package com.Finn.everything_app.service;

import com.Finn.everything_app.event.ScheduleChangedEvent;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Entprellt die automatische Neuplanung.
 *
 * {@link ScheduleChangedEvent} wird an 18 Stellen veröffentlicht (Task-, Habit-, Event-,
 * Workout- und Plan-Änderungen). Ohne Entprellung löst eine Folge von Änderungen — z.B. Plan
 * anlegen und direkt aktivieren — genauso viele vollständige CP-SAT-Läufe à mehreren Sekunden
 * aus. Hier wird pro User zusammengefasst: jede neue Meldung verschiebt den Lauf um die
 * Ruhephase nach hinten, aber nie länger als maxDelay ab der ersten Meldung, damit ein
 * Dauerstrom von Änderungen die Neuplanung nicht verhungern lässt.
 *
 * Bewusst eine eigene Komponente statt zusätzlicher Felder in {@link SmartSchedulerService}:
 * dessen Konstruktor-Argumentliste ist über @InjectMocks an die Tests gekoppelt.
 */
@Component
@Slf4j
public class ScheduleRegenerationCoordinator {

    private final SmartSchedulerService scheduler;
    private final UserService userService;
    private final long quietPeriodMs;
    private final long maxDelayMs;

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "schedule-regen");
        t.setDaemon(true);
        return t;
    });

    private final Map<Long, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final Map<Long, Long> firstRequestedAt = new ConcurrentHashMap<>();

    public ScheduleRegenerationCoordinator(
            SmartSchedulerService scheduler,
            UserService userService,
            @Value("${scheduler.debounce-ms:2000}")   long quietPeriodMs,
            @Value("${scheduler.max-delay-ms:15000}") long maxDelayMs) {
        this.scheduler     = scheduler;
        this.userService   = userService;
        this.quietPeriodMs = quietPeriodMs;
        this.maxDelayMs    = maxDelayMs;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduleChanged(ScheduleChangedEvent event) {
        request(event.getUserId());
    }

    /** Package-private, damit der Test ohne Spring-Kontext auslösen kann. */
    void request(Long userId) {
        long now = System.currentTimeMillis();
        long first = firstRequestedAt.computeIfAbsent(userId, k -> now);
        long delay = Math.min(quietPeriodMs, Math.max(0, first + maxDelayMs - now));

        pending.compute(userId, (k, previous) -> {
            if (previous != null) previous.cancel(false);
            return exec.schedule(() -> run(k), delay, TimeUnit.MILLISECONDS);
        });
    }

    private void run(Long userId) {
        pending.remove(userId);
        firstRequestedAt.remove(userId);
        try {
            if (Boolean.FALSE.equals(userService.getOrCreatePreferences(userId).getAutoScheduleEnabled())) {
                log.debug("Automatische Neuplanung für User {} ist deaktiviert", userId);
                return;
            }
            // Der Horizont kommt bewusst vom Scheduler und nicht aus einem eigenen Feld: sonst
            // stünde derselbe Wert an zwei Stellen und der manuelle Lauf über den Controller
            // könnte einen anderen Zeitraum abdecken als die automatische Neuplanung.
            LocalDate today = LocalDate.now();
            scheduler.generateOptimalSchedule(userId, today, scheduler.defaultHorizonEnd(today));
        } catch (Exception e) {
            // Ohne dieses catch verschwindet jede Exception im ScheduledExecutor spurlos.
            log.error("Automatische Neuplanung für User {} fehlgeschlagen", userId, e);
        }
    }

    @PreDestroy
    void shutdown() {
        exec.shutdownNow();
    }
}
