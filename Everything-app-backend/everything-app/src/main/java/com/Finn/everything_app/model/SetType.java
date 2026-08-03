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
    SINGLERIGHT;

    public static SetType orDefault(SetType value) {
        return value != null ? value : NORMAL;
    }
}
