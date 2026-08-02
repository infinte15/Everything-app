package com.Finn.everything_app.service;

import com.Finn.everything_app.model.Task;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ScheduleResult {
    private List<ScheduledItem> scheduledTasks = new ArrayList<>();
    private List<ScheduledItem> scheduledHabits = new ArrayList<>();
    private List<Task> unscheduledTasks = new ArrayList<>();
    private Integer totalTasksScheduled = 0;
    private Double totalHoursScheduled = 0.0;

    /** Items, die nicht (oder nur verspätet) untergebracht werden konnten. */
    private List<AtRiskItem> atRisk = new ArrayList<>();
    /** Für UI und Logs: warum der Lauf so ausging, wie er ausging. */
    private String message;
    private String solverStatus;
}
