package com.Finn.everything_app.service;

import com.Finn.everything_app.model.MuscleGroup;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Ruhetag gilt dem Muskel, nicht dem Kalender.
 *
 * <p>Vorher galten fuer jedes Paar zwei Tage Abstand, egal was die beiden Einheiten trainierten.
 * Diese Tests halten fest, wovon der Abstand jetzt abhaengt.
 */
class RestDaysBetweenTest {

    private static final Set<MuscleGroup> PUSH =
            Set.of(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS);
    private static final Set<MuscleGroup> PULL =
            Set.of(MuscleGroup.LATS, MuscleGroup.MIDDLE_BACK, MuscleGroup.BICEPS);
    private static final Set<MuscleGroup> BEINE =
            Set.of(MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES);

    @Test
    void dieselbeEinheitZweimalBrauchtDenGroesstenAbstand() {
        assertEquals(3, SmartSchedulerService.restDaysBetween(PUSH, PUSH));
    }

    @Test
    void ohneGemeinsamenMuskelReichtEinTag() {
        assertEquals(1, SmartSchedulerService.restDaysBetween(PUSH, BEINE));
        assertEquals(1, SmartSchedulerService.restDaysBetween(PULL, BEINE));
    }

    @Test
    void pushUndPullLiegenDazwischen() {
        // Zwei Oberkoerpertage teilen sich die Schultern nicht, aber sie liegen naeher
        // beieinander als Druecken und Beine.
        Set<MuscleGroup> pullMitSchulter = Set.of(
                MuscleGroup.LATS, MuscleGroup.MIDDLE_BACK, MuscleGroup.BICEPS, MuscleGroup.SHOULDERS);
        int abstand = SmartSchedulerService.restDaysBetween(PUSH, pullMitSchulter);
        assertTrue(abstand >= 1 && abstand <= 2, "erwartet 1 bis 2, war " + abstand);
    }

    @Test
    void ohneBekannteMuskelnBleibtEsBeimBisherigenAbstand() {
        // Eine freie Einheit ohne Routine. "Keine Ueberschneidung" anzunehmen waere falsch:
        // das legte zwei Trainings auf aufeinanderfolgende Tage, nur weil eine Zuordnung fehlt.
        assertEquals(2, SmartSchedulerService.restDaysBetween(Set.of(), PUSH));
        assertEquals(2, SmartSchedulerService.restDaysBetween(PUSH, Set.of()));
        assertEquals(2, SmartSchedulerService.restDaysBetween(Set.of(), Set.of()));
    }

    @Test
    void derAbstandIstSymmetrisch() {
        assertEquals(
                SmartSchedulerService.restDaysBetween(PUSH, PULL),
                SmartSchedulerService.restDaysBetween(PULL, PUSH));
    }
}
