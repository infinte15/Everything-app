package com.Finn.everything_app.repository.projection;

/** Bestes geschaetztes Ein-Wiederholungs-Maximum je Uebung (Epley, ueber alle Einheiten). */
public interface ExerciseOneRmRow {
    Long getExerciseId();

    Double getBestOneRm();
}
