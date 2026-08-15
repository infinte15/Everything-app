package com.Finn.everything_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Ein Item, das der Scheduler nicht (oder nur verspätet) unterbringen konnte. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AtRiskItemDTO {
    private Long taskId;
    private Long habitId;
    private String title;
    private Integer minutes;
    private String reason;
    /** Wann der erste geplante Block liegt — bei Überfälligem der Nachholtermin. */
    private LocalDateTime plannedStart;
}
