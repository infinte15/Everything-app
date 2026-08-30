package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.AtRiskItemDTO;
import com.Finn.everything_app.dto.ScheduleStatusDTO;
import com.Finn.everything_app.event.ScheduleRunFinishedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hält Clients hin, bis für sie neu geplant wurde ("Long-Poll").
 *
 * <p><b>Wozu.</b> Vorher fragte die App in einer Retry-Leiter nach (400/600/900/1400/2000 ms),
 * nachdem sie zuvor pauschal 500 ms gewartet hatte. Der Server war typisch nach 1,4 s fertig, die
 * App zeigte es bei 1,9–2,4 s — die Differenz war reine Rundung auf das Raster der Leiter. Hier
 * wird die Anfrage stattdessen geparkt und in dem Moment beantwortet, in dem das Ergebnis vorliegt.
 *
 * <p><b>Warum Long-Poll und nicht SSE.</b> Der Kanal wird nur nach einer Nutzeraktion für rund
 * anderthalb Sekunden gebraucht, nicht dauerhaft. Ein stehender Stream bräuchte Reconnect-Logik,
 * Heartbeats, Lifecycle-Behandlung und eine neue Netz-Primitive im Frontend ({@code ApiService}
 * kennt nur {@code http.Response}) — alles Aufwand für eine Verbindung, die ohnehin nach anderthalb
 * Sekunden ihren Zweck erfüllt hat. Ein {@link DeferredResult} belegt beim Warten weder einen
 * Tomcat-Thread noch eine Datenbankverbindung.
 *
 * <p><b>Warum AFTER_COMMIT und nicht direkt in {@code record(...)}.</b> Der Store wird INNERHALB
 * der Transaktion des Laufs gefüllt. Würde man dort wecken, führe der Client seinen
 * {@code GET /events} gegen eine noch nicht committete Änderung und bekäme unter READ_COMMITTED den
 * ALTEN Plan zurück — der Umbau senkte die Wartezeit und zeigte dafür sporadisch das falsche
 * Ergebnis. {@code fallbackExecution = true} deckt Aufrufer ohne Transaktion ab (Tests, der
 * Zweig "Auto-Planung aus" im {@link ScheduleRegenerationCoordinator}).
 */
@Component
@Slf4j
public class ScheduleRunNotifier {

    private final LastScheduleRunStore store;
    private final long awaitTimeoutMs;

    /**
     * Wartende je Nutzer. Einträge werden in {@code onCompletion}/{@code onTimeout} wieder
     * entfernt — ohne das wächst die Menge mit jeder Anfrage und wird zum Speicherleck.
     */
    private final Map<Long, Set<DeferredResult<ResponseEntity<ScheduleStatusDTO>>>> wartende =
            new ConcurrentHashMap<>();

    public ScheduleRunNotifier(
            LastScheduleRunStore store,
            // Vorgabewert im Code, nicht in application.properties: die Datei ist gitignored, ein
            // frischer Klon hätte den Wert sonst nicht. Der Test konstruiert direkt mit 200 ms.
            @Value("${scheduler.await-timeout-ms:25000}") long awaitTimeoutMs) {
        this.store = store;
        this.awaitTimeoutMs = awaitTimeoutMs;
    }

