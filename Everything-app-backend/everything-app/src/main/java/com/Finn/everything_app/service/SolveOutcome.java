package com.Finn.everything_app.service;

import com.google.ortools.sat.CpSolverStatus;
import lombok.Data;

import java.util.List;

/**
 * Rohes Ergebnis eines CP-SAT-Laufs, bevor irgendetwas in die DB geschrieben wird.
 *
 * Der Aufrufer darf den bestehenden Schedule erst löschen, wenn {@link #isUsable()} true ist —
 * ein leerer Kalender ist schlechter als ein veralteter.
 */
@Data
public class SolveOutcome {

    private CpSolverStatus status;
    private List<ScheduledItem> items;
    private List<AtRiskItem> atRisk;

    // ---- Messwerte des Laufs ----
    //
    // Bewusst am Ergebnis und nicht in einem Feld des Service: der Solve läuft pro User und soll
    // später (Phase 3) aus der Transaktion heraus nebenläufig werden. Ein Zähler am Service wäre
    // dann nicht mehr eindeutig einem Lauf zuzuordnen.
    private long phase1Ms;
    private long phase2Ms;
    private int  intervals;
    private int  placeables;
    private long drop;
    private double placementObjective = Double.NaN;
    /** Phase 2 hat ihr Budget gerissen und Phase 1 musste erneut gelöst werden. */
    private boolean phase2Retried;

    public SolveOutcome(CpSolverStatus status, List<ScheduledItem> items, List<AtRiskItem> atRisk) {
        this.status = status;
        this.items  = items;
        this.atRisk = atRisk;
    }

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
