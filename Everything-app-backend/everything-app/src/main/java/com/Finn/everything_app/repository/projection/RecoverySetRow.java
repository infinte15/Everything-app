package com.Finn.everything_app.repository.projection;

import java.time.LocalDateTime;

/**
 * Ein Arbeitssatz, aufgeschluesselt nach den Muskeln, die er belastet.
 *
 * <p>Ein Satz erscheint mehrfach - einmal je Muskel. {@link #getFactor()} traegt, wie stark:
 * 1,0 fuer Primaer-, 0,5 fuer Sekundaermuskeln.
 */
public interface RecoverySetRow {
    String getMuscle();

    Double getFactor();

    Long getExerciseId();

    Long getSessionId();

    Double getWeight();

    Integer getReps();

    LocalDateTime getPerformedAt();
}
