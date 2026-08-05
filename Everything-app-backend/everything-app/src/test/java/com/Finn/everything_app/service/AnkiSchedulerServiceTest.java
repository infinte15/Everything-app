package com.Finn.everything_app.service;

import com.Finn.everything_app.model.ReviewRating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die Erwartungswerte hier sind die Ausgaben von
 * {@code Everything-app-frontend/everything_app/lib/utils/anki_scheduler.dart#schedule()} —
 * hartkodiert und von Hand nachgerechnet.
 *
 * Der Grund: die Intervallformel existiert bewusst zweimal, in Dart für die Beschriftung der
 * Bewertungs-Buttons und in Java als einzige Wahrheit. Driften beide auseinander, verspricht
 * die Schaltfläche "2 Tage" und der Server plant etwas anderes. Genau das faengt diese Tabelle.
 */
class AnkiSchedulerServiceTest {

    private final AnkiSchedulerService scheduler = new AnkiSchedulerService();

    private static AnkiSchedulerService.CardState state(
            int reps, double interval, int step, double ease) {
        return new AnkiSchedulerService.CardState(reps, interval, step, ease, 0);
    }

    /** reps, intervalDays, learningStep, ease, Bewertung -> neue reps, Intervall, Schritt, Ease, Abstand. */
    static Stream<Arguments> table() {
        return Stream.of(
                // --- Neue Karte (nie bewertet) ---
                Arguments.of("neu + AGAIN", state(0, 0, 0, 2.5), ReviewRating.AGAIN,
                        0, 0.0, 0, 2.3, Duration.ofMinutes(1)),
                Arguments.of("neu + HARD", state(0, 0, 0, 2.5), ReviewRating.HARD,
                        0, 0.0, 0, 2.35, Duration.ofMinutes(6)),
                // Der Bug-3-Fall: "Gut" auf einer neuen Karte gibt einen Tag und zaehlt als
                // bestanden. Vorher fiel GOOD in "default: return 2" und galt wegen q < 3 als
                // falsch beantwortet - die Karte wurde also zurueckgesetzt.
                Arguments.of("neu + GOOD", state(0, 0, 0, 2.5), ReviewRating.GOOD,
                        1, 1.0, 2, 2.5, Duration.ofDays(1)),
                Arguments.of("neu + EASY", state(0, 0, 0, 2.5), ReviewRating.EASY,
                        1, 4.0, 2, 2.65, Duration.ofDays(4)),

                // --- In der Lernphase (Schritt < 2) ---
                Arguments.of("lernend + HARD bleibt bei 6 Minuten", state(1, 1, 1, 2.5), ReviewRating.HARD,
                        1, 1.0, 1, 2.35, Duration.ofMinutes(6)),
                Arguments.of("lernend + GOOD", state(1, 1, 1, 2.5), ReviewRating.GOOD,
                        2, 2.5, 2, 2.5, Duration.ofDays(3)),   // 1 * 2.5 = 2.5 -> gerundet 3 Tage

                // --- Gereifte Karte ---
                Arguments.of("reif + GOOD multipliziert mit dem Ease", state(3, 10, 2, 2.5), ReviewRating.GOOD,
                        4, 25.0, 2, 2.5, Duration.ofDays(25)),
                Arguments.of("reif + HARD waechst nur um 20 Prozent", state(3, 10, 2, 2.5), ReviewRating.HARD,
                        3, 12.0, 2, 2.35, Duration.ofDays(12)),
                Arguments.of("reif + EASY", state(3, 10, 2, 2.5), ReviewRating.EASY,
                        4, 32.5, 2, 2.65, Duration.ofDays(33)),   // 10 * 2.5 * 1.3
                Arguments.of("reif + AGAIN faellt auf eine Minute zurueck", state(5, 30, 2, 2.5), ReviewRating.AGAIN,
                        0, 0.0, 0, 2.3, Duration.ofMinutes(1)),

                // --- Grenzen ---
                Arguments.of("Intervall wird bei einem Jahr gedeckelt", state(9, 300, 2, 2.5), ReviewRating.EASY,
                        10, 365.0, 2, 2.65, Duration.ofDays(365)),
                Arguments.of("Ease faellt nie unter 1.3", state(2, 6, 2, 1.3), ReviewRating.AGAIN,
                        0, 0.0, 0, 1.3, Duration.ofMinutes(1)),
                Arguments.of("Ease steigt nie ueber 5.0", state(2, 6, 2, 5.0), ReviewRating.EASY,
                        3, 39.0, 2, 5.0, Duration.ofDays(39))    // 6 * 5.0 * 1.3
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void matchesTheDartImplementation(
            String name,
            AnkiSchedulerService.CardState before,
            ReviewRating rating,
            int expectedReps,
            double expectedInterval,
            int expectedStep,
            double expectedEase,
            Duration expectedNext) {

        AnkiSchedulerService.ScheduledReview result = scheduler.schedule(before, rating);
        AnkiSchedulerService.CardState after = result.state();

        assertEquals(expectedReps, after.repetitions(), "Wiederholungen");
        assertEquals(expectedInterval, after.intervalDays(), 0.0001, "Intervall in Tagen");
        assertEquals(expectedStep, after.learningStep(), "Lernschritt");
        assertEquals(expectedEase, after.ease(), 0.0001, "Ease-Faktor");
        assertEquals(expectedNext, result.nextInterval(), "Abstand bis zur naechsten Faelligkeit");
    }

    @Test
    @DisplayName("AGAIN zaehlt ein Vergessen, die anderen drei nicht")
    void onlyAgainCountsALapse() {
        AnkiSchedulerService.CardState card = state(3, 10, 2, 2.5);

        assertEquals(1, scheduler.schedule(card, ReviewRating.AGAIN).state().lapses());
        assertEquals(0, scheduler.schedule(card, ReviewRating.HARD).state().lapses());
        assertEquals(0, scheduler.schedule(card, ReviewRating.GOOD).state().lapses());
        assertEquals(0, scheduler.schedule(card, ReviewRating.EASY).state().lapses());
    }

    // Kein Lauf durch die Kette: was die Karte gespeichert hat, ist der Ausgangspunkt. Der alte
    // SM-2 rechnete das Intervall aus der Wiederholungszahl nach und kam bei derselben Karte
    // auf einen voellig anderen Wert.
    @Test
    void theStoredIntervalIsTheStartingPointNotTheRepetitionCount() {
        AnkiSchedulerService.ScheduledReview result =
                scheduler.schedule(state(4, 100, 2, 2.0), ReviewRating.GOOD);

        assertEquals(200.0, result.state().intervalDays(), 0.0001,
                "100 gespeicherte Tage * Ease 2.0 - eine nachgerechnete Kette laege weit darunter");
    }

    // Das Frontend bewertet einmal, der Server ist die Wahrheit: dieselbe Eingabe muss zweimal
    // dasselbe liefern (der Planer haelt keinen Zustand).
    @Test
    void schedulingIsPure() {
        AnkiSchedulerService.CardState card = state(3, 10, 2, 2.5);

        assertEquals(scheduler.schedule(card, ReviewRating.GOOD).state(),
                scheduler.schedule(card, ReviewRating.GOOD).state());
    }
}
