package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import lombok.Data;
import java.util.List;

@Data
public class ScheduleInput {
    private List<Task> tasks;
    private List<CalendarEvent> fixedEvents;
    private List<Habit> habits;
    private List<WorkoutSession> fixedWorkouts;
    private List<WorkoutSession> flexibleWorkouts;
    private List<CourseSchedule> courseSchedules;

    /**
     * Die vom letzten Lauf erzeugten (nicht gepinnten) Events, eingesammelt BEVOR sie gelöscht
     * werden. Speist den Stabilitätsterm: ohne ihn springt bei jeder kleinen Änderung der
     * komplette restliche Kalender.
     */
    private List<CalendarEvent> previousScheduledEvents;
}