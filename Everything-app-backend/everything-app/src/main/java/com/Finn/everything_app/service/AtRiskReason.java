package com.Finn.everything_app.service;

/** Warum ein Item vom Scheduler nicht (vollständig) platziert werden konnte. */
public enum AtRiskReason {
    /** Kein freier Platz im Horizont — der Kalender ist zu voll. */
    NO_ROOM,
    /** Die Deadline liegt bereits in der Vergangenheit. */
    PAST_DEADLINE,
    /** Platziert, aber erst nach der Deadline. */
    WOULD_MISS_DEADLINE,
    /** Das Item gehört in eine Woche/einen Tag außerhalb des Planungshorizonts. */
    OUTSIDE_HORIZON,
    /**
     * Die geschätzte Zeit ist aufgebraucht, die Aufgabe steht aber weiter offen.
     *
     * <p>Der Planer kann hier nichts mehr tun: {@code chunkSizes} rechnet
     * {@code estimated − completed − gepinnt}, und bei {@code <= 0} entstehen keine Blöcke mehr.
     * Vorher verschwand die Aufgabe dadurch LAUTLOS aus dem Kalender und stand trotzdem auf offen —
     * die Art Zustand, in der man die App für kaputt hält. Es ist kein Deadline-Risiko, sondern eine
     * Bitte um eine neue Schätzung.
     */
    ESTIMATE_EXHAUSTED
}
