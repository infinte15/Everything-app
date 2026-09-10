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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Qualitätsgatter: ist der Plan, den der Scheduler liefert, auch der beste, den er liefern könnte?
 *
 * <p><b>Warum es diese Klasse braucht.</b> {@code SmartSchedulerServiceTest} prüft die Mechanik,
 * {@code SmartSchedulerSzenarienTest} das Verhalten — beide prüfen <i>Eigenschaften</i> ("die
 * wichtigere Aufgabe liegt früher"). Keiner der rund 175 Tests dort sieht sich jemals den
 * Zielwert oder den Löserstatus an. Ob die drei Heuristiken, mit denen der Lauf schnell gehalten
 * wird — Stillstandsabbruch nach 700 ms, Aufnahmegrenze bei 50 Chunks, nur auffüllende
 * Nachläufe —, dabei Qualität kosten, war schlicht ungemessen.
 *
 * <p><b>Wie hier gemessen wird: gegen einen Referenzlauf.</b> Derselbe Bestand wird zweimal
 * geplant — einmal mit den produktiven Einstellungen, einmal mit entschärften Bremsen (großes
 * Budget, kein Stillstandsabbruch, keine Aufnahmegrenze). Der Referenzlauf ist damit das, was das
 * Modell hergibt, wenn Zeit keine Rolle spielt. Die Tests vergleichen die beiden.
 *
 * <p><b>Was sich dabei beweisen lässt und was nicht.</b> "Findet immer die beste Lösung" ist für
 * Phase 2 nicht erreichbar und auch nicht angestrebt: sie beweist bei realistischem Bestand nie
 * Optimalität. Beweisbar ist die lexikographisch dominante Hälfte — <b>was überhaupt eingeplant
 * wird</b> (Phase 1, {@code drop}) ist beweisbar optimal, und die Feinplatzierung darauf
 * ({@code obj}) liegt messbar nah am Referenzoptimum. Genau in dieser Reihenfolge stehen die
 * Tests, denn nur bei gleichem {@code drop} sind zwei {@code obj}-Werte überhaupt vergleichbar:
 * Phase 2 minimiert unter der Nebenbedingung {@code dropCost <= bestDrop}, ein anderer Wert dort
 * heißt eine andere zulässige Menge.
 *
 * <p>Gemessen wird über {@link SchedLog}, also über die {@code SCHED}-Zeile, die der Service
 * ohnehin schreibt — {@link ScheduleResult} gibt weder Zielwert noch Phasenzeiten heraus.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Smart Scheduler: Qualität")
class SmartSchedulerQualitaetTest {

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
    private final LocalDate HORIZONT_ENDE = MORGEN.plusDays(30);

    /** Das Budget, das der Nutzer im Betrieb wirklich abwartet — nicht abgeschrieben, gelesen. */
    private static final double PRODUKTIONSBUDGET = SchedulerFixtures.produktionsBudgetSekunden();

    /**
     * Budget des Referenzlaufs.
     *
     * <p>Deutlich über dem produktiven Wert, aber nicht beliebig groß: Phase 2 verbraucht JEDE
     * Sekunde, die sie bekommt (sie beweist nie Optimalität), das Budget ist hier also direkt die
     * Laufzeit dieser Testklasse. 10 s reichen — die Messreihen zeigen, dass sich der Zielwert
     * am Demo-Bestand schon ab 1 s nicht mehr bewegt.
     */
    private static final double REFERENZBUDGET = 10.0;

    /**
     * Wie weit der produktive Lauf hinter dem Referenzlauf zurückbleiben darf.
     *
     * <p>Bewusst eine Konstante und kein loses {@code assertTrue}: verschlechtert sich die
     * Planung, soll die Zahl hier steigen müssen — und damit sichtbar werden — statt dass ein
     * großzügiger Vergleich sie stillschweigend durchwinkt. Gemessen sind rund 16 %; die 30 %
     * lassen Luft für die Streuung zwischen zwei Läufen (Phase 2 endet auf FEASIBLE, ihr
     * Zielwert schwankt).
     */
    private static final double TOLERANZ = 1.30;

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

        produktivEinstellen();
    }

    // ==================================================================
    // 1. Was eingeplant wird, ist beweisbar das Beste
    // ==================================================================

    /**
     * Bei normalem Bestand beweist Phase 1 Optimalität — die stärkste Aussage, die möglich ist.
     *
     * <p>{@code drop} ist die lexikographisch dominante Größe: {@code OPTIMAL} heißt, dass es
     * keine Belegung gibt, die mehr (bzw. Wichtigeres) unterbringt. Alles, was Phase 2 danach
     * tut, verschiebt nur noch innerhalb dieser Menge.
     *
     * <p>"Normal" ist hier gemessen und nicht geraten: bei bis zu etwa 20 offenen Aufgaben mit
     * realistischer Gewohnheitslast erreicht Phase 1 das zuverlässig — das deckt den Alltag ab
     * (der Entwicklungsbestand hat 16 bis 17). Was oberhalb davon passiert, steht in
     * {@link #beiGrossemBestandBleibtPhase1Brauchbar()}.
     */
    @Test
    void phase1BeweistOptimalitaetBeiNormalemBestand() {
        SchedLog.Lauf produktiv = mitBestand(20, this::produktivEinstellen);
        SchedLog.Lauf referenz  = mitBestand(20, this::referenzEinstellen);

        assertEquals("OPTIMAL", produktiv.p1Status(),
                "Phase 1 muss bei 20 Aufgaben Optimalität beweisen, war aber "
                        + produktiv.p1Status() + " (" + produktiv.p1Ms() + " ms). Steht hier "
                        + "UNKNOWN, kam der Löser wegen des Presolves nicht zum Suchen.");
        assertEquals(referenz.drop(), produktiv.drop(),
                "der produktive Lauf lässt mehr liegen als der Referenzlauf ohne Bremsen");
    }

    /**
     * Bei großem Bestand liefert Phase 1 mindestens eine brauchbare Belegung.
     *
     * <p>Oberhalb von etwa 20 Aufgaben schöpft Phase 1 ihren Deckel aus und endet auf FEASIBLE —
     * das ist hingenommen und der Grund für den Phase-1-Schnappschuss. Was NICHT passieren darf,
     * ist UNKNOWN: dann bleibt der Kalender unverändert stehen. Genau das war der Zustand vom
     * 30./31.08.2026, und dafür gab es bis hierher keinen Test.
     */
    @Test
    void beiGrossemBestandBleibtPhase1Brauchbar() {
        for (int aufgaben : new int[]{ 50, 80, 120 }) {
            SchedLog.Lauf lauf = mitBestand(aufgaben, this::produktivEinstellen);
            assertNotEquals("UNKNOWN", lauf.p1Status(),
                    aufgaben + " Aufgaben: Phase 1 kam nicht zum Suchen (p1Ms=" + lauf.p1Ms()
                            + ", intervalle=" + lauf.intervalle() + ") — der Kalender bleibt dann "
                            + "unverändert, und der Nutzer merkt nur, dass sich nichts tut.");
        }
    }

    /**
     * Wie weit die Feinplatzierung hinter dem Optimum zurückbleibt — als Zahl, nicht als Gefühl.
     *
     * <p>Der Stillstandsabbruch nach 700 ms kostet Qualität; das ist beabsichtigt und der Preis
     * dafür, dass der Nutzer nicht auf ein volles Zeitbudget wartet. Ungemessen war er bisher.
     * Gemessen (10.09.2026, 25 Aufgaben): der produktive Lauf liegt rund 16 % über dem
     * Referenzoptimum. {@link #TOLERANZ} hält das fest — wird es deutlich mehr, ist etwas kaputt
     * und nicht bloß knapp.
     */
    @Test
    void derAbstandZumOptimumBleibtImRahmen() {
        SchedLog.Lauf produktiv = mitBestand(25, this::produktivEinstellen);
        SchedLog.Lauf referenz  = mitBestand(25, this::referenzEinstellen);

        assertEquals(referenz.drop(), produktiv.drop(),
                "die Vergleichbarkeit der Zielwerte hängt an gleichem drop — Phase 2 minimiert "
                        + "unter der Nebenbedingung dropCost <= bestDrop");
        assertTrue(produktiv.obj() <= referenz.obj() * TOLERANZ,
                "obj=" + produktiv.obj() + " liegt "
                        + Math.round((produktiv.obj() / (double) referenz.obj() - 1) * 100)
                        + " % über dem Referenzoptimum " + referenz.obj()
                        + " — erlaubt sind " + Math.round((TOLERANZ - 1) * 100) + " %");
    }

    /**
     * Die Laufzeitgrenze des Modells kostet keine nennenswerte Menge eingeplanter Aufgaben.
     *
     * <p>Die nutzersichtbare Fassung der Frage: es geht am Ende nicht um einen Zielwert, sondern
     * darum, wie viele Aufgaben im Kalender stehen. Dieser Test hat den größten Fund dieser Runde
     * geliefert — mit der alten Grenze (50 Chunks) plante der produktive Lauf bei 80 offenen
     * Aufgaben nur 45 ein, der Referenzlauf 80. Die übrigen 35 wurden als gefährdet gemeldet,
     * obwohl sie bequem in den Horizont gepasst hätten.
     */
    @Test
    void dieLaufzeitgrenzeKostetKaumEingeplanteAufgaben() {
        for (int aufgaben : new int[]{ 20, 50, 80 }) {
            long p = geplanteAufgaben(aufgaben, this::produktivEinstellen);
            long r = geplanteAufgaben(aufgaben, this::referenzEinstellen);
            assertTrue(p >= r * 0.9,
                    "bei " + aufgaben + " Aufgaben plant der produktive Lauf " + p
                            + " Aufgaben ein, der Referenzlauf ohne Bremsen " + r
                            + " — mehr als 10 % Verlust ist keine Laufzeitgrenze mehr, sondern "
                            + "eine schlechtere Planung");
        }
    }

    // ==================================================================
    // 2. Kein Lauf endet still
    // ==================================================================

    /**
     * Der Regressionstest zur teuersten Störung dieses Schedulers.
     *
     * <p>Am 30./31.08.2026 kippte der Lauf ab etwa 70 Aufgaben um: Status UNKNOWN, Kalender
     * unverändert, {@code atRisk} <b>leer</b> — der Nutzer erfuhr also nicht einmal, dass nichts
     * geplant worden war. Behoben wurde das über den Phase-1-Schnappschuss und das Abschalten des
     * Presolve-Probings, nachgewiesen aber nur am laufenden Server; als "im Mockito-Harness nicht
     * testbar" abgelegt, weil die damaligen Fixtures viel kleiner waren als der Betrieb.
     *
     * <p>Nicht das Harness war die Grenze, sondern die Fixtures: dieser Bestand erreicht mit
     * Gewohnheiten, Training, Projekten und Vorlesungen dieselbe Intervallzahl wie der Betrieb.
     *
     * <p>Geprüft wird beides — der Lauf kommt durch, UND falls doch nicht, erfährt der Nutzer
     * davon. Die zweite Hälfte ist die eigentliche Zusicherung: ein stiller Fehlschlag ist
     * schlimmer als ein lauter.
     */
    @Test
    void keinLaufEndetStillOhneErgebnis() {
        for (int aufgaben : new int[]{ 20, 50, 80, 120 }) {
            bestandStellen(aufgaben, 4242);
            ScheduleResult r;
            SchedLog.Lauf lauf;
            try (SchedLog log = SchedLog.anhaengen()) {
                r = service.generateOptimalSchedule(1L, MORGEN, HORIZONT_ENDE);
                lauf = log.letzter();
            }

            assertNotEquals("UNKNOWN", lauf.status(),
                    aufgaben + " Aufgaben: der Lauf endete auf UNKNOWN — der Kalender bleibt dann "
                            + "unverändert. intervalle=" + lauf.intervalle()
                            + " p1Ms=" + lauf.p1Ms() + " p1Status=" + lauf.p1Status());

            assertTrue(!r.getScheduledTasks().isEmpty() || !r.getAtRisk().isEmpty(),
                    aufgaben + " Aufgaben: weder ein Block noch eine Meldung — der Nutzer erfährt "
                            + "nicht, dass nichts geplant wurde");

            SchedulerFixtures.assertKeineUeberlappung(r);
        }
    }

    /**
     * Jede Aufgabe steht am Ende entweder im Kalender oder in der Meldung — nie nirgends.
     *
     * <p>Gilt auch über der Aufnahmegrenze: was {@code markiereAufnahmegrenze} aus dem Modell
     * nimmt, bleibt in der Chunk-Liste, damit {@code classifyAtRisk} es melden kann.
     */
    @Test
    void keineAufgabeVerschwindetLautlos() {
        for (int aufgaben : new int[]{ 20, 80, 120 }) {
            List<Task> bestand = bestandStellen(aufgaben, 77);
            ScheduleResult r = service.generateOptimalSchedule(1L, MORGEN, HORIZONT_ENDE);

            Set<Long> geplant = r.getScheduledTasks().stream()
                    .map(i -> i.getTask().getId()).collect(Collectors.toSet());
            Set<Long> gemeldet = r.getAtRisk().stream()
                    .filter(a -> a.getTaskId() != null)
                    .map(AtRiskItem::getTaskId).collect(Collectors.toSet());

            List<String> verschwunden = bestand.stream()
                    .filter(t -> !geplant.contains(t.getId()) && !gemeldet.contains(t.getId()))
                    .map(Task::getTitle).collect(Collectors.toList());

            assertTrue(verschwunden.isEmpty(),
                    aufgaben + " Aufgaben: lautlos verschwunden — " + verschwunden);
        }
    }

    /**
     * Die Aufnahmegrenze schneidet strikt nach Priorität ab, nicht nach Gelegenheit.
     *
     * <p>{@code markiereAufnahmegrenze} lässt Task-Gruppen in der Reihenfolge von
     * {@code calculateTaskWeight} zu und kippt beim ersten Überlauf auf "voll". Damit darf keine
     * abgewiesene Aufgabe wichtiger sein als eine zugelassene — sonst wäre die Laufzeitgrenze
     * ein Geschmacksurteil.
     */
    @Test
    void dieAufnahmegrenzeSchneidetNachPrioritaetAb() {
        // Deutlich über der Grenze von 50 Chunks, alle unteilbar: ein Chunk je Aufgabe.
        List<Task> bestand = new ArrayList<>();
        Random rnd = new Random(9);
        for (int i = 0; i < 90; i++) {
            Task t = f.unteilbar(f.task("A" + i, 60, 1 + rnd.nextInt(5), null));
            bestand.add(t);
        }
        when(taskService.getSchedulableTasks(1L)).thenReturn(bestand);

        ScheduleResult r = service.generateOptimalSchedule(1L, MORGEN, HORIZONT_ENDE);

        Set<Long> geplant = r.getScheduledTasks().stream()
                .map(i -> i.getTask().getId()).collect(Collectors.toSet());
        int niedrigsteGeplant = bestand.stream().filter(t -> geplant.contains(t.getId()))
                .mapToInt(Task::getPriority).min().orElse(6);
        int hoechsteAbgewiesen = bestand.stream().filter(t -> !geplant.contains(t.getId()))
                .mapToInt(Task::getPriority).max().orElse(0);

        assertTrue(hoechsteAbgewiesen <= niedrigsteGeplant,
                "eine wichtigere Aufgabe (Priorität " + hoechsteAbgewiesen + ") wurde abgewiesen, "
                        + "während eine unwichtigere (Priorität " + niedrigsteGeplant
                        + ") eingeplant wurde");
    }

    // ==================================================================
    // 3. Größenordnung der Laufzeit — die billige Wache
    // ==================================================================

    /**
     * Die Wartezeit bleibt in der Größenordnung des Budgets.
     *
     * <p>Bewusst grob: die Uhr misst hier die <i>Größenordnung</i>, nicht die Dauer. Die genauen
     * Verteilungen stehen in {@code SmartSchedulerLastTest}, das getaggt ist und nicht bei jedem
     * {@code mvn test} mitläuft — eine scharfe Zeitschranke in der normalen Suite wird auf einer
     * ausgelasteten Maschine rot, ohne dass sich am Scheduler etwas geändert hätte.
     */
    @Test
    void dieWartezeitBleibtInDerGroessenordnungDesBudgets() {
        bestandStellen(80, 5);

        long begonnen = System.nanoTime();
        service.generateOptimalSchedule(1L, MORGEN, HORIZONT_ENDE);
        long ms = (System.nanoTime() - begonnen) / 1_000_000;

        long schranke = Math.round(PRODUKTIONSBUDGET * 1000 * 3);
        assertTrue(ms < schranke,
                "80 Aufgaben brauchten " + ms + " ms bei einem Budget von "
                        + PRODUKTIONSBUDGET + " s — mehr als das Dreifache heißt, dass die Zeit "
                        + "nicht mehr im Löser steckt (Presolve? Einsammeln? Persistenz?)");
    }

    // ==================================================================
    // Hilfen
    // ==================================================================

    /** Ein Lauf über den angegebenen Bestand, mit der übergebenen Einstellung. */
    private SchedLog.Lauf mitBestand(int aufgaben, Runnable einstellung) {
        bestandStellen(aufgaben, 4242);
        einstellung.run();
        try (SchedLog log = SchedLog.anhaengen()) {
            service.generateOptimalSchedule(1L, MORGEN, HORIZONT_ENDE);
            return log.letzter();
        } finally {
            produktivEinstellen();
        }
    }

    private long geplanteAufgaben(int aufgaben, Runnable einstellung) {
        bestandStellen(aufgaben, 4242);
        einstellung.run();
        try {
            return service.generateOptimalSchedule(1L, MORGEN, HORIZONT_ENDE)
                    .getScheduledTasks().stream().map(i -> i.getTask().getId()).distinct().count();
        } finally {
            produktivEinstellen();
        }
    }

    /** Produktive Einstellungen: genau das, was im Betrieb läuft. */
    private void produktivEinstellen() {
        setzeFeld("solverTimeLimitSeconds", PRODUKTIONSBUDGET);
        setzeFeld("phase1CapSeconds", 1.5d);
        setzeFeld("solverStallMs", 700L);
        setzeFeld("maxTaskChunks", 120);
        setzeFeld("maxTaskDayVars", 1200);
    }

    /**
     * Referenzlauf: dasselbe Modell, aber ohne die drei Bremsen.
     *
     * <p>Alle vier Felder sind {@code @Value}-Felder — der Referenzlauf ändert also nur
     * Konfiguration, nicht das Modell. Genau darauf beruht die Aussagekraft des Vergleichs: was
     * hier besser wird, ist der Preis der Wartezeit, kein anderer Plan.
     */
    private void referenzEinstellen() {
        setzeFeld("solverTimeLimitSeconds", REFERENZBUDGET);
        setzeFeld("phase1CapSeconds", REFERENZBUDGET);
        setzeFeld("solverStallMs", Long.MAX_VALUE);
        setzeFeld("maxTaskChunks", 100_000);
        setzeFeld("maxTaskDayVars", 100_000);
    }

    private void setzeFeld(String name, Object wert) {
        try {
            Field feld = SmartSchedulerService.class.getDeclaredField(name);
            feld.setAccessible(true);
            feld.set(service, wert);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Feld " + name + " gibt es nicht mehr — der Referenzlauf "
                    + "bräuchte dann einen anderen Hebel", e);
        }
    }

    /**
     * Stellt die Mocks auf einen realistischen Bestand der gewünschten Größe.
     *
     * @return die Aufgaben des Bestands, für Zusicherungen über einzelne Titel
     */
    private List<Task> bestandStellen(int anzahlAufgaben, long keim) {
        SchedulerFixtures.Bestand b = f.bestand(anzahlAufgaben, keim, MORGEN);
        when(taskService.getSchedulableTasks(1L)).thenReturn(b.aufgaben());
        when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(b.gewohnheiten());
        when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(1L)).thenReturn(b.trainings());
        when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(b.projekte());
        when(courseScheduleRepository.findByUserId(1L)).thenReturn(b.vorlesungen());
        when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(b.termine());
        return b.aufgaben();
    }
}
