package com.Finn.everything_app.service;

import com.google.ortools.sat.CpSolverStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Rohes Ergebnis eines CP-SAT-Laufs, bevor irgendetwas in die DB geschrieben wird.
 *
 * Der Aufrufer darf den bestehenden Schedule erst löschen, wenn {@link #isUsable()} true ist —
 * ein leerer Kalender ist schlechter als ein veralteter.
 */
@Data
@AllArgsConstructor
public class SolveOutcome {

    private CpSolverStatus status;
    private List<ScheduledItem> items;
    private List<AtRiskItem> atRisk;

    public boolean isUsable() {
        return status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE;
    }

    /** Kein verwertbares Ergebnis — der bestehende Schedule bleibt unangetastet. */
    public static SolveOutcome unusable(CpSolverStatus status) {
        return new SolveOutcome(status, List.of(), List.of());
    }

    /** Es gab schlicht nichts zu planen; das ist ein Erfolg, kein Fehler. */
    public static SolveOutcome empty() {
        return new SolveOutcome(CpSolverStatus.OPTIMAL, List.of(), List.of());
    }
}
