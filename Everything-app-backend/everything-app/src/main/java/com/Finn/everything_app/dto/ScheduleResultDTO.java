package com.Finn.everything_app.dto;


import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleResultDTO {
    private Integer totalTasksScheduled;
    private Double totalHoursScheduled;
    private Integer unscheduledTasksCount;
    private String message;

    /** Was nicht (oder nur verspätet) untergebracht werden konnte. */
    private List<AtRiskItemDTO> atRisk = new ArrayList<>();
    private String solverStatus;
}
