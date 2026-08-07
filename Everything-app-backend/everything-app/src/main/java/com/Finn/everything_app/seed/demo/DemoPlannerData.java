package com.Finn.everything_app.seed.demo;

import com.Finn.everything_app.model.CalendarEvent;
import com.Finn.everything_app.model.EventType;
import com.Finn.everything_app.model.Habit;
import com.Finn.everything_app.model.HabitCompletion;
import com.Finn.everything_app.model.HabitFrequency;
import com.Finn.everything_app.model.HabitWindow;
import com.Finn.everything_app.model.Project;
import com.Finn.everything_app.model.ProjectStatus;
import com.Finn.everything_app.model.SpaceType;
import com.Finn.everything_app.model.Task;
import com.Finn.everything_app.model.TaskStatus;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.CalendarEventRepository;
import com.Finn.everything_app.repository.HabitCompletionRepository;
import com.Finn.everything_app.repository.HabitRepository;
import com.Finn.everything_app.repository.ProjectRepository;
import com.Finn.everything_app.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Demo-Bestand für Aufgaben, Projekte, Gewohnheiten und die festen Kalendereinträge.
 *
 * <p>Offene Aufgaben bekommen hier bewusst <b>keine</b> Uhrzeit: {@code scheduledStartTime} bleibt
 * leer, damit der Smart Scheduler sie selbst platziert. Genau das ist der Teil der App, den eine
 * Demo zeigen soll — von Hand gesetzte Zeiten würden ihn unsichtbar machen.
 *
 * <p>Ebenso fehlen Kalendereinträge vom Typ TASK, HABIT, WORKOUT, PROJECT und CLASS: die erzeugt
 * der Scheduler und räumt sie bei jedem Lauf vorher weg. Hier stehen nur die Termine, die von
 * außen kommen und den Kalender blockieren.
 */
@Component
@RequiredArgsConstructor
public class DemoPlannerData {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final HabitRepository habitRepository;
    private final HabitCompletionRepository habitCompletionRepository;
    private final CalendarEventRepository calendarEventRepository;

    @Transactional
    public void seed(User user, LocalDate today) {
        Project thesis = project(user, "Bachelorarbeit vorbereiten", ProjectStatus.PLANNING,
                today.minusWeeks(2), today.plusMonths(5), 10, 2, 90, """
                        Thema finden, Betreuer ansprechen, Exposé schreiben. Anmeldung muss bis zum \
                        Ende des Semesters raus, sonst verschiebt sich alles um ein halbes Jahr.""");

        Project app = project(user, "Everything App v2", ProjectStatus.IN_PROGRESS,
                today.minusWeeks(9), today.plusWeeks(8), 45, 2, 60, """
                        Eigenes Nebenprojekt. Aktuell: Kalender-Ansicht überarbeiten und den \
                        Scheduler auf mehrere Wochen ausweiten.""");

        Project trip = project(user, "Interrail-Reise im September", ProjectStatus.ACTIVE,
                today.minusWeeks(1), today.plusWeeks(12), 15, 1, 45, """
                        Route, Tickets, Unterkünfte. Zu viert — Abstimmung frisst mehr Zeit als \
                        die Planung selbst.""");

        // Pausiert heißt: keine Wochensitzungen. Sonst schneidet der Scheduler weiter Zeit für
        // ein Projekt heraus, das laut Status gerade gar nicht laufen soll.
        Project flat = project(user, "WG-Zimmer renovieren", ProjectStatus.ON_HOLD,
                today.minusWeeks(6), today.plusWeeks(20), 20, 0, 120, """
                        Streichen und ein Regal bauen. Liegt bis nach den Klausuren auf Eis.""");

        Project move = project(user, "Umzug in die neue WG", ProjectStatus.COMPLETED,
                today.minusWeeks(20), today.minusWeeks(9), 100, 0, 0, """
                        Abgeschlossen. Bleibt als Beleg dafür, dass Umzüge doppelt so lange \
                        dauern wie geplant.""");
        move.setActualEndDate(today.minusWeeks(9));
        projectRepository.save(move);

        seedTasks(user, today, thesis, app, trip, flat, move);
        seedHabits(user, today);
        seedFixedEvents(user, today);
    }

