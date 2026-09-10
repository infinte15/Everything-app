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
    /**
     * Der Status, mit dem Phase 1 abgeschlossen hat.
     *
     * <p>{@link #status} ist der Status von Phase 2 (und nur bei deren Scheitern der von Phase 1).
     * Damit war die teuerste Störung des Schedulers bisher unsichtbar: Kippt Phase 1 selbst um —
     * am 31.08.2026 kam sie wegen des Presolves ab ~70 Aufgaben gar nicht mehr zum Suchen —, sieht
     * man das im Log erst, wenn der ganze Lauf unbrauchbar ist. Steht hier dagegen dauerhaft
     * {@code FEASIBLE} statt {@code OPTIMAL}, schöpft Phase 1 ihren Deckel aus und die Kante ist
     * nah, obwohl der Lauf noch durchgeht.
     */
    private CpSolverStatus phase1Status;
    private int  intervals;
    private int  placeables;
    private long drop;
    private double placementObjective = Double.NaN;
    /** Phase 2 hat ihr Budget gerissen und Phase 1 musste erneut gelöst werden. */
    private boolean phase2Retried;
    /**
     * Wie viele Blöcke die beiden Nachläufe noch gerettet haben (nachgerückt / gequetscht).
     *
     * Im Log, weil die Pässe sonst unsichtbar sind: sie laufen nur, wenn der Hauptlauf etwas
     * liegen gelassen hat, und ein Bestand ohne Engpass zeigt dauerhaft 0+0.
     */
    private int reliefCatchUp;
    private int reliefSqueeze;
    /**
     * Wie viele Blöcke der Vorlauf für überfällige Aufgaben platziert hat.
     *
     * Ohne diese Zahl ist der Pass unsichtbar: er läuft nur, wenn überhaupt etwas überfällig ist,
     * und man kann an der SCHED-Zeile sonst nicht unterscheiden, ob nichts überfällig war oder ob
     * der Vorlauf nichts untergebracht hat.
     */
    private int overduePlaced;

    /**
     * Wie viele bestehende Blöcke der Verdrängungs-Nachlauf verschoben oder verworfen hat.
     *
     * Steht hier dauerhaft etwas > 0, ist nicht der Pass das Problem, sondern die Lage: dann ist
     * der Kalender chronisch so voll, dass Deadlines nur noch auf Kosten anderer Blöcke zu halten
     * sind.
     */
    private int displaced;

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

    /**
     * Kein verwertbarer Plan — aber die Liste dessen, was ohne Termin dasteht.
     *
     * Der Kalender bleibt in diesem Fall unverändert (siehe
     * {@code SmartSchedulerService#generateOptimalSchedule}); die Meldung ist dann das Einzige,
     * was der Nutzer überhaupt bekommt, und darf deshalb nicht leer sein.
     */
    public static SolveOutcome unusable(CpSolverStatus status, List<AtRiskItem> atRisk) {
        return new SolveOutcome(status, List.of(), atRisk);
    }

    /** Es gab schlicht nichts zu planen; das ist ein Erfolg, kein Fehler. */
    public static SolveOutcome empty() {
        SolveOutcome leer = new SolveOutcome(CpSolverStatus.OPTIMAL, List.of(), List.of());
        leer.setPhase1Status(CpSolverStatus.OPTIMAL);
        return leer;
    }
}
