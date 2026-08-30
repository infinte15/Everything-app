package com.Finn.everything_app.model;

/**
 * Satz-Art. Die Namen entsprechen bewusst dem {@code GymSetType} des Flutter-Clients.
 *
 * <p>In der Datenbank ist die Spalte nullable, weil {@code ddl-auto=update} kein NOT NULL auf
 * eine bereits gefuellte Tabelle setzen kann. {@code null} ist deshalb als {@link #NORMAL}
 * zu lesen - dafuer gibt es {@link #orDefault(SetType)}.
 */
public enum SetType {
    NORMAL,
    WARMUP,
    DROP,
    FAILURE,
    AMRAP,
    SINGLELEFT,
    SINGLERIGHT,

    /**
     * Ein Cluster innerhalb eines Rest-Pause-Satzes. Haengt ueber
     * {@code ExerciseSet.parentSetId} am Arbeitssatz, dessen {@code reps} bereits die Summe
     * aller Cluster tragen.
     */
    RESTPAUSE;

    public static SetType orDefault(SetType value) {
        return value != null ? value : NORMAL;
    }

    /**
     * Zaehlt ein Satz dieser Art ins Trainingsvolumen?
     *
     * <ul>
     *   <li>{@link #WARMUP} - nein. Aufwaermsaetze sind Vorbereitung. Seit die Rampe sie
     *       automatisch anlegt, wuerde sonst jede Uebung das Volumen aufblaehen.</li>
     *   <li>{@link #RESTPAUSE} - nein. Der Burst ist eine Teilmenge des Arbeitssatzes;
     *       seine Wiederholungen stecken schon in der Zeile darueber. Mitzaehlen hiesse
     *       doppelt zaehlen.</li>
     *   <li>{@link #DROP} - ja. Der Abfallsatz ist zusaetzliche Arbeit mit eigener Last und
     *       steht deshalb als eigene Zeile mit eigenem Gewicht da.</li>
     * </ul>
     */
    public static boolean countsTowardVolume(SetType value) {
        SetType type = orDefault(value);
        return type != WARMUP && type != RESTPAUSE;
    }
}
