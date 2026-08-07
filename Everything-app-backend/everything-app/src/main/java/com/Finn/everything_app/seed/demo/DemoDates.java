package com.Finn.everything_app.seed.demo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Die zwei Datumsgriffe, die im Demo-Bestand überall gebraucht werden.
 *
 * <p>Der Bestand hängt durchgehend an {@code LocalDate.now()} statt an festen Kalenderdaten:
 * ein Seeder mit hartkodiertem "2026-03-14" sieht drei Monate später nach vergessener Karteileiche
 * aus, und der Scheduler bekäme nur noch Vergangenheit zu planen.
 */
final class DemoDates {

    private DemoDates() {
    }

    /** Montag der Woche, in der {@code date} liegt. Wochenanker für Ziele und Statistiken. */
    static LocalDate monday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Der nächste {@code day} ab {@code from} — {@code from} selbst zählt mit. */
    static LocalDate next(LocalDate from, DayOfWeek day) {
        return from.with(TemporalAdjusters.nextOrSame(day));
    }
}
