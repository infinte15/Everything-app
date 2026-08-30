package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verhaltenstests für den Smart Scheduler — das Pflichtenheft, nicht die Mechanik.
 *
 * <p>Abgrenzung zu {@link SmartSchedulerServiceTest}: dort steht, wie der Löser arbeitet
 * (Modellaufbau, Gewichte, Laufzeit, Persistenz). Hier steht, was am Ende im Kalender stehen muss,
 * formuliert wie ein Nutzer es beschreiben würde — "die überfällige Aufgabe kommt zuerst", "vor
 * der Deadline ist Schluss", "die Meditation weicht, nicht die Klausurvorbereitung". Beides
 * getrennt zu halten hat einen Grund: die Mechanik darf sich ändern, dieses Verhalten nicht.
 *
 * <p>Der Solver läuft echt (OR-Tools-Natives), nur die Repositories sind gemockt.
 *
 * <p><b>Warum fast alle Szenarien ab morgen laufen.</b> {@code replanCutoff} ist
 * {@code max(jetzt, Tagesbeginn)}. Startet ein Szenario heute, hängt sein Ergebnis an der Uhrzeit,
 * zu der die Suite läuft — vormittags grün, abends rot. Wo "heute" gebraucht wird (Überfälliges),
 * ist die Arbeitszeit bewusst bis kurz vor Mitternacht geöffnet.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Smart Scheduler: Szenarien")
class SmartSchedulerSzenarienTest {

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

    private UserPreferences prefs;
    private final LocalDate HEUTE  = LocalDate.now();
    private final LocalDate MORGEN = LocalDate.now().plusDays(1);

    private long naechsteId = 9000L;

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
    // 1. Prioritäten — der Rang entscheidet, nicht der Zufall
    // ==================================================================

    @Nested
    @DisplayName("Prioritäten")
    class Prioritaeten {

        /** Bei Knappheit fällt die unwichtigste Aufgabe heraus, nicht irgendeine. */
        @Test
        void beiKnappheitFaelltDieUnwichtigsteHeraus() {
            prefs.setWorkdayEnd(LocalTime.of(10, 0));   // zwei Stunden, drei Aufgaben à 60 min

            List<Task> aufgaben = List.of(
                    unteilbar(task("P1", 60, 1, null)),
                    unteilbar(task("P3", 60, 3, null)),
                    unteilbar(task("P5", 60, 5, null)));
            when(taskService.getSchedulableTasks(1L)).thenReturn(aufgaben);

            ScheduleResult r = lauf(MORGEN, MORGEN);

            List<String> geplant = titel(r.getScheduledTasks());
            assertEquals(2, geplant.size(), "es passen nur zwei: " + geplant);
            assertTrue(geplant.contains("P5") && geplant.contains("P3"),
                    "P5 und P3 müssen liegen, P1 fällt: " + geplant);
        }

