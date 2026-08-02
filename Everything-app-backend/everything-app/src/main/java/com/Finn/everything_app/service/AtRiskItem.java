package com.Finn.everything_app.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    public static AtRiskItem forTask(Long taskId, String title, int minutes, AtRiskReason reason) {
        return new AtRiskItem(taskId, null, title, minutes, reason);
    }

    public static AtRiskItem forHabit(Long habitId, String title, int minutes, AtRiskReason reason) {
        return new AtRiskItem(null, habitId, title, minutes, reason);
    }
}
