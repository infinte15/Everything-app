package com.Finn.everything_app.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Ein Item, das der Scheduler nicht (oder nur verspätet) unterbringen konnte.
 *
 * Reclaim-Verhalten: statt den kompletten Plan scheitern zu lassen, wird so viel wie möglich
 * platziert und der Rest als "at risk" gemeldet. Diese Items werden bewusst NICHT als
 * CalendarEvent persistiert — sie sind reine Reporting-Daten für die Antwort.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AtRiskItem {
    private Long taskId;
    private Long habitId;
    private String title;
    /** Wie viele Minuten unplatziert blieben. */
    private Integer minutes;
    private AtRiskReason reason;
    /**
     * Wann der erste Block dieses Items liegt, oder {@code null}, wenn es gar keinen gibt.
     *
     * Wichtig für den überfälligen Fall: dort ist die Meldung keine Bitte um Nachsicht, sondern
     * eine Ansage — „überfällig, Nachholtermin heute 20:15". Ohne diesen Zeitpunkt müsste die
     * Oberfläche ihn aus den geladenen Kalendertagen zusammensuchen und läge daneben, sobald der
     * Block in einem Monat liegt, den sie gerade nicht geladen hat.
     */
    private LocalDateTime plannedStart;

    public static AtRiskItem forTask(Long taskId, String title, int minutes, AtRiskReason reason,
                                     LocalDateTime plannedStart) {
        return new AtRiskItem(taskId, null, title, minutes, reason, plannedStart);
    }

    public static AtRiskItem forHabit(Long habitId, String title, int minutes, AtRiskReason reason) {
        return new AtRiskItem(null, habitId, title, minutes, reason, null);
    }
}
