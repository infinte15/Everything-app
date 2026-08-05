package com.Finn.everything_app.service;

import com.Finn.everything_app.model.ReviewRating;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Der Wiederholungsplaner — reiner Rechenkern, ohne Repository, ohne Entity.
 *
 * <p>Zeilengetreuer Port von
 * {@code Everything-app-frontend/everything_app/lib/utils/anki_scheduler.dart#schedule()}.
 * Diese Datei ist die benannte Spezifikation: gleiche Ease-Grenze 1.3, gleiche
 * again/hard/good/easy-Zweige, gleiches {@code clamp(1, 365)}, gleiche 1-/6-Minuten-
 * Lernschritte. Das Frontend rechnet dieselbe Formel nur noch für die Beschriftung der
 * Bewertungs-Buttons ("Gut → 2 Tage"); persistiert wird ausschließlich, was hier herauskommt.
 * Die Doppelung ist bewusst und durch {@code AnkiSchedulerServiceTest} abgesichert — dessen
 * Erwartungswerte sind die Ausgaben der Dart-Implementierung.
 *
 * <p>Der vorherige SM-2 war nicht buggy, sondern unbrauchbar: er rechnete die Intervallkette
 * rekursiv allein aus {@code repetitions} nach und ignorierte das tatsächlich zuletzt
 * vergebene Intervall, weil das gar nicht gespeichert wurde. Jede Ease-Änderung schrieb damit
 * die gesamte Historie rückwirkend um.
 */
@Service
public class AnkiSchedulerService {

    /** Unter 1.3 fällt der Ease-Faktor nie — sonst kollabiert eine schwere Karte auf Dauer-Minuten. */
    private static final double MIN_EASE = 1.3;
    private static final double MAX_EASE = 5.0;
    private static final double MIN_INTERVAL_DAYS = 1.0;
    private static final double MAX_INTERVAL_DAYS = 365.0;

    /** Lernschritte, bevor eine Karte in den Tagesrhythmus übergeht. */
    private static final Duration AGAIN_STEP = Duration.ofMinutes(1);
    private static final Duration HARD_STEP  = Duration.ofMinutes(6);

    /**
     * Der Wiederholungszustand einer Karte. {@code ease} ist der übliche Faktor (2.5), nicht
     * die intern als Ganzzahl ×100 gespeicherte Form — die Umrechnung bleibt am Rand.
     */
    public record CardState(
            int repetitions,
            double intervalDays,
            int learningStep,
            double ease,
            int lapses) {
    }

    /** Neuer Zustand plus Abstand bis zur nächsten Fälligkeit. */
    public record ScheduledReview(CardState state, Duration nextInterval) {
    }

    public ScheduledReview schedule(CardState current, ReviewRating rating) {
        int    reps         = current.repetitions();
        double intervalDays = current.intervalDays();
        int    learningStep = current.learningStep();
        double ease         = current.ease();

        int    newReps     = reps;
        double newInterval = intervalDays;
        int    newStep     = learningStep;
        double newEase     = ease;
        int    newLapses   = current.lapses();
        Duration next;

        switch (rating) {
            case AGAIN -> {
                // Vergessen: zurück an den Anfang der Lernphase, in derselben Sitzung erneut.
                newReps     = 0;
                newStep     = 0;
                newInterval = 0;
                newEase     = clampEase(ease - 0.2);
                // Das Dart-Original führt keine Lapses (die Zahl lebt nur serverseitig).
                newLapses   = current.lapses() + 1;
                next        = AGAIN_STEP;
            }
            case HARD -> {
                newEase = clampEase(ease - 0.15);
                if (reps == 0 || learningStep < 2) {
                    // Noch in der Lernphase: Schritt halten, gleich nochmal zeigen.
                    next = HARD_STEP;
                } else {
                    newInterval = clampInterval(intervalDays * 1.2);
                    next        = daysOf(newInterval);
                }
            }
            case GOOD -> {
                if (reps == 0 || learningStep < 1) {
                    // Erste bestandene Wiederholung: raus aus den Minuten, rein in den Tag.
                    newReps     = 1;
                    newStep     = 2;
                    newInterval = 1;
                    next        = Duration.ofDays(1);
                } else {
                    newReps     = reps + 1;
                    newStep     = 2;
                    // Bewusst der ALTE Ease-Faktor: GOOD verändert ihn nicht.
                    newInterval = clampInterval(intervalDays * ease);
                    next        = daysOf(newInterval);
                }
            }
            case EASY -> {
                newEase = clampEase(ease + 0.15);
                newReps = reps + 1;
                newStep = 2;
                if (reps == 0) {
                    newInterval = 4;
                } else {
                    newInterval = clampInterval(intervalDays * ease * 1.3);
                }
                next = daysOf(newInterval);
            }
            default -> throw new IllegalArgumentException("Unbekannte Bewertung: " + rating);
        }

        return new ScheduledReview(
                new CardState(newReps, newInterval, newStep, newEase, newLapses),
                next);
    }

    private static double clampEase(double ease) {
        return Math.min(MAX_EASE, Math.max(MIN_EASE, ease));
    }

    private static double clampInterval(double days) {
        return Math.min(MAX_INTERVAL_DAYS, Math.max(MIN_INTERVAL_DAYS, days));
    }

    private static Duration daysOf(double intervalDays) {
        return Duration.ofDays(Math.round(intervalDays));
    }
}
