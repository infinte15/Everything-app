package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.ScheduleStatusDTO;
import com.Finn.everything_app.event.ScheduleRunFinishedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reines JUnit ohne Spring-Kontext: der Notifier wird direkt konstruiert, damit das Zeitfenster
 * im Test winzig sein kann. Deshalb steht der Vorgabewert von {@code scheduler.await-timeout-ms}
 * auch im Code und nicht in {@code application.properties} — die Datei ist gitignored, und dieser
 * Test soll KEINEN weiteren Eintrag in der Schlüsselliste von
 * {@code ScheduleRegenerationCoordinatorTest} erzwingen.
 */
class ScheduleRunNotifierTest {

    /** Echter Store statt Mock: das Zusammenspiel Store→Ereignis→Antwort ist genau das Prüfobjekt. */
    private LastScheduleRunStore store(ApplicationEventPublisher publisher) {
        return new LastScheduleRunStore(publisher);
    }

    private ScheduleRunNotifier notifier(LastScheduleRunStore store, long timeoutMs) {
        return new ScheduleRunNotifier(store, timeoutMs);
    }

    @Test
    void einFertigerLaufWecktDenWartenden() {
        LastScheduleRunStore store = store(e -> { });
        ScheduleRunNotifier notifier = notifier(store, 5000L);

        DeferredResult<ResponseEntity<ScheduleStatusDTO>> wartender =
                notifier.awaitRun(1L, LocalDateTime.now());
        assertThat(wartender.hasResult()).isFalse();

        store.record(1L, "OPTIMAL", List.of(), 12, 3);
        notifier.onRunFinished(new ScheduleRunFinishedEvent(this, 1L));

        assertThat(wartender.hasResult()).isTrue();
        ScheduleStatusDTO dto = body(wartender);
        assertThat(dto.getSolverStatus()).isEqualTo("OPTIMAL");
        assertThat(dto.getScheduledBlocks()).isEqualTo(12);
        assertThat(dto.getChangedBlocks()).isEqualTo(3);
    }

    /**
     * Der Wettlauf, den man ohne die Vorabprüfung nie gewinnt.
     *
     * Der entprellte Lauf kann durch sein, BEVOR die Anfrage überhaupt ankommt. Ohne den Blick in
     * den Store würde der Client dann die vollen 25 Sekunden auf ein Ereignis warten, das längst
     * vorbei ist — und der ganze Umbau wäre in genau dem Fall langsamer als die alte Retry-Leiter.
     */
    @Test
    void einLaufVorDerAnfrageWirdSofortBeantwortet() {
        LastScheduleRunStore store = store(e -> { });
        ScheduleRunNotifier notifier = notifier(store, 5000L);

        LocalDateTime vorher = LocalDateTime.now().minusMinutes(1);
        store.record(1L, "FEASIBLE", List.of(), 4, 2);

        DeferredResult<ResponseEntity<ScheduleStatusDTO>> wartender = notifier.awaitRun(1L, vorher);

        assertThat(wartender.hasResult()).isTrue();
        assertThat(body(wartender).getSolverStatus()).isEqualTo("FEASIBLE");
        assertThat(notifier.anzahlWartender()).isZero();
    }

    /**
     * Ohne {@code since} (App-Start) darf NICHT sofort geantwortet werden — sonst meldet der erste
     * Nachlauf nach dem Start einen Lauf, den der Nutzer gar nicht ausgelöst hat.
     */
    @Test
    void ohneSinceWirdGewartetStattSofortZuAntworten() {
        LastScheduleRunStore store = store(e -> { });
        ScheduleRunNotifier notifier = notifier(store, 5000L);
        store.record(1L, "OPTIMAL", List.of(), 4, 1);

        DeferredResult<ResponseEntity<ScheduleStatusDTO>> wartender = notifier.awaitRun(1L, null);

        assertThat(wartender.hasResult()).isFalse();
        assertThat(notifier.anzahlWartender()).isEqualTo(1);
    }

