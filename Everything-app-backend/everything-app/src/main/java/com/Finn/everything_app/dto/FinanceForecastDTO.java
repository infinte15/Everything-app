package com.Finn.everything_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Was bis Monatsende noch uebrig bleibt - die Kernzahl des Finance Space.
 *
 * <p>{@link #available} ist bewusst {@code Double} und darf {@code null} sein: ohne verbundenes
 * Konto gibt es keinen Kontostand, und eine erfundene Null waere schlimmer als keine Zahl. Die
 * Oberflaeche zeigt in dem Fall den "Konto verbinden"-Zustand.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceForecastDTO {

    private String month;
    private LocalDate monthStart;
    private LocalDate monthEnd;

    /** Heutiger Kontostand ueber alle abgerufenen Konten. {@code null} ohne Bankanbindung. */
    private Double currentBalance;

    /** Voraussichtlicher Kontostand am Monatsende. {@code null} ohne Bankanbindung. */
    private Double available;

    /** Noch faellige Vertragsausgaben bis Monatsende. */
    private Double upcomingContractExpenses;

    /** Noch erwartete Vertragseinnahmen bis Monatsende (Gehalt). */
    private Double upcomingContractIncome;

    /** Geschaetzte Alltagsausgaben fuer die Resttage - Vertraege sind darin nicht enthalten. */
    private Double projectedVariableExpenses;

    /** Durchschnitt der letzten drei Monate, ohne Vertragsbuchungen. */
    private Double averageDailyVariableExpenses;

    private int daysRemaining;

    /** {@code true}, wenn die Prognose ins Minus laeuft - der einzige Anlass fuer Rot. */
    private boolean shortfall;

    /** Bereits gebucht in diesem Monat. */
    private Double monthIncome;
    private Double monthExpenses;

    /** Tagesreihe fuer den Chart: erst Ist, dann Projektion. */
    private List<DayPoint> series;

    /** Was in den Resttagen noch ansteht - als Liste, nicht nur als Summe. */
    private List<ContractDTO> upcoming;

    /**
     * Ein Punkt der Saldokurve.
     *
     * @param projected {@code false} bis heute (aus echten Buchungen rekonstruiert),
     *                  {@code true} danach. Die App zeichnet den projizierten Teil gestrichelt -
     *                  eine durchgehende Linie wuerde eine Gewissheit vorgeben, die es nicht gibt.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayPoint {
        private LocalDate date;
        private Double balance;
        private boolean projected;
    }
}
