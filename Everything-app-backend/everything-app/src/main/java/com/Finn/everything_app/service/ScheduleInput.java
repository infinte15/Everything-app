package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import lombok.Data;
import java.util.List;

@Data
public class ScheduleInput {
    private List<Task> tasks;
    private List<CalendarEvent> fixedEvents;
    private List<Habit> habits;
    private List<WorkoutSession> workouts;
    private List<CourseSchedule> courseSchedules;
}