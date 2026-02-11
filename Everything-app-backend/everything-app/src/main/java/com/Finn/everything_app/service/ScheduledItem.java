package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ScheduledItem {
    private Task task;
    private Habit habit;
    private WorkoutSession workoutSession;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private ScheduledItemType type;
}

enum ScheduledItemType {
    TASK, HABIT, WORKOUT, CLASS
}