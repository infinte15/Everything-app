package com.Finn.everything_app.model;

import java.time.LocalTime;

/**
 * Wunschfenster einer Habit (Reclaims "ideal time").
 *
 * Im Gegensatz zur alten preferredTime ist das ein Bereich, kein Punkt: innerhalb des Fensters
 * ist jede Lage gleich gut, erst außerhalb steigen die Kosten. Die Grenze bleibt weich — passt
 * eine Habit nicht ins Fenster, wird sie lieber daneben geplant als gar nicht.
 */
public enum HabitWindow {

    MORNING(LocalTime.of(6, 0), LocalTime.of(12, 0)),
    AFTERNOON(LocalTime.of(12, 0), LocalTime.of(17, 0)),
    EVENING(LocalTime.of(17, 0), LocalTime.of(22, 0)),
    /** Egal wann — der Solver darf frei innerhalb der Arbeitszeit wählen. */
    ANYTIME(null, null),
    /** Eigene Zeiten aus idealWindowStart/idealWindowEnd. */
    CUSTOM(null, null);

    private final LocalTime defaultStart;
    private final LocalTime defaultEnd;

    HabitWindow(LocalTime defaultStart, LocalTime defaultEnd) {
        this.defaultStart = defaultStart;
        this.defaultEnd = defaultEnd;
    }

    public LocalTime defaultStart() {
        return defaultStart;
    }

    public LocalTime defaultEnd() {
        return defaultEnd;
    }

    public boolean hasFixedRange() {
        return defaultStart != null && defaultEnd != null;
    }
}