    /**
     * Parkt die Anfrage, bis für {@code userId} ein Lauf fertig ist, der neuer ist als
     * {@code since}.
     *
     * @param since Zeitstempel des zuletzt gesehenen Laufs; {@code null} beim App-Start.
     * @return 200 mit dem Ergebnis, oder 204 wenn innerhalb des Zeitfensters nichts passiert ist.
     */
    public DeferredResult<ResponseEntity<ScheduleStatusDTO>> awaitRun(Long userId, LocalDateTime since) {
        DeferredResult<ResponseEntity<ScheduleStatusDTO>> ergebnis =
                new DeferredResult<>(awaitTimeoutMs, () -> ResponseEntity.noContent().build());

        // Zuerst nachsehen, dann warten. Ohne diese Prüfung entsteht ein Wettlauf, den man nie
        // gewinnt: der entprellte Lauf kann durch sein, bevor die Anfrage überhaupt ankommt — der
        // Client wartete dann die vollen 25 s auf ein Ereignis, das schon vorbei war.
        LastScheduleRunStore.Entry vorhanden = store.get(userId);
        if (istNeuer(vorhanden, since)) {
            ergebnis.setResult(ResponseEntity.ok(toDto(vorhanden)));
            return ergebnis;
        }

        Set<DeferredResult<ResponseEntity<ScheduleStatusDTO>>> menge =
                wartende.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        menge.add(ergebnis);

        // Beide Pfade abräumen: onCompletion deckt Erfolg und Client-Abbruch ab, onTimeout den
        // Ablauf. Eines allein genügt nicht.
        ergebnis.onCompletion(() -> entferne(userId, ergebnis));
        ergebnis.onTimeout(()    -> entferne(userId, ergebnis));

        return ergebnis;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRunFinished(ScheduleRunFinishedEvent event) {
        wecke(event.getUserId());
    }

    /**
     * Zweiter Eingang für Fälle ohne Lauf — siehe der Zweig "Auto-Planung aus" im
     * {@link ScheduleRegenerationCoordinator}. Dort gibt es kein Ergebnis im Store; geweckt werden
     * muss der Client trotzdem, sonst wartet er 25 s auf etwas, das nie kommt.
     */
    @EventListener
    public void onNothingToDo(ScheduleSkippedEvent event) {
        wecke(event.userId());
    }

    /** Es gab nichts zu planen. Kein {@link ScheduleRunFinishedEvent}: der Store bleibt unberührt. */
    public record ScheduleSkippedEvent(Long userId) {}

    private void wecke(Long userId) {
        // Die Menge wird ENTNOMMEN, nicht durchlaufen: wer beantwortet ist, ist fertig. Sich dafür
        // allein auf onCompletion zu verlassen wäre eine Wette auf den Servlet-Container — der
        // Rückruf kommt von dort, nicht von setResult. Ohne dieses Entnehmen bliebe jeder
        // beantwortete Eintrag hängen, bis der Container sich meldet, und in einem Test ohne
        // Container für immer. onCompletion/onTimeout bleiben trotzdem: sie decken die Fälle ab,
        // in denen die Anfrage stirbt, OHNE dass hier je eine Antwort entsteht.
        Set<DeferredResult<ResponseEntity<ScheduleStatusDTO>>> menge = wartende.remove(userId);
        if (menge == null || menge.isEmpty()) return;

        LastScheduleRunStore.Entry aktuell = store.get(userId);
        ResponseEntity<ScheduleStatusDTO> antwort = aktuell == null
                ? ResponseEntity.ok(new ScheduleStatusDTO())
                : ResponseEntity.ok(toDto(aktuell));

        for (DeferredResult<ResponseEntity<ScheduleStatusDTO>> wartender : menge) {
            // setResult gibt false zurück, wenn die Anfrage schon abgelaufen oder abgebrochen ist.
            // Das ist kein Fehler, sondern der Normalfall bei einem weggelegten Handy.
            wartender.setResult(antwort);
        }
    }

    private void entferne(Long userId, DeferredResult<ResponseEntity<ScheduleStatusDTO>> ergebnis) {
        wartende.computeIfPresent(userId, (k, menge) -> {
            menge.remove(ergebnis);
            // Leere Mengen mitentfernen, sonst bleibt pro Nutzer ein Eintrag für immer stehen.
            return menge.isEmpty() ? null : menge;
        });
    }

    private boolean istNeuer(LastScheduleRunStore.Entry entry, LocalDateTime since) {
        // Ohne "since" (App-Start) darf NICHT sofort geantwortet werden — sonst reißt der erste
        // Nachlauf nach dem Start durch und meldet einen Lauf, den der Nutzer nicht ausgelöst hat.
        if (entry == null || since == null) return false;
        return entry.getFinishedAt().isAfter(since);
    }

    private ScheduleStatusDTO toDto(LastScheduleRunStore.Entry entry) {
        ScheduleStatusDTO dto = new ScheduleStatusDTO();
        dto.setLastRunAt(entry.getFinishedAt());
        dto.setSolverStatus(entry.getSolverStatus());
        dto.setScheduledBlocks(entry.getScheduledBlocks());
        dto.setChangedBlocks(entry.getChangedBlocks());
        dto.setAtRisk(entry.getAtRisk().stream()
                .map(a -> new AtRiskItemDTO(a.getTaskId(), a.getHabitId(), a.getTitle(),
                        a.getMinutes(), a.getReason() != null ? a.getReason().name() : null,
                        a.getPlannedStart()))
                .collect(Collectors.toList()));
        return dto;
    }

    /** Damit {@link CalendarController} dieselbe Abbildung nutzt, statt sie ein zweites Mal zu bauen. */
    public ScheduleStatusDTO statusVon(Long userId) {
        LastScheduleRunStore.Entry entry = store.get(userId);
        return entry == null ? new ScheduleStatusDTO() : toDto(entry);
    }

    /** Nur für Tests: wie viele Anfragen gerade geparkt sind. */
    int anzahlWartender() {
        return wartende.values().stream().mapToInt(Set::size).sum();
    }
}