    // ------------------------------------------------------------------ Projekte

    private Project project(User user, String name, ProjectStatus status, LocalDate start,
                            LocalDate targetEnd, int completion, int weeklySessions,
                            int sessionMinutes, String description) {
        Project project = new Project();
        project.setUser(user);
        project.setName(name);
        project.setDescription(description);
        project.setStatus(status);
        project.setStartDate(start);
        project.setTargetEndDate(targetEnd);
        project.setCompletionPercentage(completion);
        project.setWeeklySessionCount(weeklySessions);
        project.setSessionDurationMinutes(sessionMinutes);
        return projectRepository.save(project);
    }

    // ------------------------------------------------------------------ Aufgaben

    private void seedTasks(User user, LocalDate today, Project thesis, Project app,
                           Project trip, Project flat, Project move) {

        // --- Lernen: der Löwenanteil, teils in Häppchen planbar ---
        // Der große, teilbare Brocken im laufenden Horizont: acht Stunden, höchstens zwei
        // Stunden am Tag - daran lässt sich zeigen, wie CP-SAT eine Aufgabe zerlegt.
        Task klausur = task(user, "Klausurvorbereitung Analysis II", SpaceType.STUDY, "Analysis II", 5,
                today.plusWeeks(5).atTime(8, 0), 480, TaskStatus.TODO, """
                        Altklausuren der letzten vier Semester durchrechnen. Schwerpunkt \
                        Mehrfachintegrale und der Satz über implizite Funktionen.""");
        splittable(klausur, 60, 120, 1);

        // Die zweite Lernphase beginnt bewusst erst in drei Wochen: zwei gleichzeitige
        // Klausurvorbereitungen würden den Horizont füllen und alles andere verdrängen.
        Task theoKlausur = task(user, "Klausurvorbereitung Theoretische Informatik", SpaceType.STUDY,
                "Theoretische Informatik", 5, today.plusWeeks(6).atTime(8, 0), 600, TaskStatus.TODO, """
                        Nach der Probeklausur (3,0) mit Berechenbarkeit anfangen — da war die \
                        Lücke am größten.""");
        splittable(theoKlausur, 60, 120, 2);
        theoKlausur.setNotBefore(today.plusWeeks(3).atTime(8, 0));
        taskRepository.save(theoKlausur);

        progress(task(user, "Übungsblatt 9 Analysis II", SpaceType.STUDY, "Analysis II", 4,
                today.plusDays(4).atTime(23, 59), 180, TaskStatus.TODO,
                "Aufgabe 1 und 2 stehen, bei Aufgabe 4 (Lagrange) hänge ich."), 60);

        task(user, "Übungsblatt 8 Datenbanksysteme", SpaceType.STUDY, "Datenbanksysteme", 4,
                today.plusDays(2).atTime(23, 59), 120, TaskStatus.TODO,
                "Anfrageoptimierung — Kostenmodell an drei Beispielen durchrechnen.");

        task(user, "Übungsblatt 7 Statistik", SpaceType.STUDY, "Statistik für Informatiker", 3,
                today.plusDays(6).atTime(23, 59), 90, TaskStatus.TODO,
                "Hypothesentests, sollte in einem Rutsch gehen.");

        Task refs = task(user, "Referat Software Engineering vorbereiten", SpaceType.STUDY,
                "Software Engineering", 4, today.plusDays(9).atTime(14, 15), 300, TaskStatus.TODO, """
                        20 Minuten über Architekturentscheidungen im Gruppenprojekt. \
                        Folien plus einmal laut durchsprechen.""");
        splittable(refs, 45, 90, 1);

        task(user, "Karteikarten Theoretische Informatik nachziehen", SpaceType.STUDY,
                "Theoretische Informatik", 2, today.plusDays(3).atTime(20, 0), 45, TaskStatus.TODO,
                "Kapitel Komplexitätsklassen ist im Deck noch nicht abgebildet.");

        task(user, "Mitschrift Datenbanksysteme aufräumen", SpaceType.STUDY, "Datenbanksysteme", 2,
                null, 60, TaskStatus.TODO,
                "Die Fotos vom Whiteboard in die Notiz übertragen, solange ich noch weiß, was drauf stand.");

        completed(user, "Übungsblatt 8 Analysis II abgeben", SpaceType.STUDY, "Analysis II", 4,
                today.minusDays(6), 150);
        completed(user, "Zwischenklausur Datenbanksysteme nachbereiten", SpaceType.STUDY,
                "Datenbanksysteme", 3, today.minusDays(11), 90);
        completed(user, "Statistik-Testat 1 vorbereiten", SpaceType.STUDY,
                "Statistik für Informatiker", 3, today.minusDays(9), 120);

        // --- Projekte ---
        Task expose = task(user, "Exposé-Gliederung entwerfen", SpaceType.PROJECTS, "Bachelorarbeit", 4,
                today.plusDays(12).atTime(18, 0), 240, TaskStatus.TODO,
                "Drei Kapitel skizzieren, dann mit Prof. Lehmann abstimmen.");
        expose.setProject(thesis);
        progress(expose, 60);
        splittable(expose, 60, 120, 1);

        Task betreuer = task(user, "Prof. Lehmann wegen Betreuung anschreiben", SpaceType.PROJECTS,
                "Bachelorarbeit", 5, today.plusDays(2).atTime(12, 0), 30, TaskStatus.TODO,
                "Kurze Mail mit zwei Themenvorschlägen und Terminvorschlag für die Sprechstunde.");
        betreuer.setProject(thesis);
        taskRepository.save(betreuer);

        Task literatur = task(user, "Literaturrecherche Themenfeld eingrenzen", SpaceType.PROJECTS,
                "Bachelorarbeit", 3, today.plusWeeks(3).atTime(18, 0), 360, TaskStatus.TODO,
                "Fünf aktuelle Paper lesen und in Zotero ablegen.");
        literatur.setProject(thesis);
        splittable(literatur, 60, 120, 1);

        // Die eine Aufgabe, die tatsächlich auf IN_PROGRESS steht - damit der Status im
        // Filter und in der Liste vorkommt. Der Scheduler übergeht sie (siehe progress()),
        // was hier nicht stört: an ihr wird gerade ohnehin gearbeitet.
        Task kalender = task(user, "Kalender-Wochenansicht neu bauen", SpaceType.PROJECTS,
                "Everything App", 4, today.plusDays(8).atTime(20, 0), 300, TaskStatus.IN_PROGRESS,
                "Überlappende Termine nebeneinander statt übereinander zeichnen.");
        kalender.setProject(app);
        progress(kalender, 120);
        splittable(kalender, 60, 120, 1);

        Task solver = task(user, "Scheduler auf vier Wochen Horizont ausweiten", SpaceType.PROJECTS,
                "Everything App", 3, today.plusWeeks(2).atTime(20, 0), 240, TaskStatus.TODO,
                "Laufzeit im Blick behalten — bei zwei Wochen braucht CP-SAT schon 6 Sekunden.");
        solver.setProject(app);
        splittable(solver, 60, 120, 1);

        Task tests = task(user, "Tests für den Rezept-Import nachziehen", SpaceType.PROJECTS,
                "Everything App", 2, today.plusDays(15).atTime(20, 0), 120, TaskStatus.TODO,
                "Der Parser hat drei Sonderfälle, für die es noch keinen Test gibt.");
        tests.setProject(app);
        taskRepository.save(tests);

        Task route = task(user, "Interrail-Route festlegen", SpaceType.PROJECTS, "Reise", 3,
                today.plusDays(10).atTime(19, 0), 120, TaskStatus.TODO,
                "Vorschlag: Wien – Ljubljana – Split – Sarajevo. Muss noch durch die Gruppe.");
        route.setProject(trip);
        taskRepository.save(route);

        Task hostels = task(user, "Unterkünfte für die erste Woche buchen", SpaceType.PROJECTS,
                "Reise", 4, today.plusDays(18).atTime(19, 0), 90, TaskStatus.TODO,
                "Erst nach der Routenentscheidung — sonst zweimal buchen.");
        hostels.setProject(trip);
        hostels.setNotBefore(today.plusDays(11).atTime(9, 0));
        taskRepository.save(hostels);

        Task regal = task(user, "Regal für die Nische bauen", SpaceType.PROJECTS, "Wohnen", 1,
                null, 240, TaskStatus.TODO,
                "Maße stehen, Holz fehlt. Liegt bewusst bis nach den Klausuren.");
        regal.setProject(flat);
        regal.setNotBefore(today.plusWeeks(7).atTime(9, 0));
        taskRepository.save(regal);

        Task kartons = completed(user, "Umzugskartons zurückgeben", SpaceType.PROJECTS, "Wohnen", 2,
                today.minusWeeks(9), 60);
        kartons.setProject(move);
        taskRepository.save(kartons);

        // --- Alltag ---
        task(user, "Nachmieter für das alte Zimmer suchen", SpaceType.TASKS, "Wohnen", 3,
                today.minusDays(2).atTime(18, 0), 60, TaskStatus.TODO,
                "Frist war gestern — Anzeige ist immer noch nicht online.");

        task(user, "Wäsche waschen", SpaceType.TASKS, "Haushalt", 2,
                today.plusDays(1).atTime(21, 0), 45, TaskStatus.TODO, null);

        task(user, "Bad putzen", SpaceType.TASKS, "Haushalt", 2,
                today.plusDays(3).atTime(21, 0), 40, TaskStatus.TODO,
                "Bin diese Woche im WG-Putzplan dran.");

        task(user, "Fahrrad zur Reparatur bringen", SpaceType.TASKS, "Alltag", 3,
                today.plusDays(5).atTime(17, 0), 45, TaskStatus.TODO,
                "Hinterrad eiert. Laden hat bis 18 Uhr offen.");

        task(user, "Geschenk für Lenas Geburtstag besorgen", SpaceType.TASKS, "Alltag", 4,
                today.plusDays(8).atTime(18, 0), 60, TaskStatus.TODO,
                "Sie hat den Bildband erwähnt, den sie in der Buchhandlung liegen sah.");

        task(user, "Personalausweis verlängern", SpaceType.TASKS, "Behörden", 3,
                today.plusWeeks(3).atTime(12, 0), 90, TaskStatus.TODO,
                "Termin beim Bürgerbüro online buchen — läuft in vier Monaten ab.");

        task(user, "Zimmerpflanzen umtopfen", SpaceType.TASKS, "Haushalt", 1,
                null, 40, TaskStatus.TODO, null);

        completed(user, "Rezept für die Bolognese aufschreiben", SpaceType.RECIPES, "Rezepte", 1,
                today.minusDays(6), 20);

        task(user, "Meal Prep für die Woche kochen", SpaceType.RECIPES, "Rezepte", 3,
                DemoDates.next(today, DayOfWeek.SUNDAY).atTime(18, 0), 120, TaskStatus.TODO,
                "Vier Portionen Falafel-Bowl. Einkauf steht schon auf der Liste.");

        task(user, "Einkaufsliste durchgehen und ergänzen", SpaceType.RECIPES, "Rezepte", 2,
                today.plusDays(2).atTime(18, 0), 20, TaskStatus.TODO, null);

        // --- Finanzen ---
        task(user, "Steuererklärung 2025 fertigstellen", SpaceType.FINANCE, "Finanzen", 4,
                today.plusWeeks(4).atTime(18, 0), 180, TaskStatus.TODO,
                "Werbungskosten für die Fahrten zum Werkstudentenjob nicht vergessen.");

        task(user, "Budget des laufenden Monats prüfen", SpaceType.FINANCE, "Finanzen", 2,
                today.plusDays(7).atTime(20, 0), 30, TaskStatus.TODO,
                "Restaurant liegt gefühlt schon über dem Limit.");

        task(user, "DAZN-Kündigung bestätigen lassen", SpaceType.FINANCE, "Finanzen", 2,
                today.plusDays(4).atTime(18, 0), 15, TaskStatus.TODO,
                "Kündigung ist raus, die schriftliche Bestätigung fehlt noch.");

        // --- Sport ---
        task(user, "Trainingsplan für den nächsten Block schreiben", SpaceType.SPORTS, "Training", 2,
                today.plusWeeks(2).atTime(19, 0), 60, TaskStatus.TODO,
                "Zwölf Wochen sind fast durch — danach eine Woche Deload.");

        task(user, "Laufschuhe ersetzen", SpaceType.SPORTS, "Training", 2,
                today.plusWeeks(3).atTime(18, 0), 60, TaskStatus.TODO,
                "Über 800 km drauf, die Dämpfung ist durch.");

        // Eine abgebrochene Aufgabe - ohne sie sieht der Status-Filter aus, als gäbe es nur drei Zustände.
        Task cancelled = task(user, "Zweitfach Wirtschaftsinformatik belegen", SpaceType.STUDY,
                "Studium", 2, null, 60, TaskStatus.CANCELLED,
                "Verworfen: passt nicht mehr in den Stundenplan.");
        cancelled.setCompletedAt(today.minusWeeks(4).atTime(21, 0));
        taskRepository.save(cancelled);
    }

