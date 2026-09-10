package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Gemeinsame Bausteine für die Scheduler-Tests.
 *
 * <p>Die Builder standen bis hierher doppelt in {@code SmartSchedulerServiceTest} und
 * {@code SmartSchedulerSzenarienTest}. Mit den drei neuen Testklassen (Qualität, Eingaben, Last)
 * wären es fünf Kopien geworden — und fünf Stellen, an denen ein neues Pflichtfeld an einer
 * Entität nachgezogen werden muss.
 *
 * <p>Bewusst eine <b>Instanz</b> und keine Sammlung statischer Methoden: der ID-Zähler gehört zum
 * Testlauf, nicht zur Klasse. Statisch geteilt würden IDs zwischen parallel laufenden Testklassen
 * wandern, und die Fehlermeldungen wären nicht mehr reproduzierbar.
 */
class SchedulerFixtures {

    private long naechsteId = 9000L;

    /**
     * Die produktiven Scheduler-Einstellungen, gelesen aus {@code application.properties}.
     *
     * <p>Es gibt keinen zweiten Ort, an dem ein Test das Zeitbudget nachschlagen dürfte. Der
     * vorige Versuch — eine Konstante {@code PRODUKTIONS_ZEITBUDGET_SEKUNDEN = 2.0} mit dem
     * Kommentar "muss zu application.properties passen" — stand über Monate auf 2.0, während
     * produktiv 1.5 lief. Der Test, der als Qualitätsgatter gedacht war, gab dem Löser damit ein
     * Drittel mehr Zeit, als er im Betrieb je bekommt.
     */
    static Properties produktivEinstellungen() {
        Path datei = Path.of("src/main/resources/application.properties");
        if (!Files.exists(datei)) {
            throw new AssertionError("application.properties fehlt — sie ist seit der "
                    + "Deployment-Vorbereitung versioniert und muss vorhanden sein: "
                    + datei.toAbsolutePath());
        }
        Properties p = new Properties();
        try (var in = Files.newInputStream(datei)) {
            p.load(in);
        } catch (IOException e) {
            throw new AssertionError("application.properties nicht lesbar", e);
        }
        return p;
    }

    /** Das Zeitbudget, das der Nutzer im Betrieb tatsächlich abwartet. */
    static double produktionsBudgetSekunden() {
        String wert = produktivEinstellungen().getProperty("scheduler.solver-time-limit-seconds");
        if (wert == null) throw new AssertionError(
                "scheduler.solver-time-limit-seconds fehlt in application.properties");
        return Double.parseDouble(wert.trim());
    }

    long id() {
        return naechsteId++;
    }

    // ------------------------------------------------------------------
    // Aufgaben
    // ------------------------------------------------------------------

