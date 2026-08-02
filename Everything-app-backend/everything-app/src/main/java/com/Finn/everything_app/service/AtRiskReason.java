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
    OUTSIDE_HORIZON
}
