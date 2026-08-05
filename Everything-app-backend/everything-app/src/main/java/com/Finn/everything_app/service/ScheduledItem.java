package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ScheduledItem {
    private Task task;
    private Habit habit;
    private WorkoutSession workoutSession;
    /** Nur für CLASS: der Stundenplaneintrag, aus dem dieser Termin entstanden ist. */
    private CourseSchedule courseSchedule;
    /** Nur für PROJECT: das Projekt, dessen Wochenpensum dieser Block abdeckt. */
    private Project project;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private ScheduledItemType type;

    // Nur für Tasks, die in mehrere Blöcke aufgeteilt wurden: 1-basierte Nummer und Gesamtzahl,
    // damit im Kalender "Report (2/3)" steht. Bei einem einzigen Block bleibt chunkCount 1.
    private Integer chunkIndex;
    private Integer chunkCount;
}

enum ScheduledItemType {
    TASK, HABIT, WORKOUT, PROJECT, CLASS
}