    Task task(String titel, int minuten, int prio, LocalDateTime deadline) {
        Task t = new Task();
        t.setId(id());
        t.setTitle(titel);
        t.setEstimatedDurationMinutes(minuten);
        t.setPriority(prio);
        t.setDeadline(deadline);
        t.setStatus(TaskStatus.TODO);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    Task unteilbar(Task t) {
        t.setSplittable(false);
        return t;
    }

    Task teilbar(Task t, Integer minChunk, Integer maxChunk) {
        t.setSplittable(true);
        t.setMinChunkMinutes(minChunk);
        t.setMaxChunkMinutes(maxChunk);
        return t;
    }

    // ------------------------------------------------------------------
    // Gewohnheiten
    // ------------------------------------------------------------------

    Habit taeglicheGewohnheit(String name, int minuten, int prio) {
        Habit h = new Habit();
        h.setId(id());
        h.setName(name);
        h.setTimesPerWeek(7);
        h.setDurationMinutes(minuten);
        h.setPriority(prio);
        h.setStartDate(LocalDate.now().minusDays(60));
        return h;
    }

    Habit gewohnheitProWoche(String name, int minuten, int prio, int malProWoche) {
        Habit h = taeglicheGewohnheit(name, minuten, prio);
        h.setTimesPerWeek(malProWoche);
        return h;
    }

    // ------------------------------------------------------------------
    // Training
    // ------------------------------------------------------------------

    WorkoutSession training(String name, int minuten, LocalDate wochenstart) {
        WorkoutSession w = new WorkoutSession();
        w.setId(id());
        w.setName(name);
        w.setDurationMinutes(minuten);
        w.setIsFlexible(true);
        w.setIsCompleted(false);
        w.setTargetWeekStart(wochenstart);
        return w;
    }

    WorkoutSession trainingAmWunschtag(String name, int minuten, LocalDate wochenstart,
                                       DayOfWeek wunschtag) {
        WorkoutSession w = training(name, minuten, wochenstart);
        Routine r = new Routine();
        r.setId(id());
        r.setName(name);
        r.setPreferredWeekday(wunschtag.getValue());
        w.setRoutine(r);
        return w;
    }

    // ------------------------------------------------------------------
    // Projekte
    // ------------------------------------------------------------------

    Project projekt(String name, int sitzungenProWoche, int minutenJeSitzung) {
        Project p = new Project();
        p.setId(id());
        p.setName(name);
        p.setStatus(ProjectStatus.ACTIVE);
        p.setWeeklySessionCount(sitzungenProWoche);
        p.setSessionDurationMinutes(minutenJeSitzung);
        return p;
    }

    // ------------------------------------------------------------------
    // Kalender und Stundenplan
    // ------------------------------------------------------------------

    CalendarEvent fixerBlock(LocalDateTime von, LocalDateTime bis) {
        CalendarEvent e = new CalendarEvent();
        e.setId(id());
        e.setTitle("Fest");
        e.setIsFixed(true);
        e.setStartTime(von);
        e.setEndTime(bis);
        e.setEventType(EventType.OTHER);
        return e;
    }

    CourseSchedule vorlesung(DayOfWeek tag, LocalTime von, LocalTime bis) {
        long nummer = id();
        Course course = new Course();
        course.setId(nummer);
        course.setName("Vorlesung " + nummer);
        course.setColor("#C2C1FF");

        CourseSchedule cs = new CourseSchedule();
        cs.setId(nummer);
        cs.setCourse(course);
        cs.setDayOfWeek(tag);
        cs.setStartTime(von);
        cs.setEndTime(bis);
        return cs;
    }

    /** Den Plan als Kalendereinträge zurückspielen, damit der nächste Lauf einen Bestand sieht. */
    List<CalendarEvent> alsEvents(ScheduleResult r) {
        List<CalendarEvent> out = new ArrayList<>();
        for (ScheduledItem i : r.getScheduledTasks()) {
            CalendarEvent e = new CalendarEvent();
            e.setId(id());
            e.setTitle(i.getTask().getTitle());
            e.setIsFixed(false);
            e.setStartTime(i.getStartTime());
            e.setEndTime(i.getEndTime());
            e.setEventType(EventType.TASK);
            e.setRelatedTask(i.getTask());
            out.add(e);
        }
        for (ScheduledItem i : r.getScheduledHabits()) {
            if (i.getHabit() == null) continue;
            CalendarEvent e = new CalendarEvent();
            e.setId(id());
            e.setTitle(i.getHabit().getName());
            e.setIsFixed(false);
            e.setStartTime(i.getStartTime());
            e.setEndTime(i.getEndTime());
            e.setEventType(EventType.HABIT);
            e.setRelatedHabit(i.getHabit());
            e.setTargetWeekStart(i.getTargetWeekStart());
            e.setTargetDate(i.getTargetDate());
            out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Ein ganzer Bestand
    // ------------------------------------------------------------------

    /**
     * Alles, was ein Lauf an Eingaben sieht — damit die Testklassen ihre Mocks daraus stellen.
     */
    record Bestand(List<Task> aufgaben, List<Habit> gewohnheiten, List<WorkoutSession> trainings,
                   List<Project> projekte, List<CourseSchedule> vorlesungen,
                   List<CalendarEvent> termine) {}

    /**
     * Ein realistischer Bestand in der gewünschten Größe.
     *
     * <p><b>Der Zuschnitt ist wichtiger als die Aufgabenzahl.</b> Ein Bestand aus lauter Aufgaben
     * erreicht die Modellgröße des Betriebs nicht — rund 85 % der Placeables kommen aus den
     * wiederkehrenden Items, weil die sich mit dem Horizont vervielfachen (8 tägliche
     * Gewohnheiten über 31 Tage sind allein 248 Slots). Genau daran ist der Befund vom 30.08.2026
     * vorbeigegangen, der im damaligen Harness "nicht nachstellbar" war.
     *
     * <p>Deterministisch über den Keim: ein fehlgeschlagener Lauf lässt sich mit derselben Zahl
     * wiederholen.
     */
    Bestand bestand(int anzahlAufgaben, long keim, LocalDate ab) {
        Random rnd = new Random(keim);

        List<Task> aufgaben = new ArrayList<>();
        for (int i = 0; i < anzahlAufgaben; i++) {
            int prio  = 1 + rnd.nextInt(5);
            int dauer = 30 + rnd.nextInt(4) * 30;
            // Zwei Drittel mit Termin, über den ganzen Horizont gestreut; der Rest ohne.
            Task t = task("A" + i, dauer, prio,
                    rnd.nextInt(3) == 0 ? null : ab.plusDays(1 + rnd.nextInt(28)).atTime(18, 0));
            if (dauer > 60 && rnd.nextBoolean()) {
                teilbar(t, 30, 90);
                t.setMaxChunksPerDay(1 + rnd.nextInt(2));
            } else {
                unteilbar(t);
            }
            aufgaben.add(t);
        }

        List<Habit> gewohnheiten = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            gewohnheiten.add(taeglicheGewohnheit("G" + i, 15 + i * 5, 1 + (i % 5)));
        }

        LocalDate wochenstart = ab.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<WorkoutSession> trainings = new ArrayList<>();
        for (int woche = 0; woche < 4; woche++) {
            LocalDate w = wochenstart.plusWeeks(woche);
            trainings.add(training("Push", 60, w));
            trainings.add(training("Pull", 60, w));
            trainings.add(training("Beine", 60, w));
        }

        List<Project> projekte = List.of(
                projekt("Bachelorarbeit", 3, 90),
                projekt("Umzug", 2, 60),
                projekt("Website", 2, 45));

        List<CourseSchedule> vorlesungen = List.of(
                vorlesung(DayOfWeek.MONDAY,    LocalTime.of(10, 0), LocalTime.of(11, 30)),
                vorlesung(DayOfWeek.MONDAY,    LocalTime.of(14, 0), LocalTime.of(15, 30)),
                vorlesung(DayOfWeek.TUESDAY,   LocalTime.of(8, 0),  LocalTime.of(9, 30)),
                vorlesung(DayOfWeek.WEDNESDAY, LocalTime.of(12, 0), LocalTime.of(13, 30)),
                vorlesung(DayOfWeek.THURSDAY,  LocalTime.of(10, 0), LocalTime.of(11, 30)),
                vorlesung(DayOfWeek.FRIDAY,    LocalTime.of(9, 0),  LocalTime.of(10, 30)));

        List<CalendarEvent> termine = new ArrayList<>();
        for (int tag = 0; tag < 24; tag++) {
            LocalDate d = ab.plusDays(tag);
            termine.add(fixerBlock(d.atTime(16, 0), d.atTime(17, 0)));
        }

        return new Bestand(aufgaben, gewohnheiten, trainings, projekte, vorlesungen, termine);
    }

    // ------------------------------------------------------------------
    // Auswertung
    // ------------------------------------------------------------------

    static List<ScheduledItem> alleItems(ScheduleResult r) {
        List<ScheduledItem> alle = new ArrayList<>(r.getScheduledTasks());
        alle.addAll(r.getScheduledHabits());
        return alle;
    }

    /**
     * Kein Block überlappt einen anderen.
     *
     * <p>Die Zusicherung, die IMMER gelten muss, egal wie das Szenario aussieht — deshalb steht
     * sie hier und nicht in einer einzelnen Testklasse.
     */
    static void assertKeineUeberlappung(ScheduleResult r) {
        List<ScheduledItem> alle = new ArrayList<>(alleItems(r));
        alle.sort(Comparator.comparing(ScheduledItem::getStartTime));
        for (int i = 1; i < alle.size(); i++) {
            ScheduledItem a = alle.get(i - 1);
            ScheduledItem b = alle.get(i);
            if (b.getStartTime().isBefore(a.getEndTime())) {
                throw new AssertionError("Überlappung: " + a.getStartTime() + "–" + a.getEndTime()
                        + " und " + b.getStartTime() + "–" + b.getEndTime());
            }
        }
    }
}
