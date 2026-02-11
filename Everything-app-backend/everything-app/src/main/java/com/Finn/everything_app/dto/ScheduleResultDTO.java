package com.Finn.everything_app.dto;


import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleResultDTO {
    private Integer totalTasksScheduled;
    private Double totalHoursScheduled;
    private Integer unscheduledTasksCount;
    private String message;
}
