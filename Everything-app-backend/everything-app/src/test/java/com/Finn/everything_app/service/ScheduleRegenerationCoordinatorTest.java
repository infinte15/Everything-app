package com.Finn.everything_app.service;

import com.Finn.everything_app.model.UserPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Reines Mockito, kein Spring-Kontext — der Koordinator wird direkt konstruiert, damit die
 * Zeitparameter im Test klein gehalten werden können.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleRegenerationCoordinatorTest {

    @Mock SmartSchedulerService scheduler;
    @Mock UserService userService;
    @Mock ApplicationEventPublisher eventPublisher;

    private ScheduleRegenerationCoordinator coordinator(long quietMs, long maxDelayMs) {
        lenient().when(userService.getOrCreatePreferences(anyLong())).thenReturn(enabledPrefs());
        lenient().when(scheduler.defaultHorizonEnd(any())).thenAnswer(i -> i.<LocalDate>getArgument(0).plusDays(28));
        lenient().when(scheduler.classHorizonEnd(any())).thenAnswer(i -> i.<LocalDate>getArgument(0).plusDays(120));
        return new ScheduleRegenerationCoordinator(scheduler, userService, eventPublisher,
                quietMs, maxDelayMs);
    }

    private UserPreferences enabledPrefs() {
        UserPreferences p = new UserPreferences();
        p.setAutoScheduleEnabled(true);
        return p;
    }

    @Test
    void aBurstOfChangesTriggersExactlyOneRegeneration() {
        ScheduleRegenerationCoordinator coord = coordinator(80L, 5000L);

        for (int i = 0; i < 5; i++) coord.request(1L);

        verify(scheduler, timeout(2000).times(1))
                .generateOptimalSchedule(eq(1L), any(LocalDate.class), any(LocalDate.class),
                        any(LocalDate.class));
        // ignoreStubs blendet den gestubbten Horizont-Aufruf aus; geprüft wird nur, dass keine
        // ZWEITE Neuplanung stattgefunden hat.
        verifyNoMoreInteractions(ignoreStubs(scheduler));
    }

    @Test
    void differentUsersAreDebouncedIndependently() {
        ScheduleRegenerationCoordinator coord = coordinator(80L, 5000L);

        coord.request(1L);
        coord.request(2L);

        verify(scheduler, timeout(2000)).generateOptimalSchedule(eq(1L), any(), any(), any());
        verify(scheduler, timeout(2000)).generateOptimalSchedule(eq(2L), any(), any(), any());
    }

    @Test
    void aContinuousStreamOfChangesStillGetsRegeneratedViaMaxDelay() throws Exception {
        // Ruhephase 200ms, aber spätestens nach 150ms muss gelaufen werden: ohne die
        // maxDelay-Klammer würde ein Dauerstrom von Änderungen die Neuplanung ewig verschieben.
        ScheduleRegenerationCoordinator coord = coordinator(200L, 150L);

        long until = System.currentTimeMillis() + 600;
        while (System.currentTimeMillis() < until) {
            coord.request(1L);
            Thread.sleep(20);
        }

        verify(scheduler, timeout(2000).atLeastOnce())
                .generateOptimalSchedule(eq(1L), any(), any(), any());
    }

    @Test
    void regenerationIsSkippedWhenAutoSchedulingIsDisabled() {
        UserPreferences off = new UserPreferences();
        off.setAutoScheduleEnabled(false);
        when(userService.getOrCreatePreferences(1L)).thenReturn(off);
        // Auf diesem Pfad wird nur der Stundenplan gespiegelt, also zählt allein sein Fenster.
        when(scheduler.classHorizonEnd(any())).thenAnswer(i -> i.<LocalDate>getArgument(0).plusDays(120));
        ScheduleRegenerationCoordinator coord =
                new ScheduleRegenerationCoordinator(scheduler, userService, eventPublisher,
                        50L, 5000L);

        coord.request(1L);

        verify(userService, timeout(2000)).getOrCreatePreferences(1L);
        verify(scheduler, never()).generateOptimalSchedule(any(), any(), any(), any());

        // Der Stundenplan wird nicht geplant, sondern abgebildet: er muss auch bei
        // abgeschalteter Autoplanung stimmen, sonst verschwände eine gelöschte Vorlesung
        // nie aus dem Kalender.
        verify(scheduler, timeout(2000)).syncClassEvents(eq(1L), any(), any());
    }

    /**
     * Auch ohne Lauf muss ein wartender Client geweckt werden.
     *
     * Bei abgeschalteter Autoplanung entsteht kein Ergebnis und damit kein
     * {@code ScheduleRunFinishedEvent}. Ohne das Ersatzsignal liefe der Long-Poll des Kalenders in
     * seinen vollen Zeitablauf — die App zeigte 25 Sekunden lang "wird neu geplant" für etwas, das
     * gar nicht stattfindet.
     */
    @Test
    void autoSchedulingAusMeldetTrotzdemFertig() {
        UserPreferences off = new UserPreferences();
        off.setAutoScheduleEnabled(false);
        when(userService.getOrCreatePreferences(1L)).thenReturn(off);
        when(scheduler.classHorizonEnd(any())).thenAnswer(i -> i.<LocalDate>getArgument(0).plusDays(120));
        ScheduleRegenerationCoordinator coord =
                new ScheduleRegenerationCoordinator(scheduler, userService, eventPublisher,
                        50L, 5000L);

        coord.request(1L);

        ArgumentCaptor<Object> gemeldet = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, timeout(2000)).publishEvent(gemeldet.capture());
        assertThat(gemeldet.getValue())
                .isEqualTo(new ScheduleRunNotifier.ScheduleSkippedEvent(1L));
    }

    /**
     * Ein langer Lauf für einen Nutzer darf einen anderen nicht aufhalten.
     *
     * Vorher lagen Entprell-Timer und Rechenlauf auf demselben Single-Thread-Executor: solange
     * für Nutzer 1 gerechnet wurde, konnte der Timer von Nutzer 2 nicht einmal feuern. Mit dem
     * nächtlichen Rundlauf über ALLE Nutzer wäre daraus eine Warteschlange geworden, in der der
     * letzte minutenlang hinten steht.
     */
    @Test
    void einLangerLaufBlockiertAndereNutzerNicht() throws Exception {
        ScheduleRegenerationCoordinator coord = coordinator(50L, 5000L);
        java.util.concurrent.CountDownLatch haelt = new java.util.concurrent.CountDownLatch(1);
        doAnswer(inv -> {
            haelt.await();
            return null;
        }).when(scheduler).generateOptimalSchedule(eq(1L), any(), any(), any());

        coord.request(1L);
        // Kurz warten, damit der Lauf für Nutzer 1 wirklich angefangen hat.
        Thread.sleep(200);
        coord.request(2L);

        try {
            verify(scheduler, timeout(2000)).generateOptimalSchedule(eq(2L), any(), any(), any());
        } finally {
            haelt.countDown();
        }
    }

    /**
     * Die Ruhephase im Code und die in {@code application.properties} müssen denselben Wert haben.
     *
     * Das ist kein Selbstzweck: die Herleitung der 400ms steht im Javadoc dieser Klasse, der
     * gemessene Gewinn von 1.6s pro Interaktion hing daran — und trotzdem wurde beim Umbau nur der
     * {@code @Value}-Vorgabewert und die TEST-Properties angepasst, während
     * {@code src/main/resources/application.properties} bei 2000 stehen blieb. Die Property
     * gewinnt, also lief die App monatelang mit dem alten Wert weiter, ohne dass ein Test etwas
     * gemerkt hätte.
     *
     * <p>Geprüft wird gegen die im Konstruktor deklarierte Rückfallebene, nicht gegen eine hier
     * hartkodierte Zahl: sonst wären es wieder zwei Stellen, die auseinanderlaufen können.
     *
     * <p><b>Warum die Datei fehlen darf.</b> {@code src/main/resources/application.properties}
     * steht in {@code .gitignore} — sie enthält DB-Passwort und JWT-Schlüssel. Genau daher rührt
     * die Regression: der Commit, der 400ms eingeführt hat, KONNTE die Datei nicht mitändern, weil
     * sie nicht in der Versionsverwaltung liegt. Auf einem frischen Klon existiert sie nicht, und
     * dann hat dieser Test nichts zu prüfen — er darf dort nicht rot werden, sondern muss sich
     * enthalten.
     */
    @Test
    void ruhephaseImCodeUndInDenPropertiesStimmenUeberein() throws Exception {
        Path datei = Path.of("src/main/resources/application.properties");
        assumeTrue(Files.exists(datei),
                "application.properties ist nicht versioniert und auf diesem Rechner nicht vorhanden");

        Properties produktiv = new Properties();
        try (var in = Files.newInputStream(datei)) {
            produktiv.load(in);
        }

        for (String schluessel : List.of("scheduler.debounce-ms", "scheduler.max-delay-ms")) {
            String ausProperties = produktiv.getProperty(schluessel);
            assertThat(ausProperties)
                    .as("%s fehlt in application.properties", schluessel)
                    .isNotNull();
            assertThat(Long.parseLong(ausProperties.trim()))
                    .as("%s: application.properties und der @Value-Vorgabewert im Konstruktor "
                            + "von ScheduleRegenerationCoordinator laufen auseinander", schluessel)
                    .isEqualTo(vorgabewertAusKonstruktor(schluessel));
        }
    }

    /** Liest die Rückfallebene aus einem {@code @Value("${schluessel:fallback}")} am Konstruktor. */
    private long vorgabewertAusKonstruktor(String schluessel) {
        for (Parameter p : ScheduleRegenerationCoordinator.class
                .getDeclaredConstructors()[0].getParameters()) {
            Value v = p.getAnnotation(Value.class);
            if (v == null || !v.value().startsWith("${" + schluessel + ":")) continue;
            String ausdruck = v.value();
            return Long.parseLong(ausdruck.substring(ausdruck.indexOf(':') + 1,
                    ausdruck.length() - 1).trim());
        }
        throw new AssertionError("Kein @Value für " + schluessel + " am Konstruktor gefunden");
    }

    @Test
    void aFailingRegenerationDoesNotKillTheExecutor() {
        ScheduleRegenerationCoordinator coord = coordinator(50L, 5000L);
        doThrow(new RuntimeException("Solver kaputt"))
                .when(scheduler).generateOptimalSchedule(eq(1L), any(), any(), any());

        coord.request(1L);
        verify(scheduler, timeout(2000)).generateOptimalSchedule(eq(1L), any(), any(), any());

        // Der Executor muss danach weiterhin Aufträge annehmen.
        reset(scheduler);
        coord.request(2L);
        verify(scheduler, timeout(2000)).generateOptimalSchedule(eq(2L), any(), any(), any());
    }
}
