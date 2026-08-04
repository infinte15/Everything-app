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
        lenient().when(scheduler.defaultHorizonEnd(any())).thenAnswer(i -> i.<LocalDate>getArgument(0).plusDays(84));
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
                .generateOptimalSchedule(eq(1L), any(LocalDate.class), any(LocalDate.class));
        // ignoreStubs blendet den gestubbten Horizont-Aufruf aus; geprüft wird nur, dass keine
        // ZWEITE Neuplanung stattgefunden hat.
        verifyNoMoreInteractions(ignoreStubs(scheduler));
    }

    @Test
    void differentUsersAreDebouncedIndependently() {
        ScheduleRegenerationCoordinator coord = coordinator(80L, 5000L);

        coord.request(1L);
        coord.request(2L);

        verify(scheduler, timeout(2000)).generateOptimalSchedule(eq(1L), any(), any());
        verify(scheduler, timeout(2000)).generateOptimalSchedule(eq(2L), any(), any());
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
                .generateOptimalSchedule(eq(1L), any(), any());
    }

    @Test
    void regenerationIsSkippedWhenAutoSchedulingIsDisabled() {
        UserPreferences off = new UserPreferences();
        off.setAutoScheduleEnabled(false);
        when(userService.getOrCreatePreferences(1L)).thenReturn(off);
        ScheduleRegenerationCoordinator coord =
                new ScheduleRegenerationCoordinator(scheduler, userService, 50L, 5000L);

        coord.request(1L);

        verify(userService, timeout(2000)).getOrCreatePreferences(1L);
        verify(scheduler, never()).generateOptimalSchedule(any(), any(), any());
    }

    @Test
    void aFailingRegenerationDoesNotKillTheExecutor() {
        ScheduleRegenerationCoordinator coord = coordinator(50L, 5000L);
        doThrow(new RuntimeException("Solver kaputt"))
                .when(scheduler).generateOptimalSchedule(eq(1L), any(), any());

        coord.request(1L);
        verify(scheduler, timeout(2000)).generateOptimalSchedule(eq(1L), any(), any());

        // Der Executor muss danach weiterhin Aufträge annehmen.
        reset(scheduler);
        coord.request(2L);
        verify(scheduler, timeout(2000)).generateOptimalSchedule(eq(2L), any(), any());
    }
}
