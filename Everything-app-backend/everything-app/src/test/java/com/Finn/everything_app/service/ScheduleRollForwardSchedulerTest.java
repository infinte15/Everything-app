package com.Finn.everything_app.service;

import com.Finn.everything_app.repository.UserPreferencesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests für das Weiterschieben des rollierenden Planungsfensters.
 *
 * Der eigentliche Inhalt der Komponente ist eine Auswahl — WELCHE Nutzer angestoßen werden —,
 * deshalb steht hier die Abfrage im Mittelpunkt und nicht das Ergebnis einer Planung.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleRollForwardSchedulerTest {

    @Mock UserPreferencesRepository preferencesRepository;
    @Mock ScheduleRegenerationCoordinator coordinator;

    @InjectMocks ScheduleRollForwardScheduler scheduler;

    private void enable() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
    }

    @Test
    void nutzerMitVeraltetemLaufWirdAngestossen() {
        enable();
        when(preferencesRepository.findUserIdsNeedingRollForward(any())).thenReturn(List.of(7L, 9L));

        scheduler.rollForwardNightly();

        verify(coordinator).request(7L);
        verify(coordinator).request(9L);
    }

    @Test
    void ohneNachzueglerPassiertNichts() {
        enable();
        when(preferencesRepository.findUserIdsNeedingRollForward(any())).thenReturn(List.of());

        scheduler.rollForwardSweep();

        verifyNoInteractions(coordinator);
    }

    /** Die Abfrage bekommt das HEUTIGE Datum — sonst rollt das Fenster nie oder ständig. */
    @Test
    void abfrageLaeuftGegenDenHeutigenTag() {
        enable();
        when(preferencesRepository.findUserIdsNeedingRollForward(any())).thenReturn(List.of());

        scheduler.rollForwardSweep();

        verify(preferencesRepository).findUserIdsNeedingRollForward(LocalDate.now());
    }

    /**
     * Ein Fehler in der Abfrage darf den Sweep nicht nach oben verlassen: aus einem
     * {@code @Scheduled}-Aufruf heraus verschwände die Exception spurlos, und der nächste Lauf
     * käme trotzdem — nur wüsste niemand, warum das Fenster stehen bleibt.
     */
    @Test
    void fehlerWirdGefangenUndNichtWeitergereicht() {
        enable();
        when(preferencesRepository.findUserIdsNeedingRollForward(any()))
                .thenThrow(new RuntimeException("DB weg"));

        scheduler.rollForwardSweep();

        verifyNoInteractions(coordinator);
    }

    @Test
    void abgeschaltetRuehrtNichtsAn() {
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.rollForwardNightly();
        scheduler.rollForwardSweep();

        verifyNoInteractions(preferencesRepository, coordinator);
    }
}