    /**
     * Ohne Lauf bleibt die Anfrage geparkt. Dass daraus am Ende ein 204 wird, entscheidet der
     * Servlet-Container — geprüft wird das deshalb in
     * {@code CalendarControllerTest}, nicht hier.
     */
    @Test
    void ohneLaufBleibtDieAnfrageGeparkt() {
        LastScheduleRunStore store = store(e -> { });
        ScheduleRunNotifier notifier = notifier(store, 5000L);

        DeferredResult<ResponseEntity<ScheduleStatusDTO>> wartender =
                notifier.awaitRun(1L, LocalDateTime.now());

        assertThat(wartender.hasResult()).isFalse();
        assertThat(notifier.anzahlWartender()).isEqualTo(1);
    }

    /** Ohne dieses Aufräumen wächst die Warteliste mit jeder Anfrage — ein Leck auf Raten. */
    @Test
    void dieWartelisteIstNachDerAntwortLeer() {
        LastScheduleRunStore store = store(e -> { });
        ScheduleRunNotifier notifier = notifier(store, 5000L);

        notifier.awaitRun(1L, LocalDateTime.now());
        assertThat(notifier.anzahlWartender()).isEqualTo(1);

        store.record(1L, "OPTIMAL", List.of(), 1, 1);
        notifier.onRunFinished(new ScheduleRunFinishedEvent(this, 1L));

        assertThat(notifier.anzahlWartender()).isZero();
    }

    @Test
    void einLaufFuerEinenNutzerWecktKeinenAnderen() {
        LastScheduleRunStore store = store(e -> { });
        ScheduleRunNotifier notifier = notifier(store, 5000L);

        DeferredResult<ResponseEntity<ScheduleStatusDTO>> eins =
                notifier.awaitRun(1L, LocalDateTime.now());
        DeferredResult<ResponseEntity<ScheduleStatusDTO>> zwei =
                notifier.awaitRun(2L, LocalDateTime.now());

        store.record(1L, "OPTIMAL", List.of(), 1, 1);
        notifier.onRunFinished(new ScheduleRunFinishedEvent(this, 1L));

        assertThat(eins.hasResult()).isTrue();
        assertThat(zwei.hasResult()).isFalse();
    }

    /**
     * Bei abgeschalteter Autoplanung gibt es kein Ergebnis — geweckt werden muss trotzdem, sonst
     * hängt die Anzeige bis zum Zeitablauf.
     */
    @Test
    void dasUebersprungenSignalWecktEbenfalls() {
        LastScheduleRunStore store = store(e -> { });
        ScheduleRunNotifier notifier = notifier(store, 5000L);

        DeferredResult<ResponseEntity<ScheduleStatusDTO>> wartender =
                notifier.awaitRun(1L, LocalDateTime.now());

        notifier.onNothingToDo(new ScheduleRunNotifier.ScheduleSkippedEvent(1L));

        assertThat(wartender.hasResult()).isTrue();
        // Kein Lauf, also kein Zeitstempel: der Client sieht unverändertes lastRunAt und beendet
        // seinen Nachlauf, ohne den Monat neu zu laden.
        assertThat(body(wartender).getLastRunAt()).isNull();
    }

    /**
     * Der Store meldet selbst — sonst müsste jeder Aufrufer daran denken, und genau das wäre die
     * Stelle, an der es irgendwann vergessen wird.
     */
    @Test
    void derStoreVeroeffentlichtDasFertigSignal() {
        java.util.List<Object> gemeldet = new java.util.ArrayList<>();
        LastScheduleRunStore store = store(gemeldet::add);

        store.record(7L, "OPTIMAL", List.of(), 3, 1);

        assertThat(gemeldet).singleElement()
                .isInstanceOfSatisfying(ScheduleRunFinishedEvent.class,
                        e -> assertThat(e.getUserId()).isEqualTo(7L));
    }

    @SuppressWarnings("unchecked")
    private ScheduleStatusDTO body(DeferredResult<ResponseEntity<ScheduleStatusDTO>> wartender) {
        return ((ResponseEntity<ScheduleStatusDTO>) wartender.getResult()).getBody();
    }
}
