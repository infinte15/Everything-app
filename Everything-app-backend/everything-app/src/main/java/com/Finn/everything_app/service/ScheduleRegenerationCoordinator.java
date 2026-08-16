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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
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
 * <p><b>Zur Länge der Ruhephase.</b> Sie stand lange auf 2000ms und war damit der größte einzelne
 * Posten der spürbaren Wartezeit: der Solver braucht für einen Wiederholungslauf rund eine
 * Sekunde, davor lagen aber zwei Sekunden, in denen schlicht nichts geschah. Die Ruhephase soll
 * einen SCHWALL zusammenfassen — und ein Schwall aus einer einzelnen Nutzeraktion ist nach
 * Millisekunden vorbei, nicht nach zwei Sekunden. 400ms fassen ihn weiterhin vollständig
 * zusammen und nehmen 1.6s aus jeder einzelnen Interaktion heraus.
 *
 * <p>Bewusst KEINE Entprellung auf der Vorderflanke ("die erste Änderung startet sofort"): sie
 * bräche die Zusage "ein Schwall = genau ein Lauf" (siehe
 * {@code aBurstOfChangesTriggersExactlyOneRegeneration}), weil auf den Sofortlauf immer noch ein
 * nachlaufender folgen müsste. Für die verbleibenden 400ms ist ein zweiter vollständiger
 * CP-SAT-Lauf der schlechtere Tausch.
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

    /**
     * Getrennte Rollen: der Timer plant nur, gerechnet wird woanders.
     *
     * Vorher lagen beide auf demselben Single-Thread-Executor. Solange für einen Nutzer gerechnet
     * wurde, konnte der Entprell-Timer eines anderen nicht einmal feuern — und seit es den
     * nächtlichen Rundlauf über ALLE Nutzer gibt (ScheduleRollForwardScheduler), hätte sich das
     * zu einer Warteschlange aufgereiht, in der der letzte Nutzer minutenlang hinten steht.
     *
     * Zwei Rechen-Threads, nicht mehr: der Löser bekommt selbst schon acht Suchthreads, und die
     * Maschine hat zwölf Kerne. Der zweite Platz ist dafür da, dass ein einzelner langer Lauf den
     * nächtlichen Rundlauf nicht komplett aufhält.
     */
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "schedule-regen-timer");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService workers = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "schedule-regen-worker");
        t.setDaemon(true);
        return t;
    });

    private final Map<Long, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final Map<Long, Long> firstRequestedAt = new ConcurrentHashMap<>();
    /** Nutzer, für die gerade gerechnet wird — verhindert zwei gleichzeitige Läufe pro Nutzer. */
    private final Set<Long> laufend = ConcurrentHashMap.newKeySet();

    public ScheduleRegenerationCoordinator(
            SmartSchedulerService scheduler,
            UserService userService,
            @Value("${scheduler.debounce-ms:400}")    long quietPeriodMs,
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
            // Der Timer gibt nur ab. Würde er selbst rechnen, stünde jeder andere Nutzer so lange
            // still, wie dieser Lauf dauert.
            return timer.schedule(() -> workers.submit(() -> run(k)), delay, TimeUnit.MILLISECONDS);
        });
    }

    private void run(Long userId) {
        pending.remove(userId);
        firstRequestedAt.remove(userId);

        // Läuft für diesen Nutzer schon einer, wird nicht danebengerechnet, sondern neu
        // angemeldet: die Entprellung behält damit ihre Bedeutung, und zwei Läufe können sich
        // nicht gegenseitig den Kalender überschreiben.
        if (!laufend.add(userId)) {
            request(userId);
            return;
        }

        try {
            // Der Horizont kommt bewusst vom Scheduler und nicht aus einem eigenen Feld: sonst
            // stünde derselbe Wert an zwei Stellen und der manuelle Lauf über den Controller
            // könnte einen anderen Zeitraum abdecken als die automatische Neuplanung.
            LocalDate today = LocalDate.now();

            if (Boolean.FALSE.equals(userService.getOrCreatePreferences(userId).getAutoScheduleEnabled())) {
                // Der Stundenplan wird nicht geplant, sondern abgebildet — er muss auch dann
                // stimmen, wenn der Nutzer die automatische Neuplanung abgeschaltet hat. Ohne
                // diesen Aufruf verschwände eine gelöschte Vorlesung nie aus dem Kalender.
                log.debug("Automatische Neuplanung für User {} ist deaktiviert, nur Vorlesungen", userId);
                scheduler.syncClassEvents(userId, today, scheduler.classHorizonEnd(today));
                return;
            }
            // Zwei Zeiträume: geplant wird das kurze, rollierende Fenster, der Stundenplan wird
            // deutlich weiter in den Kalender gespiegelt (er kostet den Löser nichts).
            scheduler.generateOptimalSchedule(userId, today, scheduler.defaultHorizonEnd(today),
                    scheduler.classHorizonEnd(today));
        } catch (Exception e) {
            // Ohne dieses catch verschwindet jede Exception im Executor spurlos.
            log.error("Automatische Neuplanung für User {} fehlgeschlagen", userId, e);
        } finally {
            // Muss auch nach einem Fehlschlag fallen, sonst wäre der Nutzer für den Rest der
            // Laufzeit von jeder weiteren Neuplanung ausgesperrt.
            laufend.remove(userId);
        }
    }

    @PreDestroy
    void shutdown() {
        timer.shutdownNow();
        workers.shutdownNow();
    }
}