        /** Über mehrere Tage: die wichtigere Aufgabe bekommt den früheren Tag. */
        @Test
        void ueberTageBekommtDieWichtigereDenFruederenTag() {
            prefs.setWorkdayEnd(LocalTime.of(9, 0));   // ein Block pro Tag

            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("unwichtig", 60, 1, null)),
                    unteilbar(task("wichtig", 60, 5, null))));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(3));

            assertTrue(start(r, "wichtig").isBefore(start(r, "unwichtig")),
                    "wichtig lag " + start(r, "wichtig") + ", unwichtig " + start(r, "unwichtig"));
        }

        /**
         * Priorität schlägt Deadline-Nähe — die Kernaussage des Umbaus.
         *
         * Vorher war die Deadline ein Faktor AUF die Priorität und konnte sie überholen: ein
         * P2-Task von heute wog mehr als ein P5-Task ohne Termin.
         */
        @Test
        void prioritaetSchlaegtDeadlineNaehe() {
            prefs.setWorkdayEnd(LocalTime.of(9, 0));

            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("klein aber dringend", 60, 1, MORGEN.atTime(9, 0))),
                    unteilbar(task("gross ohne Termin", 60, 5, null))));
            // Alles außerhalb 08–09 zu, damit auch der Quetsch-Nachlauf nichts rettet.
            when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(
                    fixerBlock(MORGEN.atTime(0, 0), MORGEN.atTime(8, 0)),
                    fixerBlock(MORGEN.atTime(9, 0), MORGEN.plusDays(1).atTime(0, 0))));

            ScheduleResult r = lauf(MORGEN, MORGEN);

            assertEquals(List.of("gross ohne Termin"), titel(r.getScheduledTasks()));
        }

        /** Innerhalb einer Prioritätsstufe ordnet weiterhin die Deadline. */
        @Test
        void innerhalbEinerStufeOrdnetDieDeadline() {
            prefs.setWorkdayEnd(LocalTime.of(12, 0));

            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("spaeter faellig", 60, 3, MORGEN.plusDays(9).atTime(12, 0))),
                    unteilbar(task("frueher faellig", 60, 3, MORGEN.plusDays(2).atTime(12, 0)))));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(12));

            assertTrue(start(r, "frueher faellig").isBefore(start(r, "spaeter faellig")),
                    "bei gleicher Priorität gehört die nähere Deadline nach vorn");
        }
    }

    // ==================================================================
    // 2. Deadlines — vorher fertig, nie danach
    // ==================================================================

    @Nested
    @DisplayName("Deadlines")
    class Deadlines {

        /** Die harte Zusage: kein Block liegt hinter seiner Deadline. */
        @Test
        void keinBlockLiegtHinterSeinerDeadline() {
            List<Task> aufgaben = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                aufgaben.add(task("A" + i, 90, 1 + (i % 5), MORGEN.plusDays(i).atTime(14, 0)));
            }
            when(taskService.getSchedulableTasks(1L)).thenReturn(aufgaben);

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(20));

            for (ScheduledItem i : r.getScheduledTasks()) {
                assertFalse(i.getEndTime().isAfter(i.getTask().getDeadline()),
                        i.getTask().getTitle() + " endet " + i.getEndTime()
                                + ", Deadline war " + i.getTask().getDeadline());
            }
        }

        /** Ist Platz da, wird der Puffer eingehalten — "fertig" heißt nicht "auf die Minute". */
        @Test
        void mitPlatzWirdDerPufferEingehalten() {
            LocalDateTime deadline = MORGEN.plusDays(6).atTime(16, 0);
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(task("Bericht", 60, 3, deadline)));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(12));

            assertFalse(ende(r, "Bericht").isAfter(deadline.minusHours(24)),
                    "der letzte Block soll 24h vor der Deadline fertig sein, endete " + ende(r, "Bericht"));
            assertTrue(r.getAtRisk().isEmpty(), "der Puffer allein ist kein Grund zu warnen");
        }

        /**
         * Der Puffer darf eine Deadline niemals unerreichbar machen.
         *
         * Genau das war er zuerst: eine 120-Minuten-Aufgabe mit Termin in zweieinhalb Stunden bekam
         * ein Fenster von 75 Minuten, passte dort nicht und fiel komplett aus dem Hauptlauf.
         */
        @Test
        void derPufferMachtKeineDeadlineUnerreichbar() {
            // Deadline in gut drei Stunden ab Arbeitsbeginn, Aufgabe braucht drei.
            LocalDateTime deadline = MORGEN.atTime(11, 30);
            when(taskService.getSchedulableTasks(1L))
                    .thenReturn(List.of(unteilbar(task("Eilig", 180, 4, deadline))));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(3));

            assertEquals(1, r.getScheduledTasks().size(), "die Aufgabe muss geplant werden");
            assertFalse(ende(r, "Eilig").isAfter(deadline), "und zwar vor der Deadline");
            assertTrue(r.getAtRisk().isEmpty(), "ein geschrumpfter Puffer ist kein Risiko");
        }

        /**
         * Drei gleich wichtige Aufgaben mit Terminen an drei aufeinanderfolgenden Tagen werden in
         * Termin-Reihenfolge abgearbeitet.
         *
         * <p>Der Fall, an dem der Scheduler beim ersten Durchspielen gescheitert ist: alle drei
         * bekamen denselben Tagesrang, ihre Reihenfolge war damit beliebig — und sie landeten in
         * genau umgekehrter Reihenfolge im Kalender. Behoben durch die relative
         * Reihenfolge-Präferenz ({@code addTaskOrderPreference}) statt durch ein größeres Gewicht
         * auf der Startzeit, das die Leistungshoch-Einstellung erdrückt hätte.
         */
        @Test
        void engeDeadlinesWerdenInTerminReihenfolgeAbgearbeitet() {
            List<Task> aufgaben = List.of(
                    unteilbar(task("dritte", 120, 3, MORGEN.plusDays(3).atTime(17, 0))),
                    unteilbar(task("erste",  120, 3, MORGEN.plusDays(1).atTime(17, 0))),
                    unteilbar(task("zweite", 120, 3, MORGEN.plusDays(2).atTime(17, 0))));
            when(taskService.getSchedulableTasks(1L)).thenReturn(aufgaben);

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(10));

            List<String> reihenfolge = r.getScheduledTasks().stream()
                    .sorted(Comparator.comparing(ScheduledItem::getStartTime))
                    .map(i -> i.getTask().getTitle())
                    .collect(Collectors.toList());
            assertEquals(List.of("erste", "zweite", "dritte"), reihenfolge);

            for (ScheduledItem i : r.getScheduledTasks()) {
                assertFalse(i.getEndTime().isAfter(i.getTask().getDeadline()),
                        i.getTask().getTitle() + " endet hinter seinem Termin");
            }
            assertTrue(r.getAtRisk().isEmpty(), "und niemand ist in Gefahr");
        }

        /**
         * Die Reihenfolge ordnet den Tag, bestimmt aber nicht die Uhrzeit.
         *
         * Genau daran wäre die naheliegende Lösung (größeres Gewicht auf der Startzeit)
         * gescheitert: sie hätte die Wunschzeit-Einstellung des Nutzers überstimmt. Beide Zusagen
         * müssen gleichzeitig gelten.
         */
        @Test
        void dieReihenfolgeVerdraengtDieWunschzeitNicht() {
            prefs.setWorkdayStart(LocalTime.of(8, 0));
            prefs.setWorkdayEnd(LocalTime.of(20, 0));
            prefs.setPeakProductivityTime(ProductivityPeakTime.EVENING);
            prefs.setCoreHoursEnd(LocalTime.of(20, 0));   // Abendstrafe aus, sonst misst sie mit

            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("zuerst", 60, 3, MORGEN.plusDays(1).atTime(20, 0))),
                    unteilbar(task("danach", 60, 3, MORGEN.plusDays(2).atTime(20, 0)))));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(6));

            assertTrue(start(r, "zuerst").isBefore(start(r, "danach")),
                    "die Reihenfolge muss stimmen");
            assertTrue(start(r, "zuerst").toLocalTime().isAfter(LocalTime.of(12, 0)),
                    "und die Blöcke gehören trotzdem in das Leistungshoch am Abend, lagen aber "
                            + start(r, "zuerst"));
        }

        /**
         * Über TAGE hinweg ordnet die Deadline zuverlässig — dort greift {@code urgencyRank}.
         *
         * Passt nicht alles an einen Tag, entscheidet die Deadline-Stufe, wer den früheren Tag
         * bekommt. Das ist der Fall, in dem die Reihenfolge für den Nutzer sichtbar zählt.
         */
        @Test
        void ueberTageOrdnetDieDeadlineZuverlaessig() {
            prefs.setWorkdayEnd(LocalTime.of(10, 0));   // zwei Stunden pro Tag, ein Block

            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("in zwei Wochen", 120, 3, MORGEN.plusDays(14).atTime(17, 0))),
                    unteilbar(task("morgen", 120, 3, MORGEN.plusDays(1).atTime(10, 0)))));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(20));

            assertTrue(start(r, "morgen").isBefore(start(r, "in zwei Wochen")),
                    "die nähere Deadline gehört auf den früheren Tag");
        }

        /**
         * Die Reihenfolge darf keinen Block auf einen späteren Tag schieben.
         *
         * Das ist das Risiko eines Pauschalpreises: statt die Reihenfolge herzustellen, könnte der
         * Löser der Strafe ausweichen, indem er einen der beiden Blöcke auf den nächsten Tag legt —
         * die Strafe gilt ja nur für gemeinsame Tage. Beide passen an einen Tag, also gehören sie
         * auch an einen.
         */
        @Test
        void dieReihenfolgeSchiebtNichtsAufEinenSpaeterenTag() {
            prefs.setWorkdayStart(LocalTime.of(8, 0));
            prefs.setWorkdayEnd(LocalTime.of(17, 0));

            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("zuerst", 60, 3, MORGEN.plusDays(4).atTime(17, 0))),
                    unteilbar(task("danach", 60, 3, MORGEN.plusDays(6).atTime(17, 0)))));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(10));

            assertEquals(MORGEN, start(r, "zuerst").toLocalDate());
            assertEquals(MORGEN, start(r, "danach").toLocalDate(),
                    "beide passen an denselben Tag — die Reihenfolge ist kein Grund zu vertagen");
            assertTrue(start(r, "zuerst").isBefore(start(r, "danach")));
        }

        /** Ist es wirklich unmöglich, wird genau einmal gewarnt — nicht pro Block. */
        @Test
        void einWirklichUnmoeglicherTerminWirdGenauEinmalGemeldet() {
            // 10 Stunden Arbeit bis morgen 09:00: unmöglich, egal wie gequetscht wird.
            when(taskService.getSchedulableTasks(1L))
                    .thenReturn(List.of(task("Unmoeglich", 600, 3, MORGEN.atTime(9, 0))));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(5));

            List<AtRiskItem> deadlineRisiken = r.getAtRisk().stream()
                    .filter(i -> i.getReason() == AtRiskReason.WOULD_MISS_DEADLINE)
                    .collect(Collectors.toList());
            assertEquals(1, deadlineRisiken.size(), "genau eine Meldung: " + r.getAtRisk());
        }

        /**
         * Eine Aufgabe ohne Deadline kann keinen Termin reißen und darf nicht warnen.
         *
         * Der Bestand ist bewusst klein gehalten. Ein erster Entwurf warf 40 unplanbare Aufgaben
         * auf einen Ein-Stunden-Tag; einzeln lief das durch, im vollen Suite-Lauf trieb es den
         * Löser ins Zeitbudget. Dann greift die dokumentierte Regel "ein leerer Kalender ist
         * schlechter als ein veralteter", der Lauf gibt gar kein Ergebnis zurück — und der Test
         * prüfte plötzlich die Auslastung der Maschine statt die Meldungslogik.
         */
        @Test
        void ohneDeadlineGibtEsKeineDeadlineWarnung() {
            prefs.setWorkdayEnd(LocalTime.of(9, 0));   // ein Block pro Tag
            List<Task> viele = new ArrayList<>();
            for (int i = 0; i < 8; i++) viele.add(unteilbar(task("Ohne" + i, 60, 3, null)));
            when(taskService.getSchedulableTasks(1L)).thenReturn(viele);

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(2));

            assertTrue(r.getAtRisk().stream().noneMatch(i -> i.getReason() == AtRiskReason.WOULD_MISS_DEADLINE),
                    "ohne Termin gibt es kein Deadline-Risiko: " + r.getAtRisk());
            assertTrue(r.getAtRisk().stream().anyMatch(i -> i.getReason() == AtRiskReason.OUTSIDE_HORIZON),
                    "aber ein 'kommt später dran' ist richtig");
        }
    }

    // ==================================================================
    // 3. Überfälliges — vor allem anderen und so früh wie möglich
    // ==================================================================

    @Nested
    @DisplayName("Überfälliges")
    class Ueberfaelliges {

        @BeforeEach
        void tagWeitOeffnen() {
            // Damit "heute" unabhängig von der Uhrzeit der Testausführung Platz hat.
            prefs.setWorkdayStart(LocalTime.of(0, 0));
            prefs.setWorkdayEnd(LocalTime.of(23, 59));
            prefs.setPersonalHoursStart(LocalTime.of(0, 0));
            prefs.setPersonalHoursEnd(LocalTime.of(23, 59));
        }

        /** Überfälliges liegt vor allem, was noch Zeit hat. */
        @Test
        void ueberfaelligesLiegtVorAllemAnderen() {
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("hat Zeit", 60, 5, HEUTE.plusDays(10).atTime(12, 0))),
                    unteilbar(task("ueberfaellig", 60, 1, HEUTE.minusDays(2).atTime(12, 0)))));

            ScheduleResult r = lauf(HEUTE, HEUTE.plusDays(6));

            assertTrue(start(r, "ueberfaellig").isBefore(start(r, "hat Zeit")),
                    "überfällig lag " + start(r, "ueberfaellig") + ", das andere "
                            + start(r, "hat Zeit"));
        }

        /** Unter mehreren Überfälligen gleicher Priorität kommt das ältere zuerst. */
        @Test
        void unterUeberfaelligenKommtDasAeltereZuerst() {
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("gestern", 60, 3, HEUTE.minusDays(1).atTime(12, 0))),
                    unteilbar(task("vor zwei Wochen", 60, 3, HEUTE.minusDays(14).atTime(12, 0)))));

            ScheduleResult r = lauf(HEUTE, HEUTE.plusDays(6));

            assertTrue(start(r, "vor zwei Wochen").isBefore(start(r, "gestern")),
                    "das länger Liegengebliebene gehört zuerst nachgeholt");
        }

        /** Mehrere Überfällige überlappen sich nicht. */
        @Test
        void mehrereUeberfaelligeUeberlappenSichNicht() {
            List<Task> t = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                t.add(unteilbar(task("alt" + i, 60, 3, HEUTE.minusDays(i).atTime(12, 0))));
            }
            when(taskService.getSchedulableTasks(1L)).thenReturn(t);

            ScheduleResult r = lauf(HEUTE, HEUTE.plusDays(6));

            assertKeineUeberlappung(r);
            assertEquals(5, r.getScheduledTasks().size(), "alle fünf müssen einen Nachholtermin bekommen");
        }

        /** Auch wenn alles untergebracht ist, bleibt "der Termin ist vorbei" die wichtigere Aussage. */
        @Test
        void ueberfaelligesWirdGemeldetMitNachholtermin() {
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("ueberfaellig", 60, 3, HEUTE.minusDays(1).atTime(12, 0)))));

            ScheduleResult r = lauf(HEUTE, HEUTE.plusDays(6));

            AtRiskItem meldung = r.getAtRisk().stream()
                    .filter(i -> i.getReason() == AtRiskReason.PAST_DEADLINE)
                    .findFirst().orElseThrow(() -> new AssertionError("keine PAST_DEADLINE-Meldung"));
            assertNotNull(meldung.getPlannedStart(),
                    "die Meldung muss den Nachholtermin nennen — das ist die Antwort auf 'und was jetzt?'");
            assertEquals(0, meldung.getMinutes(), "es fehlt nichts, es ist nur zu spät");
        }

        /** Überfälliges bleibt im Nachhol-Fenster und wandert nicht in den nächsten Monat. */
        @Test
        void ueberfaelligesBleibtImNachholfenster() {
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("ueberfaellig", 60, 3, HEUTE.minusDays(30).atTime(12, 0)))));

            ScheduleResult r = lauf(HEUTE, HEUTE.plusDays(27));

            assertFalse(start(r, "ueberfaellig").toLocalDate().isAfter(HEUTE.plusDays(14)),
                    "überfällig heißt jetzt, nicht irgendwann: " + start(r, "ueberfaellig"));
        }
    }

    // ==================================================================
    // 4. Verdrängen — eine Deadline schlägt Verzichtbares
    // ==================================================================

    @Nested
    @DisplayName("Verdrängen")
    class Verdraengen {

        @BeforeEach
        void engerTag() {
            prefs.setWorkdayStart(LocalTime.of(8, 0));
            prefs.setWorkdayEnd(LocalTime.of(9, 0));
            prefs.setPersonalHoursStart(LocalTime.of(8, 0));
            prefs.setPersonalHoursEnd(LocalTime.of(9, 0));
            prefs.setBreakDurationMinutes(0);
        }

        /** Nur der erste Tag hat eine freie Stunde; alles danach ist zu. */
        private void nurErsteStundeFrei(LocalDate start, int tage) {
            List<CalendarEvent> zu = new ArrayList<>();
            for (int d = 1; d <= tage; d++) {
                zu.add(fixerBlock(start.plusDays(d).atTime(0, 0), start.plusDays(d + 1).atTime(0, 0)));
            }
            when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(zu);
        }

        @Test
        void eineDeadlineVerdraengtProjektzeit() {
            nurErsteStundeFrei(MORGEN, 6);
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("Abgabe", 60, 3, MORGEN.plusDays(1).atTime(9, 0)))));

            Project p = new Project();
            p.setId(id());
            p.setName("Nebenprojekt");
            p.setStatus(ProjectStatus.IN_PROGRESS);
            p.setWeeklySessionCount(5);
            p.setSessionDurationMinutes(60);
            when(projectRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(List.of(p));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(6));

            assertEquals(MORGEN.atTime(8, 0), start(r, "Abgabe"), "die Aufgabe bekommt die Stunde");
            assertTrue(r.getScheduledHabits().stream()
                            .noneMatch(i -> i.getStartTime().toLocalDate().equals(MORGEN)),
                    "die Projektzeit muss am ersten Tag weichen");
        }

        /** Verdrängt wird das Billigste — die niedriger priorisierte Gewohnheit, nicht die andere. */
        @Test
        void verdraengtWirdDasBilligste() {
            nurErsteStundeFrei(MORGEN, 6);
            prefs.setWorkdayEnd(LocalTime.of(10, 0));       // zwei Stunden
            prefs.setPersonalHoursEnd(LocalTime.of(10, 0));

            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("Abgabe", 60, 5, MORGEN.plusDays(1).atTime(10, 0)))));
            when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(
                    taeglicheGewohnheit("wichtige Gewohnheit", 60, 5),
                    taeglicheGewohnheit("beliebige Gewohnheit", 60, 1)));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(6));

            List<String> amErstenTag = r.getScheduledHabits().stream()
                    .filter(i -> i.getStartTime().toLocalDate().equals(MORGEN))
                    .map(i -> i.getHabit().getName())
                    .collect(Collectors.toList());
            assertEquals(List.of("wichtige Gewohnheit"), amErstenTag,
                    "die unwichtigere Gewohnheit muss weichen, nicht die wichtigere: " + amErstenTag);
        }

        /** Was weggefallen ist, steht in der At-Risk-Liste. */
        @Test
        void weggefallenesWirdGemeldet() {
            nurErsteStundeFrei(MORGEN, 6);
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("Abgabe", 60, 3, MORGEN.plusDays(1).atTime(9, 0)))));
            Habit h = taeglicheGewohnheit("Meditation", 60, 1);
            when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(h));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(6));

            assertTrue(r.getAtRisk().stream().anyMatch(i -> h.getId().equals(i.getHabitId())),
                    "die verdrängte Gewohnheit gehört gemeldet: " + r.getAtRisk());
        }

        /** Hilft Verdrängen nicht, wird auch nichts verdrängt. */
        @Test
        void ohneNutzenWirdNichtsVerdraengt() {
            // Die Aufgabe braucht zwei Stunden, frei ist nur eine — Verdrängen ändert daran nichts.
            nurErsteStundeFrei(MORGEN, 6);
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("Zu gross", 120, 3, MORGEN.plusDays(1).atTime(9, 0)))));
            when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any()))
                    .thenReturn(List.of(taeglicheGewohnheit("Meditation", 60, 1)));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(6));

            assertTrue(r.getScheduledHabits().stream()
                            .anyMatch(i -> i.getStartTime().toLocalDate().equals(MORGEN)),
                    "die Gewohnheit darf nicht umsonst geopfert werden");
        }

        /**
         * Der Tagesdeckel bleibt auch beim Verdrängen stehen.
         *
         * Verdrängen SCHAFFT Kapazität — es braucht den Deckel nicht zu brechen, und tut es nicht:
         * sonst würde aus "eine Deadline retten" ein zugestellter Tag.
         */
        @Test
        void derTagesdeckelBleibtAuchBeimVerdraengen() {
            prefs.setWorkdayStart(LocalTime.of(8, 0));
            prefs.setWorkdayEnd(LocalTime.of(20, 0));
            prefs.setPersonalHoursStart(LocalTime.of(8, 0));
            prefs.setPersonalHoursEnd(LocalTime.of(20, 0));
            prefs.setMaxTaskMinutesPerDay(120);

            List<Task> t = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                t.add(unteilbar(task("A" + i, 60, 3, MORGEN.atTime(20, 0))));
            }
            when(taskService.getSchedulableTasks(1L)).thenReturn(t);

            ScheduleResult r = lauf(MORGEN, MORGEN);

            long amTag = r.getScheduledTasks().stream()
                    .filter(i -> i.getStartTime().toLocalDate().equals(MORGEN))
                    .mapToLong(i -> ChronoUnit.MINUTES.between(i.getStartTime(), i.getEndTime()))
                    .sum();
            assertTrue(amTag <= 120,
                    "der Tagesdeckel von 120 Minuten gilt auch hier, es waren " + amTag);
        }
    }

    // ==================================================================
    // 5. Zerlegung — wie eine Aufgabe in Blöcke zerfällt
    // ==================================================================

    @Nested
    @DisplayName("Zerlegung")
    class Zerlegung {

        @Test
        void unteilbaresBleibtEinBlock() {
            Task t = unteilbar(task("Am Stueck", 180, 3, MORGEN.plusDays(5).atTime(17, 0)));
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(t));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(10));

            assertEquals(1, r.getScheduledTasks().size(), "unteilbar heißt genau ein Block");
            assertEquals(180, dauer(r.getScheduledTasks().get(0)));
        }

        @Test
        void dieBlockgrenzenWerdenEingehalten() {
            Task t = task("Gross", 300, 3, MORGEN.plusDays(8).atTime(17, 0));
            t.setSplittable(true);
            t.setMinChunkMinutes(60);
            t.setMaxChunkMinutes(120);
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(t));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(14));

            assertFalse(r.getScheduledTasks().isEmpty());
            for (ScheduledItem i : r.getScheduledTasks()) {
                assertTrue(dauer(i) >= 60 && dauer(i) <= 120,
                        "Block von " + dauer(i) + " min verletzt die Grenzen 60..120");
            }
            assertEquals(300, r.getScheduledTasks().stream().mapToLong(this::dauerL).sum(),
                    "die Summe muss die volle Dauer ergeben");
        }

        /** Schon geleistete Minuten werden nicht noch einmal verplant. */
        @Test
        void geleisteteMinutenWerdenAbgezogen() {
            Task t = task("Halb fertig", 240, 3, MORGEN.plusDays(8).atTime(17, 0));
            t.setSplittable(true);
            t.setCompletedMinutes(180);
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(t));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(14));

            assertEquals(60, r.getScheduledTasks().stream().mapToLong(this::dauerL).sum(),
                    "nur die Restzeit gehört in den Kalender");
        }

        /** Die Blocknummerierung hat keine Löcher — sonst stünde "(1/3)" neben "(3/3)". */
        @Test
        void dieBlocknummerierungHatKeineLoecher() {
            Task t = task("Lang", 480, 3, MORGEN.plusDays(10).atTime(17, 0));
            t.setSplittable(true);
            t.setMinChunkMinutes(60);
            t.setMaxChunkMinutes(60);
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(t));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(14));

            List<Integer> nummern = r.getScheduledTasks().stream()
                    .map(ScheduledItem::getChunkIndex).sorted().collect(Collectors.toList());
            for (int i = 0; i < nummern.size(); i++) {
                assertEquals(i + 1, nummern.get(i), "Lücke in der Nummerierung: " + nummern);
            }
        }

        @Test
        void hoechstensSoVieleBloeckeProTagWieErlaubt() {
            Task t = task("Verteilt", 360, 3, MORGEN.plusDays(10).atTime(17, 0));
            t.setSplittable(true);
            t.setMinChunkMinutes(60);
            t.setMaxChunkMinutes(60);
            t.setMaxChunksPerDay(1);
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(t));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(14));

            Map<LocalDate, Long> proTag = r.getScheduledTasks().stream()
                    .collect(Collectors.groupingBy(i -> i.getStartTime().toLocalDate(),
                            Collectors.counting()));
            assertTrue(proTag.values().stream().allMatch(n -> n <= 1),
                    "höchstens ein Block pro Tag, war: " + proTag);
        }

        private int dauer(ScheduledItem i) {
            return (int) ChronoUnit.MINUTES.between(i.getStartTime(), i.getEndTime());
        }

        private long dauerL(ScheduledItem i) {
            return ChronoUnit.MINUTES.between(i.getStartTime(), i.getEndTime());
        }
    }

    // ==================================================================
    // 6. Zeitliche Zusagen — Fenster, Vergangenheit, Mitternacht
    // ==================================================================

    @Nested
    @DisplayName("Zeitliche Zusagen")
    class ZeitlicheZusagen {

        /** "Schedule after": vorher wird nichts geplant. */
        @Test
        void vorNotBeforeWirdNichtsGeplant() {
            Task t = task("Erst ab Freitag", 120, 4, MORGEN.plusDays(10).atTime(17, 0));
            t.setNotBefore(MORGEN.plusDays(4).atTime(8, 0));
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(t));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(14));

            assertFalse(r.getScheduledTasks().isEmpty(), "geplant werden muss sie trotzdem");
            for (ScheduledItem i : r.getScheduledTasks()) {
                assertFalse(i.getStartTime().isBefore(t.getNotBefore()),
                        "Block lag " + i.getStartTime() + ", erlaubt erst ab " + t.getNotBefore());
            }
        }

        /** notBefore hinter dem Nahbereich lässt die Aufgabe nicht verschwinden. */
        @Test
        void notBeforeHinterDemNahbereichVerliertDieAufgabeNicht() {
            Task t = task("Weit weg", 60, 3, null);
            t.setNotBefore(MORGEN.plusDays(20).atTime(8, 0));
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(t));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(27));

            assertEquals(1, r.getScheduledTasks().size(),
                    "eine Aufgabe mit spätem Startdatum darf nicht lautlos verschwinden");
        }

        /** Aufgaben bleiben in der Arbeitszeit, auch wenn die Privatzeit weiter reicht. */
        @Test
        void aufgabenBleibenInDerArbeitszeit() {
            prefs.setWorkdayStart(LocalTime.of(9, 0));
            prefs.setWorkdayEnd(LocalTime.of(15, 0));
            prefs.setPersonalHoursStart(LocalTime.of(6, 0));
            prefs.setPersonalHoursEnd(LocalTime.of(23, 0));

            List<Task> t = new ArrayList<>();
            for (int i = 0; i < 6; i++) t.add(unteilbar(task("A" + i, 60, 3, null)));
            when(taskService.getSchedulableTasks(1L)).thenReturn(t);

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(6));

            for (ScheduledItem i : r.getScheduledTasks()) {
                assertFalse(i.getStartTime().toLocalTime().isBefore(LocalTime.of(9, 0)),
                        i.getTask().getTitle() + " begann " + i.getStartTime());
                assertFalse(i.getEndTime().toLocalTime().isAfter(LocalTime.of(15, 0)),
                        i.getTask().getTitle() + " endete " + i.getEndTime());
            }
        }

        /** Kein Block läuft über Mitternacht. */
        @Test
        void keinBlockLaeuftUeberMitternacht() {
            prefs.setWorkdayStart(LocalTime.of(8, 0));
            prefs.setWorkdayEnd(LocalTime.of(23, 30));

            List<Task> t = new ArrayList<>();
            for (int i = 0; i < 10; i++) t.add(unteilbar(task("A" + i, 120, 3, null)));
            when(taskService.getSchedulableTasks(1L)).thenReturn(t);

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(6));

            for (ScheduledItem i : alleItems(r)) {
                assertEquals(i.getStartTime().toLocalDate(), i.getEndTime().minusNanos(1).toLocalDate(),
                        "Block über Mitternacht: " + i.getStartTime() + " – " + i.getEndTime());
            }
        }

        /** Nichts wird in die Vergangenheit geplant. */
        @Test
        void nichtsLandetInDerVergangenheit() {
            prefs.setWorkdayStart(LocalTime.of(0, 0));
            prefs.setWorkdayEnd(LocalTime.of(23, 59));
            prefs.setPersonalHoursStart(LocalTime.of(0, 0));
            prefs.setPersonalHoursEnd(LocalTime.of(23, 59));

            List<Task> t = new ArrayList<>();
            for (int i = 0; i < 8; i++) t.add(unteilbar(task("A" + i, 60, 3, HEUTE.minusDays(i + 1).atTime(12, 0))));
            when(taskService.getSchedulableTasks(1L)).thenReturn(t);

            ScheduleResult r = lauf(HEUTE, HEUTE.plusDays(6));

            LocalDateTime jetzt = LocalDateTime.now();
            for (ScheduledItem i : alleItems(r)) {
                assertFalse(i.getStartTime().isBefore(jetzt.minusMinutes(20)),
                        "Block in der Vergangenheit: " + i.getStartTime());
            }
        }

        /** Ein gepinnter Termin wird nie überplant. */
        @Test
        void gepinntesWirdNieUeberplant() {
            CalendarEvent termin = fixerBlock(MORGEN.atTime(10, 0), MORGEN.atTime(14, 0));
            when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(termin));

            List<Task> t = new ArrayList<>();
            for (int i = 0; i < 6; i++) t.add(unteilbar(task("A" + i, 60, 3, MORGEN.atTime(17, 0))));
            when(taskService.getSchedulableTasks(1L)).thenReturn(t);
            when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any()))
                    .thenReturn(List.of(taeglicheGewohnheit("Lesen", 30, 3)));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(3));

            for (ScheduledItem i : alleItems(r)) {
                assertFalse(i.getStartTime().isBefore(termin.getEndTime())
                                && i.getEndTime().isAfter(termin.getStartTime()),
                        "überplant den gepinnten Termin: " + i.getStartTime() + " – " + i.getEndTime());
            }
        }
    }

    // ==================================================================
    // 7. Gewohnheiten und Trainings
    // ==================================================================

    @Nested
    @DisplayName("Gewohnheiten und Trainings")
    class WiederkehrendeItems {

        /** Eine Gewohnheit mit Wochenpensum bekommt genau so viele Termine wie vereinbart. */
        @Test
        void dasWochenpensumWirdEingehalten() {
            Habit h = taeglicheGewohnheit("Laufen", 45, 3);
            h.setTimesPerWeek(3);
            when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(h));

            LocalDate montag = MORGEN.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            ScheduleResult r = lauf(montag, montag.plusDays(6));

            assertEquals(3, r.getScheduledHabits().size(), "drei Termine in der Woche, nicht mehr");
        }

        /** Zwei Termine derselben Gewohnheit liegen nicht am selben Tag. */
        @Test
        void zweiTermineDerselbenGewohnheitNichtAmSelbenTag() {
            Habit h = taeglicheGewohnheit("Laufen", 45, 3);
            h.setTimesPerWeek(4);
            when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(h));

            LocalDate montag = MORGEN.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            ScheduleResult r = lauf(montag, montag.plusDays(6));

            List<LocalDate> tage = r.getScheduledHabits().stream()
                    .map(i -> i.getStartTime().toLocalDate()).collect(Collectors.toList());
            assertEquals(tage.size(), tage.stream().distinct().count(),
                    "jeder Tag höchstens einmal: " + tage);
        }

        /** Eine Abend-Gewohnheit landet am Abend, nicht mitten im Arbeitstag. */
        @Test
        void eineAbendGewohnheitLandetAmAbend() {
            prefs.setWorkdayStart(LocalTime.of(8, 0));
            prefs.setWorkdayEnd(LocalTime.of(17, 0));
            prefs.setPersonalHoursStart(LocalTime.of(6, 0));
            prefs.setPersonalHoursEnd(LocalTime.of(23, 0));

            Habit h = taeglicheGewohnheit("Vor dem Schlafen lesen", 30, 3);
            h.setIdealWindow(HabitWindow.EVENING);
            when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(List.of(h));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(6));

            assertFalse(r.getScheduledHabits().isEmpty());
            for (ScheduledItem i : r.getScheduledHabits()) {
                assertFalse(i.getStartTime().toLocalTime().isBefore(LocalTime.of(17, 0)),
                        "Abend-Gewohnheit lag " + i.getStartTime());
            }
        }

        /** Trainings halten einen Ruhetag Abstand. */
        @Test
        void trainingsHaltenEinenRuhetagAbstand() {
            LocalDate montag = MORGEN.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(eq(1L))).thenReturn(List.of(
                    training("Push", 60, montag),
                    training("Pull", 60, montag),
                    training("Beine", 60, montag)));

            ScheduleResult r = lauf(montag, montag.plusDays(6));

            List<LocalDate> tage = r.getScheduledHabits().stream()
                    .filter(i -> i.getWorkoutSession() != null)
                    .map(i -> i.getStartTime().toLocalDate())
                    .sorted().collect(Collectors.toList());
            assertEquals(3, tage.size());
            for (int i = 1; i < tage.size(); i++) {
                assertTrue(ChronoUnit.DAYS.between(tage.get(i - 1), tage.get(i)) >= 2,
                        "zwischen zwei Trainings gehört ein Ruhetag: " + tage);
            }
        }

        /**
         * Ein gesetzter Wochentag wird eingehalten - auch dann, wenn die Ruhetagsverteilung
         * etwas anderes wollte. Wer den Tag selbst wählt, legt den Rhythmus selbst fest.
         */
        @Test
        void einWunschtagWirdEingehalten() {
            LocalDate montag = MORGEN.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(eq(1L))).thenReturn(List.of(
                    trainingAmWunschtag("Push", 60, montag, DayOfWeek.TUESDAY)));

            ScheduleResult r = lauf(montag, montag.plusDays(6));

            List<ScheduledItem> trainings = r.getScheduledHabits().stream()
                    .filter(i -> i.getWorkoutSession() != null).toList();
            assertEquals(1, trainings.size());
            assertEquals(DayOfWeek.TUESDAY, trainings.get(0).getStartTime().getDayOfWeek());
        }

        /**
         * Mehrere Wunschtage ohne Ruhetag dazwischen bleiben trotzdem stehen. Das ist der Fall,
         * für den die Ruhetagsregel bei gepinnten Einheiten ausgesetzt wird - sie würde hier
         * gegen eine ausdrückliche Einstellung des Nutzers arbeiten.
         */
        @Test
        void wunschtageAnFolgetagenBleibenBestehen() {
            LocalDate montag = MORGEN.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(eq(1L))).thenReturn(List.of(
                    trainingAmWunschtag("Push", 60, montag, DayOfWeek.MONDAY),
                    trainingAmWunschtag("Pull", 60, montag, DayOfWeek.TUESDAY)));

            ScheduleResult r = lauf(montag, montag.plusDays(6));

            Set<DayOfWeek> tage = r.getScheduledHabits().stream()
                    .filter(i -> i.getWorkoutSession() != null)
                    .map(i -> i.getStartTime().getDayOfWeek())
                    .collect(Collectors.toSet());
            assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), tage);
        }

        /**
         * Die Reihenfolge der Wunschtage darf nicht von der Id abhängen.
         *
         * <p>Vor der Trennung von Ordnung und Tageszuweisung galt "Einheit i startet vor Einheit
         * i+1" auch für gepinnte Einheiten. Eine früh angelegte Routine am Freitag und eine
         * später angelegte am Montag machten das Modell damit unlösbar, und der Solver verwarf
         * beide - statt sie einfach an ihre Tage zu legen.
         */
        @Test
        void einSpaeterAngelegterFruehererWunschtagVerwirftNichts() {
            LocalDate montag = MORGEN.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(eq(1L))).thenReturn(List.of(
                    trainingAmWunschtag("Freitags-Einheit", 60, montag, DayOfWeek.FRIDAY),
                    trainingAmWunschtag("Montags-Einheit", 60, montag, DayOfWeek.MONDAY)));

            ScheduleResult r = lauf(montag, montag.plusDays(6));

            Set<DayOfWeek> tage = r.getScheduledHabits().stream()
                    .filter(i -> i.getWorkoutSession() != null)
                    .map(i -> i.getStartTime().getDayOfWeek())
                    .collect(Collectors.toSet());
            assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), tage,
                    "beide Einheiten müssen an ihrem Tag liegen, unabhängig von der Reihenfolge");
        }

        /** Ohne Wunschtag bleibt es beim alten Verhalten: der Scheduler verteilt mit Ruhetagen. */
        @Test
        void trainingsOhneWunschtagWerdenWeiterhinVerteilt() {
            LocalDate montag = MORGEN.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            when(workoutSessionRepository.findByUserIdAndIsFlexibleTrue(eq(1L))).thenReturn(List.of(
                    trainingAmWunschtag("Push", 60, montag, DayOfWeek.MONDAY),
                    training("Pull", 60, montag),
                    training("Beine", 60, montag)));

            ScheduleResult r = lauf(montag, montag.plusDays(6));

            List<LocalDate> frei = r.getScheduledHabits().stream()
                    .filter(i -> i.getWorkoutSession() != null
                            && i.getWorkoutSession().getRoutine() == null)
                    .map(i -> i.getStartTime().toLocalDate())
                    .sorted().toList();
            assertEquals(2, frei.size());
            assertTrue(ChronoUnit.DAYS.between(frei.get(0), frei.get(1)) >= 2,
                    "zwischen den freien Einheiten gehört weiterhin ein Ruhetag: " + frei);
        }

        /** Eine Aufgabe schlägt beim Verdrängen jedes wiederkehrende Item — die Kerninvariante. */
        @Test
        void eineAufgabeSchlaegtJedesWiederkehrendeItem() {
            prefs.setWorkdayStart(LocalTime.of(8, 0));
            prefs.setWorkdayEnd(LocalTime.of(9, 0));
            prefs.setPersonalHoursStart(LocalTime.of(8, 0));
            prefs.setPersonalHoursEnd(LocalTime.of(9, 0));

            // Die schwächstmögliche Aufgabe gegen die stärkstmögliche Gewohnheit.
            when(taskService.getSchedulableTasks(1L))
                    .thenReturn(List.of(unteilbar(task("schwaechste Aufgabe", 60, 1, null))));
            when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any()))
                    .thenReturn(List.of(taeglicheGewohnheit("staerkste Gewohnheit", 60, 5)));

            ScheduleResult r = lauf(MORGEN, MORGEN);

            assertEquals(1, r.getScheduledTasks().size(),
                    "die Aufgabe muss den Platz bekommen — sie kommt nicht nächste Woche wieder");
        }
    }

    // ==================================================================
    // 8. Einstellungen und Robustheit
    // ==================================================================

    @Nested
    @DisplayName("Einstellungen und Robustheit")
    class EinstellungenUndRobustheit {

        /** Zwischen zwei Blöcken liegt die eingestellte Pause. */
        @Test
        void zwischenBloeckenLiegtDieEingestelltePause() {
            prefs.setBreakDurationMinutes(15);
            List<Task> t = new ArrayList<>();
            for (int i = 0; i < 4; i++) t.add(unteilbar(task("A" + i, 60, 3, null)));
            when(taskService.getSchedulableTasks(1L)).thenReturn(t);

            ScheduleResult r = lauf(MORGEN, MORGEN);

            List<ScheduledItem> sortiert = r.getScheduledTasks().stream()
                    .sorted(Comparator.comparing(ScheduledItem::getStartTime))
                    .collect(Collectors.toList());
            for (int i = 1; i < sortiert.size(); i++) {
                long luecke = ChronoUnit.MINUTES.between(
                        sortiert.get(i - 1).getEndTime(), sortiert.get(i).getStartTime());
                assertTrue(luecke >= 15,
                        "zwischen zwei Blöcken gehören 15 Minuten, waren " + luecke);
            }
        }

        /** Um einen gepinnten Termin herum bleibt der eingestellte Puffer frei. */
        @Test
        void umEinenTerminBleibtDerPufferFrei() {
            prefs.setBufferMinutes(30);
            CalendarEvent termin = fixerBlock(MORGEN.atTime(12, 0), MORGEN.atTime(13, 0));
            when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(List.of(termin));

            List<Task> t = new ArrayList<>();
            for (int i = 0; i < 5; i++) t.add(unteilbar(task("A" + i, 60, 3, MORGEN.atTime(17, 0))));
            when(taskService.getSchedulableTasks(1L)).thenReturn(t);

            ScheduleResult r = lauf(MORGEN, MORGEN);

            for (ScheduledItem i : r.getScheduledTasks()) {
                assertFalse(i.getEndTime().isAfter(termin.getStartTime().minusMinutes(30))
                                && i.getStartTime().isBefore(termin.getEndTime().plusMinutes(30)),
                        "Block " + i.getStartTime() + "–" + i.getEndTime()
                                + " verletzt den 30-Minuten-Puffer um den Termin");
            }
        }

        /** Ohne alles bleibt der Kalender leer — und das ist ein Erfolg, kein Fehler. */
        @Test
        void leererBestandLiefertLeerenPlanOhneFehler() {
            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(6));

            assertTrue(r.getScheduledTasks().isEmpty());
            assertTrue(r.getScheduledHabits().isEmpty());
            assertTrue(r.getAtRisk().isEmpty());
        }

        /** Eine schlicht unmögliche Aufgabe reißt den Rest des Plans nicht mit. */
        @Test
        void eineUnmoeglicheAufgabeReisstDenRestNichtMit() {
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("Unmoeglich", 1200, 5, MORGEN.atTime(9, 0))),
                    unteilbar(task("Machbar", 60, 3, MORGEN.plusDays(5).atTime(17, 0)))));

            ScheduleResult r = lauf(MORGEN, MORGEN.plusDays(10));

            assertNotNull(start(r, "Machbar"), "die machbare Aufgabe muss trotzdem geplant werden");
        }

        /**
         * Sich überlappende gepinnte Termine dürfen das Modell nicht sprengen.
         *
         * Die drei Termine verschmelzen zu 09:00–14:00. Übrig bleiben 08:00–09:00 und 14:00–17:00 —
         * geprüft wird, dass der Lauf gelingt und keiner der Blöcke die gesperrte Zeit anrührt.
         * (Der erste Entwurf verlangte hier den Platz NACH den Terminen; der Löser nahm die freie
         * Stunde davor, und das ist richtig so — früher ist besser.)
         */
        @Test
        void ueberlappendeGepinnteTermineSprengenDasModellNicht() {
            List<CalendarEvent> termine = List.of(
                    fixerBlock(MORGEN.atTime(9, 0), MORGEN.atTime(12, 0)),
                    fixerBlock(MORGEN.atTime(10, 0), MORGEN.atTime(11, 0)),
                    fixerBlock(MORGEN.atTime(11, 30), MORGEN.atTime(14, 0)));
            when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(termine);
            when(taskService.getSchedulableTasks(1L)).thenReturn(List.of(
                    unteilbar(task("Eins", 60, 3, MORGEN.atTime(17, 0))),
                    unteilbar(task("Zwei", 60, 3, MORGEN.atTime(17, 0)))));

            ScheduleResult r = lauf(MORGEN, MORGEN);

            assertEquals(2, r.getScheduledTasks().size(), "der Lauf muss eine Lösung finden");
            for (ScheduledItem i : r.getScheduledTasks()) {
                for (CalendarEvent t : termine) {
                    assertFalse(i.getStartTime().isBefore(t.getEndTime())
                                    && i.getEndTime().isAfter(t.getStartTime()),
                            "Block " + i.getStartTime() + "–" + i.getEndTime()
                                    + " überlappt den Termin " + t.getStartTime());
                }
            }
        }

        /** Eine Aufgabe, die exakt in die verbleibende Zeit passt, wird auch geplant. */
        @Test
        void einePassgenaueAufgabeWirdGeplant() {
            prefs.setWorkdayStart(LocalTime.of(8, 0));
            prefs.setWorkdayEnd(LocalTime.of(10, 0));
            prefs.setBreakDurationMinutes(0);
            when(taskService.getSchedulableTasks(1L))
                    .thenReturn(List.of(unteilbar(task("Exakt", 120, 3, MORGEN.atTime(10, 0)))));

            ScheduleResult r = lauf(MORGEN, MORGEN);

            assertEquals(MORGEN.atTime(8, 0), start(r, "Exakt"));
            assertTrue(r.getAtRisk().isEmpty());
        }
    }

    // ==================================================================
    // 9. Der Gesamteindruck — invariant über einen realistischen Bestand
    // ==================================================================

    @Nested
    @DisplayName("Gesamtbild")
    class Gesamtbild {

        /**
         * Ein voller, realistischer Bestand — und alle Zusagen auf einmal.
         *
         * Der wertvollste Test der Suite: einzeln geprüfte Regeln können sich gegenseitig
         * aushebeln, sobald sie zusammen im selben Modell stehen.
         */
        @Test
        void einVollerBestandHaeltAlleZusagenGleichzeitig() {
            pruefeBestand(4711L);
        }

        /**
         * Dieselben Zusagen über zwölf zufällig erzeugte Bestände.
         *
         * Feste Keime, damit ein Fehlschlag reproduzierbar ist: die Streuung soll Fälle finden, die
         * sich niemand ausdenkt, nicht den Test unzuverlässig machen.
         */
        @Test
        void zwoelfZufallsbestaendeHaltenDieselbenZusagen() {
            for (long keim = 1; keim <= 12; keim++) pruefeBestand(keim);
        }

        private void pruefeBestand(long keim) {
            Random rnd = new Random(keim);
            prefs.setWorkdayStart(LocalTime.of(8, 0));
            prefs.setWorkdayEnd(LocalTime.of(18, 0));
            prefs.setPersonalHoursStart(LocalTime.of(7, 0));
            prefs.setPersonalHoursEnd(LocalTime.of(22, 0));
            prefs.setBreakDurationMinutes(15);
            prefs.setMaxTaskMinutesPerDay(300);

            List<Task> aufgaben = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                LocalDateTime dl = switch (rnd.nextInt(4)) {
                    case 0 -> null;
                    case 1 -> MORGEN.plusDays(1 + rnd.nextInt(4)).atTime(17, 0);
                    case 2 -> MORGEN.plusDays(5 + rnd.nextInt(15)).atTime(17, 0);
                    default -> HEUTE.minusDays(1 + rnd.nextInt(5)).atTime(12, 0);
                };
                Task t = task("T" + keim + "_" + i, 30 + 30 * rnd.nextInt(5), 1 + rnd.nextInt(5), dl);
                if (rnd.nextBoolean()) unteilbar(t); else t.setSplittable(true);
                aufgaben.add(t);
            }
            when(taskService.getSchedulableTasks(1L)).thenReturn(aufgaben);

            List<Habit> gewohnheiten = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                gewohnheiten.add(taeglicheGewohnheit("H" + keim + "_" + i,
                        10 + 10 * rnd.nextInt(4), 1 + rnd.nextInt(5)));
            }
            when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any())).thenReturn(gewohnheiten);

            List<CalendarEvent> fest = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                int tag = 1 + rnd.nextInt(10);
                int h   = 8 + rnd.nextInt(7);
                fest.add(fixerBlock(MORGEN.plusDays(tag).atTime(h, 0),
                        MORGEN.plusDays(tag).atTime(h + 1, 0)));
            }
            when(calendarEventService.getFixedEvents(eq(1L), any(), any())).thenReturn(fest);

            ScheduleResult r = lauf(HEUTE, HEUTE.plusDays(27));
            String wo = " (Keim " + keim + ")";

            assertKeineUeberlappung(r);

            for (ScheduledItem i : r.getScheduledTasks()) {
                LocalDateTime dl = i.getTask().getDeadline();
                if (dl != null && dl.isAfter(LocalDateTime.now())) {
                    assertFalse(i.getEndTime().isAfter(dl),
                            i.getTask().getTitle() + " endet hinter seiner Deadline" + wo);
                }
                assertFalse(i.getStartTime().isBefore(LocalDateTime.now().minusMinutes(20)),
                        "Block in der Vergangenheit" + wo + ": " + i.getStartTime());
            }

            for (ScheduledItem i : alleItems(r)) {
                assertEquals(i.getStartTime().toLocalDate(), i.getEndTime().minusNanos(1).toLocalDate(),
                        "Block über Mitternacht" + wo);
                assertFalse(i.getStartTime().toLocalTime().isBefore(LocalTime.of(7, 0)),
                        "vor dem frühesten erlaubten Zeitpunkt" + wo + ": " + i.getStartTime());
                assertFalse(i.getEndTime().toLocalTime().isAfter(LocalTime.of(22, 0))
                                && !i.getEndTime().toLocalTime().equals(LocalTime.MIDNIGHT),
                        "nach dem spätesten erlaubten Zeitpunkt" + wo + ": " + i.getEndTime());
            }

            for (ScheduledItem i : alleItems(r)) {
                for (CalendarEvent f : fest) {
                    assertFalse(i.getStartTime().isBefore(f.getEndTime())
                                    && i.getEndTime().isAfter(f.getStartTime()),
                            "überplant einen gepinnten Termin" + wo);
                }
            }

            // Jede Aufgabe steht entweder im Kalender oder in der Meldung — nie in keinem von beiden.
            for (Task t : aufgaben) {
                boolean geplant = r.getScheduledTasks().stream()
                        .anyMatch(i -> i.getTask().getId().equals(t.getId()));
                boolean gemeldet = r.getAtRisk().stream()
                        .anyMatch(i -> t.getId().equals(i.getTaskId()));
                assertTrue(geplant || gemeldet,
                        t.getTitle() + " ist weder geplant noch gemeldet — lautlos verschwunden" + wo);
            }
        }

        /** Zwei Läufe ohne Änderung liefern denselben Plan — der Kalender springt nicht. */
        @Test
        void zweiLaeufeOhneAenderungLiefernDenselbenPlan() {
            List<Task> aufgaben = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                aufgaben.add(task("A" + i, 60, 1 + (i % 5), MORGEN.plusDays(3 + i).atTime(17, 0)));
            }
            when(taskService.getSchedulableTasks(1L)).thenReturn(aufgaben);
            when(habitRepository.findHabitsActiveInRange(eq(1L), any(), any()))
                    .thenReturn(List.of(taeglicheGewohnheit("Lesen", 30, 3)));

            ScheduleResult ersteR = lauf(MORGEN, MORGEN.plusDays(20));
            Map<String, LocalDateTime> ersteLage = lage(ersteR);

            // Den ersten Plan als Bestand zurückspielen, damit der Stabilitätsanker greift — das
            // ist genau der Weg, über den der Scheduler seinen eigenen letzten Lauf wiederfindet.
            when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                    eq(1L), any(), eq(false), any(), any()))
                    .thenReturn(alsEvents(ersteR));

            ScheduleResult zweiteR = lauf(MORGEN, MORGEN.plusDays(20));

            assertEquals(ersteLage, lage(zweiteR),
                    "ohne Änderung am Bestand darf sich kein Block bewegen");
        }
    }

    // ==================================================================
    // Hilfen
    // ==================================================================

    private ScheduleResult lauf(LocalDate von, LocalDate bis) {
        return service.generateOptimalSchedule(1L, von, bis);
    }

    private long id() {
        return naechsteId++;
    }

    private Task task(String titel, int minuten, int prio, LocalDateTime deadline) {
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

    private Task unteilbar(Task t) {
        t.setSplittable(false);
        return t;
    }

    private Habit taeglicheGewohnheit(String name, int minuten, int prio) {
        Habit h = new Habit();
        h.setId(id());
        h.setName(name);
        h.setTimesPerWeek(7);
        h.setDurationMinutes(minuten);
        h.setPriority(prio);
        h.setStartDate(HEUTE.minusDays(60));
        return h;
    }

    private WorkoutSession training(String name, int minuten, LocalDate wochenstart) {
        WorkoutSession w = new WorkoutSession();
        w.setId(id());
        w.setName(name);
        w.setDurationMinutes(minuten);
        w.setIsFlexible(true);
        w.setIsCompleted(false);
        w.setTargetWeekStart(wochenstart);
        return w;
    }

    /** Wie {@link #training}, aber an eine Routine mit festem Wunsch-Wochentag gebunden. */
    private WorkoutSession trainingAmWunschtag(String name, int minuten, LocalDate wochenstart,
                                               DayOfWeek wunschtag) {
        WorkoutSession w = training(name, minuten, wochenstart);
        Routine r = new Routine();
        r.setId(id());
        r.setName(name);
        r.setPreferredWeekday(wunschtag.getValue());
        w.setRoutine(r);
        return w;
    }

    private CalendarEvent fixerBlock(LocalDateTime von, LocalDateTime bis) {
        CalendarEvent e = new CalendarEvent();
        e.setId(id());
        e.setTitle("Fest");
        e.setIsFixed(true);
        e.setStartTime(von);
        e.setEndTime(bis);
        e.setEventType(EventType.OTHER);
        return e;
    }

    private List<ScheduledItem> alleItems(ScheduleResult r) {
        List<ScheduledItem> alle = new ArrayList<>(r.getScheduledTasks());
        alle.addAll(r.getScheduledHabits());
        return alle;
    }

    private List<String> titel(List<ScheduledItem> items) {
        return items.stream().map(i -> i.getTask().getTitle()).sorted().collect(Collectors.toList());
    }

    private LocalDateTime start(ScheduleResult r, String titel) {
        return r.getScheduledTasks().stream()
                .filter(i -> i.getTask().getTitle().equals(titel))
                .map(ScheduledItem::getStartTime)
                .min(LocalDateTime::compareTo)
                .orElseThrow(() -> new AssertionError("kein Block für " + titel
                        + ", geplant war: " + titel(r.getScheduledTasks())
                        + ", gemeldet: " + r.getAtRisk()));
    }

    private LocalDateTime ende(ScheduleResult r, String titel) {
        return r.getScheduledTasks().stream()
                .filter(i -> i.getTask().getTitle().equals(titel))
                .map(ScheduledItem::getEndTime)
                .max(LocalDateTime::compareTo)
                .orElseThrow(() -> new AssertionError("kein Block für " + titel));
    }

    private Map<String, LocalDateTime> lage(ScheduleResult r) {
        Map<String, LocalDateTime> out = new HashMap<>();
        for (ScheduledItem i : r.getScheduledTasks()) {
            out.put("task:" + i.getTask().getId() + ":" + i.getChunkIndex(), i.getStartTime());
        }
        for (ScheduledItem i : r.getScheduledHabits()) {
            if (i.getHabit() == null) continue;
            out.put("habit:" + i.getHabit().getId() + ":" + i.getStartTime().toLocalDate(),
                    i.getStartTime());
        }
        return out;
    }

    /** Den Plan als Kalendereinträge zurückspielen, damit der nächste Lauf einen Bestand sieht. */
    private List<CalendarEvent> alsEvents(ScheduleResult r) {
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

    private void assertKeineUeberlappung(ScheduleResult r) {
        List<ScheduledItem> alle = alleItems(r).stream()
                .sorted(Comparator.comparing(ScheduledItem::getStartTime))
                .collect(Collectors.toList());
        for (int i = 1; i < alle.size(); i++) {
            ScheduledItem a = alle.get(i - 1);
            ScheduledItem b = alle.get(i);
            assertFalse(b.getStartTime().isBefore(a.getEndTime()),
                    "Überlappung: " + a.getStartTime() + "–" + a.getEndTime()
                            + " und " + b.getStartTime() + "–" + b.getEndTime());
        }
    }

    /** Nur damit der Import von DayOfWeek/TemporalAdjusters nicht ungenutzt bleibt. */
    @SuppressWarnings("unused")
    private LocalDate montagNach(LocalDate d) {
        return d.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }
}