    private Task task(User user, String title, SpaceType space, String category, int priority,
                      LocalDateTime deadline, int minutes, TaskStatus status, String description) {
        Task task = new Task();
        task.setUser(user);
        task.setTitle(title);
        task.setDescription(description);
        task.setSpaceType(space);
        task.setCategory(category);
        task.setPriority(priority);
        task.setDeadline(deadline);
        task.setEstimatedDurationMinutes(minutes);
        task.setStatus(status);
        return taskRepository.save(task);
    }

    /**
     * Schon angefangen, aber noch offen — der Solver plant nur die Restzeit ein.
     *
     * <p>Der Status bleibt bewusst TODO: {@code TaskRepository.findSchedulableTasks} liest
     * ausschließlich {@code status = 'TODO'}, eine auf IN_PROGRESS gesetzte Aufgabe fällt also
     * komplett aus der Planung. Für den Demo-Bestand heißt das: angefangene Arbeit wird über
     * {@code completedMinutes} abgebildet, nicht über den Status.
     */
    private void progress(Task task, int doneMinutes) {
        task.setCompletedMinutes(doneMinutes);
        taskRepository.save(task);
    }

    /** Große Brocken darf der Solver auf mehrere Sitzungen verteilen. */
    private void splittable(Task task, int minChunk, int maxChunk, int maxPerDay) {
        task.setSplittable(true);
        task.setMinChunkMinutes(minChunk);
        task.setMaxChunkMinutes(maxChunk);
        task.setMaxChunksPerDay(maxPerDay);
        taskRepository.save(task);
    }

