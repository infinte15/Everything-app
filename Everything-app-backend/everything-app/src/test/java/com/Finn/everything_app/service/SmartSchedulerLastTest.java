package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Laufzeitprofil des Schedulers — getaggt, läuft NICHT bei jedem {@code mvn test} mit.
 *
 * <pre>{@code ./mvnw test -Dtest=SmartSchedulerLastTest -Dsurefire.excludedGroups=}</pre>
 *
 * <p><b>Warum getrennt.</b> Eine scharfe Zeitschranke in der normalen Suite misst die Auslastung
 * der Maschine, nicht den Scheduler. Hier laufen deshalb mehrere Läufe je Bestandsgröße, und
 * ausgewertet wird die <i>Verteilung</i> (p50/p95) statt eines Einzelwerts. Die normale Suite
 * behält nur eine grobe Größenordnungswache
 * ({@code SmartSchedulerQualitaetTest#dieWartezeitBleibtInDerGroessenordnungDesBudgets}).
 *
 * <p><b>Diese Klasse ist auch ein Messinstrument, nicht nur ein Wächter.</b>
 * {@link #aufnahmegrenzeGegenVollesModell()} vergleicht Einstellungen gegeneinander und schreibt
 * eine Tabelle ins Log — so wurde entschieden, wo {@code scheduler.max-task-chunks} stehen muss.
 * Wer daran wieder dreht, sollte die Tabelle erneut lesen statt zu schätzen.
 */
@Tag("benchmark")
@ExtendWith(MockitoExtension.class)
@DisplayName("Smart Scheduler: Laufzeit")
class SmartSchedulerLastTest {

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
    private final LocalDate ENDE   = MORGEN.plusDays(30);

    private static final double BUDGET = SchedulerFixtures.produktionsBudgetSekunden();
    private static final int[] GROESSEN = { 20, 50, 80, 120 };
    private static final int LAEUFE = 7;

    /**
     * Obergrenze für p95 je Bestandsgröße, in Vielfachen des Zeitbudgets.
     *
     * <p>Großzügig: der Lauf besteht aus mehr als dem Hauptmodell (Vorlauf, zwei Nachläufe,
     * Verdrängung), und jeder dieser Pässe bringt sein eigenes kleines Budget mit.
     */
    private static final double P95_FAKTOR = 4.0;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);

        prefs = new UserPreferences();
        prefs.setWorkdayStart(LocalTime.of(8, 0));
        prefs.setWorkdayEnd(LocalTime.of(18, 0));

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

    /**
     * Das Laufzeitprofil: was der Nutzer nach einer Änderung tatsächlich abwartet.
     */
    @Test
    void laufzeitProfil() {
        StringBuilder tabelle = new StringBuilder("\n=== Laufzeitprofil (Budget "
                + BUDGET + "s, " + LAEUFE + " Läufe je Größe) ===\n"
                + String.format("%6s %6s %8s %8s %8s %8s %7s %7s %s%n",
                        "n", "interv", "p50 ms", "p95 ms", "p50 p1", "p50 p2", "geplant", "atRisk", "status"));

        for (int n : GROESSEN) {
            List<SchedLog.Lauf> laeufe = new ArrayList<>();
            List<Integer> geplant = new ArrayList<>();
            List<Integer> gemeldet = new ArrayList<>();

            bestandStellen(n);
            service.generateOptimalSchedule(1L, MORGEN, ENDE);   // Warmlauf, nicht gewertet

            for (int i = 0; i < LAEUFE; i++) {
                bestandStellen(n);
                try (SchedLog log = SchedLog.anhaengen()) {
                    ScheduleResult r = service.generateOptimalSchedule(1L, MORGEN, ENDE);
                    laeufe.add(log.letzter());
                    geplant.add((int) r.getScheduledTasks().stream()
                            .map(x -> x.getTask().getId()).distinct().count());
                    gemeldet.add(r.getAtRisk().size());
                }
            }

            Map<String, Long> status = laeufe.stream().collect(Collectors.groupingBy(
                    SchedLog.Lauf::status, LinkedHashMap::new, Collectors.counting()));

            tabelle.append(String.format("%6d %6d %8d %8d %8d %8d %7d %7d %s%n",
                    n,
                    laeufe.get(0).intervalle(),
                    perzentil(laeufe.stream().map(SchedLog.Lauf::totalMs).toList(), 50),
                    perzentil(laeufe.stream().map(SchedLog.Lauf::totalMs).toList(), 95),
                    perzentil(laeufe.stream().map(SchedLog.Lauf::p1Ms).toList(), 50),
                    perzentil(laeufe.stream().map(SchedLog.Lauf::p2Ms).toList(), 50),
                    (int) perzentil(geplant.stream().map(Integer::longValue).toList(), 50),
                    (int) perzentil(gemeldet.stream().map(Integer::longValue).toList(), 50),
                    status));

            long p95 = perzentil(laeufe.stream().map(SchedLog.Lauf::totalMs).toList(), 95);
            assertTrue(p95 < BUDGET * 1000 * P95_FAKTOR,
                    n + " Aufgaben: p95 = " + p95 + " ms bei einem Budget von " + BUDGET + " s");
            for (SchedLog.Lauf l : laeufe) {
                assertNotEquals("UNKNOWN", l.status(),
                        n + " Aufgaben: ein Lauf endete auf UNKNOWN — der Kalender bleibt dann stehen");
            }
        }
        System.out.println(tabelle);
    }

    /**
     * Wie viel die Aufnahmegrenze wirklich kostet — und ob sie sich überhaupt noch lohnt.
     *
     * <p>Die Grenze wurde eingeführt, weil Phase 1 bei zu vielen Chunks nicht mehr zum Suchen kam.
     * Seither ist der Phase-1-Schnappschuss dazugekommen: eine Phase 1, die ihren Deckel
     * ausschöpft, liefert trotzdem eine gültige (nur nicht bewiesen optimale) Belegung. Damit ist
     * die Frage offen, ob "39 Chunks gar nicht erst anbieten" noch besser ist als "alle anbieten
     * und nach 1,5 s nehmen, was da ist".
     *
     * <p>Der Test entscheidet nichts, er misst nur und schreibt die Tabelle ins Log.
     */
    @Test
    void aufnahmegrenzeGegenVollesModell() {
        StringBuilder tabelle = new StringBuilder("\n=== Aufnahmegrenze (Budget " + BUDGET + "s) ===\n"
                + String.format("%6s %10s %8s %8s %8s %9s %7s %9s %s%n",
                        "n", "grenze", "ms", "p1 ms", "p2 ms", "geplant", "atRisk", "drop", "p1Status"));

        for (int n : GROESSEN) {
            for (int grenze : new int[]{ 300, 600, 900, 1200, 1800, 100_000 }) {
                bestandStellen(n);
                setzeFeld("maxTaskDayVars", grenze);
                service.generateOptimalSchedule(1L, MORGEN, ENDE);   // Warmlauf

                bestandStellen(n);
                try (SchedLog log = SchedLog.anhaengen()) {
                    ScheduleResult r = service.generateOptimalSchedule(1L, MORGEN, ENDE);
                    SchedLog.Lauf l = log.letzter();
                    tabelle.append(String.format("%6d %10s %8d %8d %8d %9d %7d %9d %s%n",
                            n, grenze == 100_000 ? "ohne" : String.valueOf(grenze),
                            l.totalMs(), l.p1Ms(), l.p2Ms(),
                            r.getScheduledTasks().stream().map(x -> x.getTask().getId()).distinct().count(),
                            r.getAtRisk().size(), l.drop(), l.p1Status()));
                }
            }
        }
        setzeFeld("maxTaskDayVars", 1200);
        System.out.println(tabelle);

    }

    /**
     * Kommt der Kalender zur Ruhe, wenn sich an der Eingabe nichts ändert?
     *
     * <p>Das ist die Frage hinter "läuft es so glatt wie Reclaim". Ein Plan darf sich ändern, wenn
     * sich etwas geändert hat — er darf sich aber nicht bei jedem Lauf neu würfeln. Dafür gibt es
     * den Stabilitätsanker und die Totzone {@code W_MOVE_FIXED}.
     *
     * <p><b>Gemessen wird die Schleife des Betriebs</b>: jeder Lauf bekommt den Plan des VORIGEN
     * Laufs als {@code previousScheduledEvents} zurück. Alle Läufe gegen denselben kalten Erstplan
     * zu vergleichen misst etwas anderes — dann steckt darin vor allem der einmalige Übergang von
     * "kein Vorzustand" zu "Vorzustand da", und der darf den Plan sehr wohl verändern.
     *
     * <p><b>Befund (10.09.2026), der Grund für diesen Test.</b> Unterhalb von etwa 20 Aufgaben
     * kommt der Plan nach ein bis zwei Läufen zur Ruhe. Darüber nicht mehr — dort liegen bei
     * JEDEM Lauf rund 85 % der Blöcke woanders als beim Lauf davor, ohne dass sich an der Eingabe
     * etwas geändert hätte:
     *
     * <pre>
     *   Aufgaben   bewegte Blöcke je Lauf        davon
     *   10           17,  7,  1,  2,  1 / 260    Aufgaben 0, Gewohnheiten 1
     *   20           28, 25, 26,  7, 12 / 270    Aufgaben 10, Gewohnheiten 2
     *   50          236,253,220,255,226 / 287    Aufgaben 48, Gewohnheiten 178
     *   80          235,248,238,241,248 / 261    Aufgaben 86, Gewohnheiten 162
     * </pre>
     *
     * <p><b>Was die Ursache NICHT ist</b> (jeweils gemessen): das Zeitbudget — mit 20 s statt
     * 1,5 s bleibt die Zahl gleich; die Aufnahmegrenze — über 900/1200/1800 Tages-Booleans
     * unverändert; fehlende Anker — im warmen Lauf sind 260 bis 282 der rund 350 Placeables
     * verankert.
     *
     * <p><b>Was sie ist.</b> Phase 1 schöpft ab dieser Modellgröße immer ihren Deckel aus und
     * beweist keine Optimalität mehr; ihr {@code drop} nimmt über fünf Läufe fünf verschiedene
     * Werte an. Sie liefert also nicht bloß eine andere von mehreren gleich guten Belegungen —
     * sie liefert unterschiedlich GUTE. Der Anker in Phase 1 (siehe {@code solveWithCpSat}) bricht
     * nur den Gleichstand und kommt dagegen nicht an. Die bewegten Aufgaben verdrängen dann die
     * korrekt verankerten Gewohnheiten, was den Löwenanteil der Zahl erklärt.
     *
     * <p><b>Offen.</b> Der naheliegende Hebel ist ein reproduzierbares Arbeitsbudget für Phase 1
     * ({@code setMaxDeterministicTime}) statt der Wanduhr — dann hört sie bei gleicher Eingabe
     * immer an derselben Stelle auf, auch ohne Optimalität. Ein erster Versuch damit hat den
     * Forked-VM der Testsuite im nativen Teil von OR-Tools abstürzen lassen; das braucht eine
     * eigene Messreihe und steht deshalb aus.
     *
     * <p>Der Test hält beides fest: die Zusicherung, die gilt (kleiner Bestand kommt zur Ruhe),
     * und die Zahl, die nicht schlechter werden darf.
     */
    @Test
    void stabilitaetUeberDerAufnahmegrenze() {
        StringBuilder tabelle = new StringBuilder("\n=== Stabilität (Budget " + BUDGET + "s, 5 Läufe) ===\n"
                + String.format("%6s %8s %14s %14s %8s %10s%n",
                        "n", "grenze", "drop kalt", "Plaene kalt", "Plaene", "bewegt / Bloecke"));

        Map<Integer, Integer> letzteBewegung = new LinkedHashMap<>();
        for (int n : new int[]{ 10, 20, 50, 80 }) {
            for (int grenze : new int[]{ 1200 }) {
                setzeFeld("maxTaskDayVars", grenze);

                // Der Bestand wird GENAU EINMAL gebaut und dann mehrfach geplant. Baut man ihn je
                // Lauf neu, bekommen die Aufgaben neue IDs — der Stabilitätsanker schlüsselt aber
                // über die Task-ID und findet dann nie einen Vorgänger. Der warme Fall misst dann
                // nichts und sieht trotzdem plausibel aus.
                bestandStellen(n);
                keinVorzustand();

                Set<Long> dropsKalt = new LinkedHashSet<>();
                Set<String> plaeneKalt = new LinkedHashSet<>();
                for (int i = 0; i < 5; i++) {
                    try (SchedLog log = SchedLog.anhaengen()) {
                        ScheduleResult r = service.generateOptimalSchedule(1L, MORGEN, ENDE);
                        dropsKalt.add(log.letzter().drop());
                        plaeneKalt.add(abdruck(r));
                    }
                }

                // Warm: JEDER Lauf sieht den Plan des VORIGEN Laufs — genau die Schleife, die im
                // Betrieb läuft. Alle Läufe gegen denselben kalten Erstplan zu vergleichen misst
                // etwas anderes: dann steckt im Ergebnis vor allem der einmalige Übergang von
                // "kein Vorzustand" zu "Vorzustand da", und der darf den Plan verändern.
                ScheduleResult vorher = service.generateOptimalSchedule(1L, MORGEN, ENDE);
                List<Integer> bewegt = new ArrayList<>();
                Set<String> plaeneWarm = new LinkedHashSet<>();
                int bloecke = 0;
                for (int i = 0; i < 5; i++) {
                    List<CalendarEvent> vorzustand = f.alsEvents(vorher);
                    lenient().when(calendarEventRepository
                            .findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                                    eq(1L), any(), eq(false), any(), any())).thenReturn(vorzustand);

                    ScheduleResult jetzt = service.generateOptimalSchedule(1L, MORGEN, ENDE);
                    Map<String, LocalDateTime> vorherLage = lage(vorher);
                    bloecke = vorherLage.size();
                    bewegt.add(bewegteBloecke(vorherLage, lage(jetzt)));
                    plaeneWarm.add(abdruck(jetzt));
                    vorher = jetzt;
                }
                keinVorzustand();

                letzteBewegung.put(n, bewegt.get(bewegt.size() - 1));
                tabelle.append(String.format("%6d %8d %14d %14d %8d %10s / %d%n",
                        n, grenze, dropsKalt.size(), plaeneKalt.size(), plaeneWarm.size(),
                        bewegt, bloecke));
            }
        }
        setzeFeld("maxTaskDayVars", 1200);
        System.out.println(tabelle);

        // Was gilt: ein normaler Bestand kommt zur Ruhe. Geprüft am LETZTEN Lauf, denn der erste
        // warme Lauf darf den kalten Erstplan noch aufräumen.
        assertTrue(letzteBewegung.get(20) <= 20,
                "20 Aufgaben: der Plan kommt nicht zur Ruhe — beim fünften unveränderten Lauf lagen "
                        + letzteBewegung.get(20) + " Blöcke woanders");
        assertTrue(letzteBewegung.get(10) <= 10,
                "10 Aufgaben: der Plan kommt nicht zur Ruhe — beim fünften unveränderten Lauf lagen "
                        + letzteBewegung.get(10) + " Blöcke woanders");

        // Was (noch) nicht gilt: siehe Javadoc. Festgehalten wird nur, dass es nicht SCHLECHTER
        // wird — sonst verschwindet der Befund lautlos zwischen zwei Umbauten.
        assertTrue(letzteBewegung.get(80) <= 260,
                "80 Aufgaben: die bekannte Unruhe ist noch größer geworden ("
                        + letzteBewegung.get(80) + " bewegte Blöcke)");
    }

    private void keinVorzustand() {
        lenient().when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), any(), eq(false), any(), any())).thenReturn(new ArrayList<>());
    }

    /** Wo jeder Block liegt — Schlüssel wie im Stabilitätsanker, also über Task-ID und Chunk. */
    private static Map<String, LocalDateTime> lage(ScheduleResult r) {
        Map<String, LocalDateTime> out = new LinkedHashMap<>();
        for (ScheduledItem i : r.getScheduledTasks()) {
            out.put("t" + i.getTask().getId() + "#" + i.getChunkIndex(), i.getStartTime());
        }
        for (ScheduledItem i : r.getScheduledHabits()) {
            if (i.getHabit() == null) continue;
            out.put("h" + i.getHabit().getId() + "@" + i.getStartTime().toLocalDate(), i.getStartTime());
        }
        return out;
    }

    /** Wie viele Blöcke gegenüber dem Vorlauf woanders liegen (oder ganz fehlen). */
    private static int bewegteBloecke(Map<String, LocalDateTime> vorher,
                                      Map<String, LocalDateTime> nachher) {
        int n = 0;
        for (Map.Entry<String, LocalDateTime> e : vorher.entrySet()) {
            if (!e.getValue().equals(nachher.get(e.getKey()))) n++;
        }
        return n;
    }

    /** Was sich bewegt hat, aufgeschlüsselt: "t=..,h-Zeit=..,h-weg=..". */
    private static String bewegtNachArt(Map<String, LocalDateTime> vorher,
                                        Map<String, LocalDateTime> nachher) {
        int tasks = 0, habitZeit = 0, habitWeg = 0;
        for (Map.Entry<String, LocalDateTime> e : vorher.entrySet()) {
            LocalDateTime neu = nachher.get(e.getKey());
            if (e.getValue().equals(neu)) continue;
            if (e.getKey().startsWith("t")) tasks++;
            else if (neu == null) habitWeg++;
            else habitZeit++;
        }
        return "t=" + tasks + " hZeit=" + habitZeit + " hWeg=" + habitWeg;
    }

    /** Der Plan als Zeichenkette — zwei gleiche Abdrücke heißen: nichts hat sich bewegt. */
    private static String abdruck(ScheduleResult r) {
        return SchedulerFixtures.alleItems(r).stream()
                .map(i -> (i.getTask() != null ? "t" + i.getTask().getId() + "#" + i.getChunkIndex()
                                               : "h" + i.getStartTime().toLocalDate())
                        + "@" + i.getStartTime())
                .sorted()
                .collect(Collectors.joining(","));
    }

    // ------------------------------------------------------------------

    private static long perzentil(List<Long> werte, int p) {
        List<Long> sortiert = werte.stream().sorted().toList();
        int index = (int) Math.ceil(p / 100.0 * sortiert.size()) - 1;
        return sortiert.get(Math.max(0, Math.min(index, sortiert.size() - 1)));
    }

    private void setzeFeld(String name, Object wert) {
        try {
            Field feld = SmartSchedulerService.class.getDeclaredField(name);
            feld.setAccessible(true);
            feld.set(service, wert);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Feld " + name + " gibt es nicht mehr", e);
        }
    }

    private void bestandStellen(int anzahlAufgaben) {
        SchedulerFixtures.Bestand b = f.bestand(anzahlAufgaben, 4242, MORGEN);
        when(taskService.getSchedulableTasks(1L)).thenReturn(b.aufgaben());
        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(b.gewohnheiten());
        when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(1L)).thenReturn(b.trainings());
        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(b.projekte());
        when(courseScheduleRepository.findByUserId(1L)).thenReturn(b.vorlesungen());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(b.termine());
    }
}
