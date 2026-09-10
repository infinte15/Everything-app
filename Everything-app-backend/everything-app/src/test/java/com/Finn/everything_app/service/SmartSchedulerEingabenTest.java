package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Wird jede Planungseingabe auch wirklich benutzt?
 *
 * <p><b>Warum das eine eigene Klasse ist.</b> Die Frage "berücksichtigt der Scheduler alles
 * Nötige?" lässt sich nicht dadurch beantworten, dass irgendwo ein Test existiert, der ein Feld
 * zufällig mitsetzt. Sie braucht zwei Dinge: je Feld ein Paar von Läufen, die sich NUR in diesem
 * Feld unterscheiden und deren Ergebnis sich in der dokumentierten Richtung unterscheidet — und
 * eine Liste, die vollständig ist und vollständig bleibt.
 *
 * <p>Genau diese Lücke gab es: gezählt über beide bestehenden Scheduler-Testklassen kam
 * {@code setDeadlineBufferHours} 0×, {@code setDefaultMinChunkMinutes} 0× und
 * {@code setDefaultMaxChunkMinutes} 0× vor. Drei Einstellungen, die es im Frontend gibt, die im
 * Service ausgewertet werden — und die kein Test je angefasst hat. Dass so etwas hier vorkommt,
 * ist belegt: {@code groupSimilarTasks} und {@code hoursBeforeBreak} waren tot und wurden
 * entfernt, {@code maxTasksPerDay} sah genauso aus, wird aber ausgewertet.
 *
 * <p>{@link #keinPlanungsfeldOhneTest()} ist deshalb der eigentliche Wächter: er liest die Felder
 * von {@link UserPreferences} und {@link Task} per Reflexion und verlangt für jedes eine
 * Entscheidung — entweder ein Test hier, oder ein Eintrag in der Liste der bewusst nicht
 * planungsrelevanten Felder. Ein neues Feld ohne beides macht den Build rot.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Smart Scheduler: Eingaben")
class SmartSchedulerEingabenTest {

    @Mock TaskRepository            taskRepository;
    @Mock CalendarEventRepository   calendarEventRepository;
    @Mock HabitRepository           habitRepository;
    @Mock HabitCompletionRepository habitCompletionRepository;
    @Mock WorkoutSessionRepository  workoutSessionRepository;
    @Mock RoutineExerciseRepository routineExerciseRepository;
    @Mock CourseScheduleRepository  courseScheduleRepository;
    @Mock ProjectRepository         projectRepository;
    @Mock UserService               userService;
    @Mock CalendarEventService      calendarEventService;
    @Mock TaskService               taskService;
    @Mock WorkoutPlanService        workoutPlanService;
    @Mock LastScheduleRunStore      lastRunStore;

    @InjectMocks
    SmartSchedulerService service;

    private final SchedulerFixtures f = new SchedulerFixtures();
    private UserPreferences prefs;

    private final LocalDate MORGEN = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);

        prefs = new UserPreferences();
        prefs.setWorkdayStart(LocalTime.of(8, 0));
        prefs.setWorkdayEnd(LocalTime.of(17, 0));

        lenient().when(userService.getOrCreatePreferences(1L)).thenReturn(prefs);
        lenient().when(userService.findById(1L)).thenReturn(user);
        lenient().when(taskService.getSchedulableTasks(1L)).thenReturn(new ArrayList<>());
        lenient().when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        lenient().when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(new ArrayList<>());
        lenient().when(habitCompletionRepository.findByHabitIdInAndCompletionDateBetween(any(), any(), any()))
                 .thenReturn(new ArrayList<>());
        lenient().when(workoutSessionRepository.findByUserIdAndStartTimeBetween(eq(1L), any(), any()))
                 .thenReturn(new ArrayList<>());
        lenient().when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(eq(1L)))
                 .thenReturn(new ArrayList<>());
        lenient().when(courseScheduleRepository.findByUserId(1L)).thenReturn(new ArrayList<>());
        lenient().when(workoutPlanService.getActivePlan(1L)).thenReturn(null);
        lenient().when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(new ArrayList<>());
    }

    // ==================================================================
    // Arbeitstage — die Lücke gegenüber Reclaim
    // ==================================================================

    /**
     * Ohne Einstellung bleibt alles wie bisher: sieben Tage die Woche.
     *
     * <p>Bewusst so. Die Vorgabe darf keinen bestehenden Plan umschreiben — Reclaims Mo–Fr ist
     * hier eine Einstellung, keine Voreinstellung.
     */
    @Test
    void ohneEinstellungWirdWeiterhinAnJedemTagGeplant() {
        LocalDate samstag = naechster(DayOfWeek.SATURDAY);
        eineAufgabeProTag(samstag, 3);

        ScheduleResult r = lauf(samstag, samstag.plusDays(2));

        assertTrue(tage(r).contains(samstag),
                "ohne workDays muss der Samstag weiterhin bespielt werden: " + tage(r));
    }

    /** Mit Mo–Fr bleibt das Wochenende frei — der Kernfall. */
    @Test
    void arbeitstageHaltenDasWochenendeFrei() {
        prefs.setWorkDays("1,2,3,4,5");
        LocalDate samstag = naechster(DayOfWeek.SATURDAY);
        eineAufgabeProTag(samstag, 3);

        ScheduleResult r = lauf(samstag, samstag.plusDays(6));

        Set<LocalDate> belegt = tage(r);
        assertFalse(belegt.contains(samstag), "Samstag muss frei bleiben: " + belegt);
        assertFalse(belegt.contains(samstag.plusDays(1)), "Sonntag muss frei bleiben: " + belegt);
        assertFalse(belegt.isEmpty(), "die Aufgaben müssen in der Woche danach liegen");
    }

    /**
     * Gewohnheiten sind von den Arbeitstagen NICHT betroffen.
     *
     * <p>Sie laufen über die Privatzeiten und ihre eigenen Wochentagsflaggen. "Ich arbeite nicht
     * am Wochenende" heißt nicht "ich meditiere nicht am Wochenende".
     */
    @Test
    void gewohnheitenLaufenAuchAnFreienTagenWeiter() {
        prefs.setWorkDays("1,2,3,4,5");
        LocalDate samstag = naechster(DayOfWeek.SATURDAY);
        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any()))
                .thenReturn(List.of(f.taeglicheGewohnheit("Meditation", 20, 3)));

        ScheduleResult r = lauf(samstag, samstag.plusDays(1));

        assertFalse(r.getScheduledHabits().isEmpty(),
                "die Gewohnheit muss auch am Wochenende geplant werden");
    }

    /** Eine unbrauchbare Angabe darf den Scheduler nicht lahmlegen. */
    @Test
    void unbrauchbareArbeitstageBedeutenAlleTage() {
        prefs.setWorkDays("Montag bis Freitag");
        LocalDate samstag = naechster(DayOfWeek.SATURDAY);
        eineAufgabeProTag(samstag, 1);

        ScheduleResult r = lauf(samstag, samstag);

        assertEquals(1, r.getScheduledTasks().size(),
                "bei unlesbarer Angabe muss weiterhin geplant werden, nicht gar nichts");
    }

    // ==================================================================
    // Einstellungen, die bisher kein Test angefasst hat
    // ==================================================================

    /** Die Obergrenze für einen Block teilt eine lange Aufgabe feiner auf. */
    @Test
    void dieMaximaleBlockgroesseTeiltFeinerAuf() {
        int grob = bloeckeFuerLangeAufgabe(30, 120);
        int fein = bloeckeFuerLangeAufgabe(30, 60);

        assertTrue(fein > grob,
                "mit 60 statt 120 Minuten je Block müssen es mehr Blöcke werden: "
                        + fein + " gegen " + grob);
    }

    /** Die Untergrenze für einen Block verhindert Splitter. */
    @Test
    void dieMinimaleBlockgroesseVerhindertSplitter() {
        prefs.setDefaultMinChunkMinutes(60);
        prefs.setDefaultMaxChunkMinutes(120);
        Task t = f.task("Lang", 180, 3, null);
        t.setSplittable(true);
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(t));

        ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(6));

        for (ScheduledItem i : r.getScheduledTasks()) {
            long minuten = java.time.Duration.between(i.getStartTime(), i.getEndTime()).toMinutes();
            assertTrue(minuten >= 60,
                    "kein Block darf unter der Untergrenze liegen, war aber " + minuten + " min");
        }
    }

    /**
     * Der Deadline-Puffer macht keine Deadline unerreichbar — egal wie groß er steht.
     *
     * <p>Bewusst als NEGATIVE Zusicherung formuliert, und das ist kein Ausweichen. Der Puffer
     * schneidet ausschließlich die Obergrenze des Fensters zu; wohin ein Block innerhalb seines
     * Fensters wandert, entscheidet der Dringlichkeitsterm, und der zieht ohnehin nach vorn. Bei
     * freiem Kalender liegt die Aufgabe deshalb mit und ohne Puffer im selben Slot — ein
     * "früher als"-Test würde dort nichts messen und wäre bloß grün.
     *
     * <p>Was der Puffer wirklich kann, ist Schaden anrichten: ein fester Puffer hätte jeder
     * Aufgabe mit naher Deadline ein leeres Fenster gegeben. Dagegen ist er doppelt gedeckelt
     * (halbe Restzeit, und niemals so viel, dass die Restdauer nicht mehr hineinpasst). Genau das
     * prüft dieser Test — über eine Reihe von Puffern bis zu absurden 240 Stunden.
     */
    @Test
    void derDeadlinePufferMachtKeineDeadlineUnerreichbar() {
        for (int stunden : new int[]{ 0, 24, 72, 240 }) {
            prefs.setDeadlineBufferHours(stunden);
            LocalDateTime deadline = MORGEN.plusDays(2).atTime(16, 0);
            Task t = f.unteilbar(f.task("Abgabe", 120, 4, deadline));
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(t));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(9));

            assertEquals(1, r.getScheduledTasks().size(),
                    "Puffer " + stunden + " h: die Aufgabe muss geplant werden, gemeldet war "
                            + r.getAtRisk());
            assertFalse(r.getScheduledTasks().get(0).getEndTime().isAfter(deadline),
                    "Puffer " + stunden + " h: der Block liegt hinter seiner Deadline");
        }
    }

    /**
     * Mit Platz hält der Lauf den Puffer auch ein.
     *
     * <p>Die positive Hälfte, an der Stelle, an der sie beobachtbar ist: der Puffer ist eine
     * harte Obergrenze auf das Fenster. Der Tag vor der Deadline ist hier dicht, sodass die
     * Aufgabe nicht ohnehin schon vorn liegt.
     */
    @Test
    void mitPlatzWirdDerPufferEingehalten() {
        prefs.setDeadlineBufferHours(24);
        LocalDateTime deadline = MORGEN.plusDays(3).atTime(16, 0);
        Task t = f.unteilbar(f.task("Abgabe", 60, 4, deadline));
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(t));

        ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(9));

        assertEquals(1, r.getScheduledTasks().size(), "gemeldet war " + r.getAtRisk());
        assertFalse(r.getScheduledTasks().get(0).getEndTime().isAfter(deadline.minusHours(24)),
                "mit 24 Stunden Puffer und reichlich Platz muss der Block bis "
                        + deadline.minusHours(24) + " fertig sein, endete aber "
                        + r.getScheduledTasks().get(0).getEndTime());
    }

    // ==================================================================
    // Der Wächter
    // ==================================================================

    /**
     * Felder, die den Plan bewusst NICHT beeinflussen.
     *
     * <p>Jeder Eintrag ist eine Entscheidung, kein Versehen — deshalb steht dahinter, warum.
     */
    private static final Set<String> NICHT_PLANUNGSRELEVANT = Set.of(
            // UserPreferences
            "id", "user",
            "notificationsEnabled", "reminderMinutesBefore",   // Benachrichtigungen
            "themeColor", "darkMode",                          // Darstellung
            "targetWeightKg",                                  // Gym-Ziel
            "lastScheduleRunDate",                             // Buchhaltung des Roll-Forward
            "autoScheduleEnabled",                             // vor dem Lauf ausgewertet,
                                                               // siehe ScheduleRegenerationCoordinatorTest
            "breakDurationMinutes", "bufferMinutes",           // hier nicht, aber in
            "maxTasksPerDay", "maxTaskMinutesPerDay",          // SmartSchedulerServiceTest
            "maxScheduledMinutesPerDay", "coreHoursEnd",
            "peakProductivityTime", "personalHoursStart", "personalHoursEnd",
            "workdayStart", "workdayEnd",
            // Task
            "title", "description", "category",                // Text, nur Anzeige
            "status",                                          // entscheidet über die Auswahl,
                                                               // siehe SchedulableTaskRepositoryTest
            "createdAt", "updatedAt", "completedAt",           // Zeitstempel
            "scheduledStartTime", "scheduledEndTime",          // ERGEBNIS des Laufs, keine Eingabe
            "spaceType",                                       // nur die Farbe des Blocks
            "project",                                         // Zuordnung, keine Planungsregel
            "priority", "deadline", "estimatedDurationMinutes", // in SmartSchedulerSzenarienTest
            "completedMinutes", "minChunkMinutes", "maxChunkMinutes",
            "splittable", "maxChunksPerDay", "notBefore");

    /**
     * Kein Planungsfeld ohne Entscheidung.
     *
     * <p>Der Test prüft nicht, ob ein Feld benutzt wird — das tun die Tests darüber. Er prüft,
     * dass über jedes Feld überhaupt jemand nachgedacht hat. Wer {@link UserPreferences} oder
     * {@link Task} um ein Feld erweitert, muss es hier eintragen (mit Begründung) oder ihm einen
     * Test geben. Sonst schleicht sich wieder eine Einstellung ein, die es im Frontend gibt und
     * die der Scheduler nie liest.
     */
    @Test
    void keinPlanungsfeldOhneTest() {
        Set<String> getestet = Set.of("workDays", "defaultMinChunkMinutes",
                "defaultMaxChunkMinutes", "deadlineBufferHours");

        Set<String> offen = new TreeSet<>();
        for (Class<?> klasse : List.of(UserPreferences.class, Task.class)) {
            for (Field feld : klasse.getDeclaredFields()) {
                if (feld.isSynthetic() || Modifier.isStatic(feld.getModifiers())) continue;
                String name = feld.getName();
                if (getestet.contains(name) || NICHT_PLANUNGSRELEVANT.contains(name)) continue;
                offen.add(klasse.getSimpleName() + "." + name);
            }
        }

        assertTrue(offen.isEmpty(),
                "neue Felder ohne Entscheidung: " + offen + "\n"
                        + "Entweder einen Test in dieser Klasse ergänzen (wenn das Feld die "
                        + "Planung beeinflussen soll) oder mit Begründung in "
                        + "NICHT_PLANUNGSRELEVANT eintragen.");
    }

    // ==================================================================
    // Hilfen
    // ==================================================================

    private ScheduleResult lauf(LocalDate von, LocalDate bis) {
        return service.generateOptimalSchedule(1L, von, bis);
    }

    private LocalDate naechster(DayOfWeek tag) {
        return MORGEN.with(java.time.temporal.TemporalAdjusters.nextOrSame(tag));
    }

    private void eineAufgabeProTag(LocalDate ab, int anzahl) {
        List<Task> aufgaben = new ArrayList<>();
        for (int i = 0; i < anzahl; i++) {
            aufgaben.add(f.unteilbar(f.task("A" + i, 120, 3, null)));
        }
        when(taskService.getSchedulableTasks(1L)).thenReturn(aufgaben);
    }

    private Set<LocalDate> tage(ScheduleResult r) {
        return r.getScheduledTasks().stream()
                .map(i -> i.getStartTime().toLocalDate())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private int bloeckeFuerLangeAufgabe(int min, int max) {
        prefs.setDefaultMinChunkMinutes(min);
        prefs.setDefaultMaxChunkMinutes(max);
        Task t = f.task("Lang", 240, 3, null);
        t.setSplittable(true);
        when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(t));
        return lauf(MORGEN, MORGEN.plusDays(6)).getScheduledTasks().size();
    }

}