    private Task completed(User user, String title, SpaceType space, String category,
                           int priority, LocalDate doneOn, int minutes) {
        Task task = new Task();
        task.setUser(user);
        task.setTitle(title);
        task.setSpaceType(space);
        task.setCategory(category);
        task.setPriority(priority);
        task.setEstimatedDurationMinutes(minutes);
        task.setCompletedMinutes(minutes);
        task.setStatus(TaskStatus.COMPLETED);
        task.setDeadline(doneOn.atTime(23, 59));
        task.setScheduledStartTime(doneOn.atTime(16, 0));
        task.setScheduledEndTime(doneOn.atTime(16, 0).plusMinutes(minutes));
        task.setCompletedAt(doneOn.atTime(16, 0).plusMinutes(minutes));
        return taskRepository.save(task);
    }

    // -------------------------------------------------------------- Gewohnheiten

    private void seedHabits(User user, LocalDate today) {
        Habit stretch = habit(user, "Morgens 10 Minuten dehnen", HabitFrequency.DAILY, null, 10,
                HabitWindow.MORNING, "#FF8A65", 3, "Gesundheit", today.minusWeeks(10),
                "Nach dem Aufstehen, vor dem Kaffee. Sonst passiert es nicht.");
        allDays(stretch);

        Habit meditate = habit(user, "Meditation", HabitFrequency.DAILY, null, 10,
                HabitWindow.MORNING, "#7986CB", 2, "Gesundheit", today.minusWeeks(6),
                "Zehn Minuten geführte Meditation.");
        allDays(meditate);

        Habit vocab = habit(user, "Spanisch-Vokabeln", HabitFrequency.DAILY, null, 15,
                HabitWindow.ANYTIME, "#FFB74D", 3, "Lernen", today.minusWeeks(12),
                "Duolingo-Streak halten — 15 Minuten reichen.");
        allDays(vocab);

        Habit read = habit(user, "Vor dem Schlafen lesen", HabitFrequency.DAILY, null, 25,
                HabitWindow.EVENING, "#4DB6AC", 2, "Freizeit", today.minusWeeks(14),
                "Kein Bildschirm nach 22:30.");
        allDays(read);

        Habit run = habit(user, "Laufen gehen", HabitFrequency.CUSTOM, 3, 45,
                HabitWindow.ANYTIME, "#81C784", 4, "Sport", today.minusWeeks(8),
                "Dreimal die Woche, Tag egal — Hauptsache nicht direkt nach dem Beintraining.");
        run.setTuesday(true);
        run.setThursday(true);
        run.setSaturday(true);
        run.setSunday(true);
        habitRepository.save(run);

        Habit tidy = habit(user, "Schreibtisch aufräumen", HabitFrequency.WEEKLY, null, 20,
                HabitWindow.EVENING, "#A1887F", 2, "Haushalt", today.minusWeeks(9),
                "Freitagabend, damit die Woche sauber endet.");
        tidy.setFriday(true);
        habitRepository.save(tidy);

        Habit review = habit(user, "Wochenrückblick", HabitFrequency.WEEKLY, null, 30,
                HabitWindow.EVENING, "#9575CD", 3, "Planung", today.minusWeeks(11),
                "Was lief, was nicht, was kommt. Sonntags.");
        review.setSunday(true);
        habitRepository.save(review);

        Habit call = habit(user, "Zuhause anrufen", HabitFrequency.WEEKLY, null, 30,
                HabitWindow.EVENING, "#F06292", 3, "Familie", today.minusWeeks(16), null);
        call.setSunday(true);
        habitRepository.save(call);

        Habit water = habit(user, "2 Liter Wasser trinken", HabitFrequency.DAILY, null, 5,
                HabitWindow.ANYTIME, "#4FC3F7", 1, "Gesundheit", today.minusWeeks(5),
                "Kurz genug, dass es überall dazwischenpasst — daran hängt nur der Haken.");
        allDays(water);

        // Erledigungen der letzten Wochen. Die Quoten sind absichtlich unterschiedlich hoch -
        // eine Wand aus lauter erledigten Tagen sagt über die Streak-Anzeige nichts aus.
        completions(stretch, today, 70, 6);
        completions(meditate, today, 42, 4);
        completions(vocab, today, 84, 5);
        completions(read, today, 60, 9);
        completions(run, today, 56, 3);
        completions(tidy, today, 63, 2);
        completions(review, today, 77, 2);
        completions(call, today, 70, 3);
        completions(water, today, 35, 3);
    }

