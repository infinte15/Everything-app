package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Alle gepinnten Scheduler-Blöcke ab dem Horizontbeginn — auch die JENSEITS des Horizonts.
     *
     * {@link #fixedEvents} ist auf den Horizont beschnitten, weil es dem Solver belegte Zeit auf
     * seiner Zeitachse meldet. Für die Frage "ist dieses Item schon versorgt?" ist genau dieser
     * Zuschnitt aber falsch: zieht der Nutzer einen Block über den Horizont hinaus, fiele er aus
     * der Buchhaltung, das Item gälte als ungeplant und bekäme im Horizont einen zweiten Block.
     * Deshalb eine eigene, unbeschnittene Liste rein für die Buchhaltung.
     */
    private List<CalendarEvent> pinnedCommitments;

    /**
     * Vom Nutzer übersprungene Ausführungen.
     *
     * Sie sperren keine Zeit und sind keine Stabilitätsanker — ihr einziger Zweck ist, das
     * Wochenpensum ihrer Gewohnheit bzw. ihres Projekts als bereits gedeckt zu melden. Ohne sie
     * stünde die Woche unter Pensum und der Solver legte prompt Ersatz an.
     */
    private List<CalendarEvent> skippedEvents;

    /**
     * Je Routine die primaer beanspruchten Muskeln.
     *
     * <p>Damit bemisst der Planer den Abstand zwischen zwei Einheiten nach dem, was sie
     * gemeinsam beanspruchen, statt fuer alle Trainings denselben Ruhetag anzusetzen: Push nach
     * Push braucht mehr Erholung als Push nach Beinen. Siehe
     * {@code SmartSchedulerService.restDaysBetween}.
     */
    private Map<Long, Set<MuscleGroup>> routineMuscles = Map.of();
}