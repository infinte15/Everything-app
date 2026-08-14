package com.Finn.everything_app.service;

import com.Finn.everything_app.model.UserPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

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

    private ScheduleRegenerationCoordinator coordinator(long quietMs, long maxDelayMs) {
        lenient().when(userService.getOrCreatePreferences(anyLong())).thenReturn(enabledPrefs());
        lenient().when(scheduler.defaultHorizonEnd(any())).thenAnswer(i -> i.<LocalDate>getArgument(0).plusDays(28));
        lenient().when(scheduler.classHorizonEnd(any())).thenAnswer(i -> i.<LocalDate>getArgument(0).plusDays(120));
        return new ScheduleRegenerationCoordinator(scheduler, userService, quietMs, maxDelayMs);
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
                new ScheduleRegenerationCoordinator(scheduler, userService, 50L, 5000L);

        coord.request(1L);

        verify(userService, timeout(2000)).getOrCreatePreferences(1L);
        verify(scheduler, never()).generateOptimalSchedule(any(), any(), any(), any());

        // Der Stundenplan wird nicht geplant, sondern abgebildet: er muss auch bei
        // abgeschalteter Autoplanung stimmen, sonst verschwände eine gelöschte Vorlesung
        // nie aus dem Kalender.
        verify(scheduler, timeout(2000)).syncClassEvents(eq(1L), any(), any());
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