    private Habit habit(User user, String name, HabitFrequency frequency, Integer timesPerWeek,
                        int minutes, HabitWindow window, String color, int priority,
                        String category, LocalDate startDate, String description) {
        Habit habit = new Habit();
        habit.setUser(user);
        habit.setName(name);
        habit.setDescription(description);
        habit.setFrequency(frequency);
        habit.setTimesPerWeek(timesPerWeek);
        habit.setDurationMinutes(minutes);
        habit.setIdealWindow(window);
        habit.setColor(color);
        habit.setPriority(priority);
        habit.setCategory(category);
        habit.setStartDate(startDate);
        return habitRepository.save(habit);
    }

    private void allDays(Habit habit) {
        habit.setMonday(true);
        habit.setTuesday(true);
        habit.setWednesday(true);
        habit.setThursday(true);
        habit.setFriday(true);
        habit.setSaturday(true);
        habit.setSunday(true);
        habitRepository.save(habit);
    }

    /**
     * Trägt die letzten {@code days} Tage ein und lässt dabei jeden n-ten aus, damit eine
     * abgerissene Serie entsteht. {@code currentStreak} und {@code longestStreak} werden aus den
     * tatsächlich eingetragenen Tagen berechnet — ein frei gewählter Wert würde der Liste
     * darunter widersprechen.
     */
    private void completions(Habit habit, LocalDate today, int days, int skipEvery) {
        int currentStreak = 0;
        int longestStreak = 0;
        int running = 0;
        boolean streakStillRunning = true;

        for (int back = 1; back <= days; back++) {
            LocalDate date = today.minusDays(back);
            boolean done = back % skipEvery != 0;

            // Wochentags-Gewohnheiten nur an ihren Tagen.
            if (!isScheduledOn(habit, date)) {
                continue;
            }

            if (done) {
                HabitCompletion completion = new HabitCompletion();
                completion.setHabit(habit);
                completion.setCompletionDate(date);
                completion.setCompletedAt(date.atTime(20, 0));
                completion.setWasSuccessful(true);
                habitCompletionRepository.save(completion);

                running++;
                if (streakStillRunning) currentStreak++;
            } else {
                streakStillRunning = false;
                longestStreak = Math.max(longestStreak, running);
                running = 0;
            }
        }
        habit.setCurrentStreak(currentStreak);
        habit.setLongestStreak(Math.max(longestStreak, currentStreak));
        habitRepository.save(habit);
    }

