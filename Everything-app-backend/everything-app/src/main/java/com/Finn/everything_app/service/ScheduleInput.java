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
     * Projekte, die Wochenpensum bekommen sollen. Ihre Sessions haben keine eigene Entität —
     * die Slots werden bei jedem Lauf aus weeklySessionCount/sessionDurationMinutes abgeleitet.
     */
    private List<Project> projects;

    /**
     * Die vom letzten Lauf erzeugten (nicht gepinnten) Events, eingesammelt BEVOR sie gelöscht
     * werden. Speist den Stabilitätsterm: ohne ihn springt bei jeder kleinen Änderung der
     * komplette restliche Kalender.
     */
    private List<CalendarEvent> previousScheduledEvents;

    /**
     * Blöcke aus dem letzten Lauf, die vor dem Umplanzeitpunkt begonnen haben. Sie werden weder
     * gelöscht noch neu geplant, sondern wie gepinnte Termine behandelt: sie blockieren ihre Zeit,
     * ihre Minuten zählen auf den Task und ihr Tag zählt auf die Wochenquote der Habit. Ohne das
     * verschwindet der bereits gelaufene Vormittag aus dem Kalender, sobald irgendeine Änderung
     * eine Neuplanung auslöst.
     */
    private List<CalendarEvent> frozenEvents;
}