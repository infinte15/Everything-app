package com.Finn.everything_app.service;

import com.Finn.everything_app.model.Task;
import lombok.Data;
import java.util.List;

@Data
public class ScheduleResult {
    private List<ScheduledItem> scheduledTasks;
    private List<ScheduledItem> scheduledHabits;
    private List<Task> unscheduledTasks;
    private Integer totalTasksScheduled;
    private Double totalHoursScheduled;
}