    private boolean isScheduledOn(Habit habit, LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> Boolean.TRUE.equals(habit.getMonday());
            case TUESDAY -> Boolean.TRUE.equals(habit.getTuesday());
            case WEDNESDAY -> Boolean.TRUE.equals(habit.getWednesday());
            case THURSDAY -> Boolean.TRUE.equals(habit.getThursday());
            case FRIDAY -> Boolean.TRUE.equals(habit.getFriday());
            case SATURDAY -> Boolean.TRUE.equals(habit.getSaturday());
            case SUNDAY -> Boolean.TRUE.equals(habit.getSunday());
        };
    }

    // ------------------------------------------------------------ Feste Termine

    /**
     * Was von außen kommt und den Kalender blockiert: der Werkstudentenjob, das wöchentliche
     * Standup des Projektteams und eine Reihe von Einzelterminen über gut zwei Monate.
     */
    private void seedFixedEvents(User user, LocalDate today) {
        LocalDate from = DemoDates.monday(today.minusWeeks(3));
        LocalDate to = today.plusWeeks(6);

        for (LocalDate week = from; week.isBefore(to); week = week.plusWeeks(1)) {
            fixed(user, "Werkstudent — Netzwerk Solutions", EventType.FIXED,
                    DemoDates.next(week, DayOfWeek.MONDAY).atTime(16, 30), 240,
                    "Büro Ost, 3. OG", "#546E7A",
                    "Feste Schicht. Nicht verschiebbar, deshalb plant der Scheduler drumherum.");
            fixed(user, "Werkstudent — Netzwerk Solutions", EventType.FIXED,
                    DemoDates.next(week, DayOfWeek.THURSDAY).atTime(16, 30), 240,
                    "Büro Ost, 3. OG", "#546E7A", null);
            fixed(user, "Standup Mensa-Planer (Projektteam)", EventType.FIXED,
                    DemoDates.next(week, DayOfWeek.TUESDAY).atTime(18, 0), 30,
                    "Online", "#FF9F1C", "Kurzer Abgleich mit Lena, Jonas und Miriam.");
        }

        // --- Vergangenes ---
        fixed(user, "Zahnarzt — Prophylaxe", EventType.OTHER,
                today.minusWeeks(3).atTime(9, 30), 60, "Praxis Dr. Ritter", "#26A69A", null);
        fixed(user, "Zwischenklausur Datenbanksysteme", EventType.OTHER,
                today.minusWeeks(3).plusDays(2).atTime(10, 0), 90, "HS 3", "#2EC4B6",
                "2,0 — Normalformen saßen, Optimierung nicht.");
        fixed(user, "Grillabend bei Jonas", EventType.OTHER,
                today.minusWeeks(2).plusDays(5).atTime(18, 0), 300, "Bei Jonas", "#FFB74D", null);
        fixed(user, "Sprechstunde Prof. Baumann", EventType.OTHER,
                today.minusWeeks(1).plusDays(1).atTime(13, 0), 30, "Raum 4.12", "#E71D36",
                "Probeklausur besprochen. Empfehlung: Berechenbarkeit zuerst.");

        // --- Kommendes ---
        fixed(user, "Wohnungsbesichtigung (Nachmieter)", EventType.OTHER,
                today.plusDays(3).atTime(17, 0), 45, "Alte Wohnung", "#A1887F",
                "Zwei Interessenten hintereinander.");
        fixed(user, "Hausarzt — Blutbild", EventType.OTHER,
                today.plusDays(6).atTime(8, 30), 45, "Praxis Dr. Berg", "#26A69A",
                "Nüchtern erscheinen.");
        fixed(user, "Vorstellungsgespräch Praktikum", EventType.OTHER,
                today.plusDays(8).atTime(11, 0), 90, "Innenstadt, Königsallee 12", "#5B8DEF",
                "Pflichtpraktikum ab September. Unterlagen am Vorabend nochmal durchgehen.");
        fixed(user, "Lenas Geburtstag", EventType.OTHER,
                today.plusDays(9).atTime(19, 0), 300, "Bei Lena", "#F06292",
                "Geschenk vorher besorgen — steht als Aufgabe.");
        fixed(user, "Abgabe Sprint 2 — Mensa-Planer", EventType.OTHER,
                today.plusDays(12).atTime(23, 0), 60, "Online", "#FF9F1C",
                "Harte Frist, keine Verlängerung.");
        fixed(user, "Friseur", EventType.OTHER,
                today.plusDays(11).atTime(16, 0), 45, "Salon Vier", "#BDBDBD", null);
        fixed(user, "Konzert im Zakk", EventType.OTHER,
                today.plusDays(16).atTime(20, 0), 240, "Zakk, Fichtenstraße", "#AB47BC", null);
        fixed(user, "Semesterparty Fachschaft", EventType.OTHER,
                today.plusDays(19).atTime(21, 0), 300, "Campus Nord", "#9B5DE5", null);
        fixed(user, "Wochenende bei den Eltern", EventType.OTHER,
                today.plusDays(23).atTime(10, 0), 600, "Zuhause", "#8D6E63",
                "Hinfahrt Freitagabend, Rückfahrt Sonntag.");
        fixed(user, "Klausur Analysis II", EventType.OTHER,
                today.plusWeeks(5).atTime(9, 0), 120, "Sporthalle West", "#5B8DEF",
                "Zwei Stunden, keine Hilfsmittel.");
        fixed(user, "Klausur Theoretische Informatik", EventType.OTHER,
                today.plusWeeks(6).atTime(14, 0), 120, "HS 4", "#E71D36", null);
    }

    private void fixed(User user, String title, EventType type, LocalDateTime start,
                       int minutes, String location, String color, String notes) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(user);
        event.setTitle(title);
        event.setEventType(type);
        event.setStartTime(start);
        event.setEndTime(start.plusMinutes(minutes));
        event.setLocation(location);
        event.setColor(color);
        event.setNotes(notes);
        event.setIsFixed(true);
        calendarEventRepository.save(event);
    }
}
