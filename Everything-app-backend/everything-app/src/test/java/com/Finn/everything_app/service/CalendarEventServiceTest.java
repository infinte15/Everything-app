package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarEventServiceTest {

    @Mock CalendarEventRepository calendarEventRepository;
    @Mock UserRepository userRepository;
    @Mock TaskRepository taskRepository;
    @Mock WorkoutSessionRepository workoutSessionRepository;
    @Mock StudyGoalRepository studyGoalRepository;
    @Mock StudyGoalService studyGoalService;
    @Mock TaskService taskService;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    CalendarEventService service;

    // Guards the bug fix: repeated schedule regeneration must not duplicate HABIT/WORKOUT
    // calendar events, since the old query only ever cleared TASK-typed events.
    @Test
    void clearScheduledEventsCoversTaskHabitWorkoutAndProjectWithinBounds() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 3, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 9, 23, 59, 59);
        List<EventType> owned = List.of(EventType.TASK, EventType.HABIT, EventType.WORKOUT, EventType.PROJECT);

        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), eq(owned), eq(false), eq(start), eq(end)))
                .thenReturn(List.of());

        service.clearScheduledEvents(1L, start, end);

        verify(calendarEventRepository).findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                1L, owned, false, start, end);
        verify(calendarEventRepository).deleteAll(List.of());
    }

    // ------------------------------------------------------------------
    // Drag-and-Drop: ein manuell verschobener Termin muss den nächsten Solver-Lauf überleben
    // ------------------------------------------------------------------

    @Test
    void movingAHabitEventPinsIt() {
        CalendarEvent stored = existingEvent(EventType.HABIT, LocalDateTime.of(2026, 8, 4, 7, 0),
                LocalDateTime.of(2026, 8, 4, 7, 30));

        CalendarEvent moved = service.updateEvent(5L, 1L,
                patch(EventType.HABIT, LocalDateTime.of(2026, 8, 4, 18, 0),
                        LocalDateTime.of(2026, 8, 4, 18, 30)));

        assertTrue(moved.getIsFixed(),
                "ohne Pin löscht die nächste Neuplanung das Event und legt es woanders neu an");
        assertEquals(LocalDateTime.of(2026, 8, 4, 18, 0), moved.getStartTime());
        assertSame(stored, moved);
    }

    @Test
    void movingAProjectEventPinsIt() {
        CalendarEvent stored = existingEvent(EventType.PROJECT, LocalDateTime.of(2026, 8, 4, 9, 0),
                LocalDateTime.of(2026, 8, 4, 10, 0));

        CalendarEvent moved = service.updateEvent(5L, 1L,
                patch(EventType.PROJECT, LocalDateTime.of(2026, 8, 4, 17, 0),
                        LocalDateTime.of(2026, 8, 4, 18, 0)));

        assertTrue(moved.getIsFixed(),
                "ohne Pin zieht der Solver die Projektzeit sofort wieder an ihren alten Platz");
        assertEquals(LocalDateTime.of(2026, 8, 4, 17, 0), moved.getStartTime());
        assertSame(stored, moved);
    }

    @Test
    void movingAWorkoutEventPinsItAndSyncsTheSession() {
        WorkoutSession session = new WorkoutSession();
        session.setId(77L);
        session.setStartTime(LocalDateTime.of(2026, 8, 4, 7, 0));
        session.setEndTime(LocalDateTime.of(2026, 8, 4, 8, 0));

        CalendarEvent stored = existingEvent(EventType.WORKOUT, session.getStartTime(), session.getEndTime());
        stored.setRelatedWorkout(session);

        service.updateEvent(5L, 1L,
                patch(EventType.WORKOUT, LocalDateTime.of(2026, 8, 4, 19, 0),
                        LocalDateTime.of(2026, 8, 4, 20, 0)));

        assertTrue(stored.getIsFixed());
        // Der Solver liest Workouts aus der Session, nicht aus dem Kalender-Event — bliebe die
        // Session stehen, läge der blockierte Bereich im Modell an der alten Stelle.
        assertEquals(LocalDateTime.of(2026, 8, 4, 19, 0), session.getStartTime());
        assertEquals(LocalDateTime.of(2026, 8, 4, 20, 0), session.getEndTime());
        verify(workoutSessionRepository).save(session);
    }

    @Test
    void renamingAnEventDoesNotPinIt() {
        CalendarEvent stored = existingEvent(EventType.HABIT, LocalDateTime.of(2026, 8, 4, 7, 0),
                LocalDateTime.of(2026, 8, 4, 7, 30));

        // Gleiche Zeiten, neuer Titel — das schickt das Edit-Sheet.
        CalendarEvent patch = patch(EventType.HABIT, stored.getStartTime(), stored.getEndTime());
        patch.setTitle("Neuer Name");

        CalendarEvent renamed = service.updateEvent(5L, 1L, patch);

        assertFalse(renamed.getIsFixed(),
                "ein reines Umbenennen darf den Scheduler nicht dauerhaft aussperren");
        assertEquals("Neuer Name", renamed.getTitle());
        assertSame(stored, renamed);
        verify(workoutSessionRepository, never()).save(any());
    }

    @Test
    void movingAPlainEventLeavesItUnpinned() {
        // OTHER-Events legt der Scheduler nie an und räumt sie nie weg — hier wäre ein
        // automatisches Pinnen nur eine irreführende Zustandsänderung.
        CalendarEvent stored = existingEvent(EventType.OTHER, LocalDateTime.of(2026, 8, 4, 7, 0),
                LocalDateTime.of(2026, 8, 4, 8, 0));
        stored.setIsFixed(false);

        CalendarEvent moved = service.updateEvent(5L, 1L,
                patch(EventType.OTHER, LocalDateTime.of(2026, 8, 4, 9, 0),
                        LocalDateTime.of(2026, 8, 4, 10, 0)));

        assertFalse(moved.getIsFixed());
    }

    // ------------------------------------------------------------------
    // Vorlesungen sind aus dem Stundenplan abgeleitet und im Kalender schreibgeschützt.
    //
    // Nicht nur Bequemlichkeit: eine Änderung hier wäre nach der entprellten Neuplanung
    // ohnehin weg, und ein gepinnter CLASS-Termin überlebte clearClassEvents dauerhaft und
    // verdoppelte sich bei jedem Lauf. Ein Fehler ist ehrlicher als stille Wirkungslosigkeit.
    // ------------------------------------------------------------------

    /** Ein gespeicherter Termin ohne save()-Stub — die Sperre darf gar nicht erst speichern. */
    private CalendarEvent storedLecture() {
        User owner = new User();
        owner.setId(1L);

        CalendarEvent event = new CalendarEvent();
        event.setId(5L);
        event.setUser(owner);
        event.setTitle("Analysis I");
        event.setEventType(EventType.CLASS);
        event.setIsFixed(false);
        event.setStartTime(LocalDateTime.of(2026, 8, 4, 8, 0));
        event.setEndTime(LocalDateTime.of(2026, 8, 4, 10, 0));

        when(calendarEventRepository.findById(5L)).thenReturn(Optional.of(event));
        return event;
    }

    @Test
    void eineVorlesungLaesstSichNichtVerschieben() {
        storedLecture();

        assertThrows(BadRequestException.class, () -> service.updateEvent(5L, 1L,
                patch(EventType.CLASS, LocalDateTime.of(2026, 8, 4, 14, 0),
                        LocalDateTime.of(2026, 8, 4, 16, 0))));

        verify(calendarEventRepository, never()).save(any());
    }

    @Test
    void eineVorlesungLaesstSichNichtPinnen() {
        CalendarEvent lecture = storedLecture();

        assertThrows(BadRequestException.class, () -> service.setPinned(5L, 1L, true));

        assertFalse(lecture.getIsFixed(), "gepinnt entkäme sie dem Aufräumen und verdoppelte sich");
        verify(calendarEventRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Überspringen
    // ------------------------------------------------------------------

    @Test
    void eineGewohnheitLaesstSichUeberspringenUndZurueckholen() {
        CalendarEvent block = storedOwned(EventType.HABIT);

        CalendarEvent skipped = service.setSkipped(5L, 1L, true);
        assertNotNull(skipped.getSkippedAt(), "der Block muss als übersprungen markiert sein");

        // Zurücknehmen: der Zeitstempel verschwindet wieder.
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(i -> i.getArgument(0));
        CalendarEvent restored = service.setSkipped(5L, 1L, false);
        assertNull(restored.getSkippedAt(), "das Zurücknehmen muss den Block wiederherstellen");
    }

    @Test
    void einAufgabenblockLaesstSichNichtUeberspringen() {
        CalendarEvent block = storedOwned(EventType.TASK);

        assertThrows(BadRequestException.class, () -> service.setSkipped(5L, 1L, true));

        assertNull(block.getSkippedAt(),
                "ein Task-Block ist ein Bruchteil einer Aufgabe — seine Restminuten bleiben");
        verify(calendarEventRepository, never()).save(any());
    }

    @Test
    void einUebersprungenesWorkoutMarkiertAuchSeineSession() {
        CalendarEvent block = storedOwned(EventType.WORKOUT);
        WorkoutSession session = new WorkoutSession();
        session.setId(42L);
        session.setIsFlexible(true);
        block.setRelatedWorkout(session);

        service.setSkipped(5L, 1L, true);

        assertTrue(session.getIsSkipped(),
                "der Solver liest Trainings aus der Session — ohne Abgleich wäre es sofort zurück");
        verify(workoutSessionRepository).save(session);
    }

    @Test
    void zweimalUeberspringenSchreibtNichtZweimal() {
        CalendarEvent block = storedOwned(EventType.PROJECT);

        service.setSkipped(5L, 1L, true);
        clearInvocations(calendarEventRepository);
        service.setSkipped(5L, 1L, true);

        verify(calendarEventRepository, never()).save(any());
    }

    @Test
    void uebersprungeneBloeckeUeberlebenDasAufraeumen() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 3, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 9, 23, 59, 59);

        CalendarEvent offen = new CalendarEvent();
        offen.setId(1L);
        CalendarEvent uebersprungen = new CalendarEvent();
        uebersprungen.setId(2L);
        uebersprungen.setSkippedAt(LocalDateTime.of(2026, 8, 4, 9, 0));

        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), anyList(), eq(false), any(), any()))
                .thenReturn(List.of(offen, uebersprungen));

        service.clearScheduledEvents(1L, start, end);

        ArgumentCaptor<List<CalendarEvent>> deleted = ArgumentCaptor.forClass(List.class);
        verify(calendarEventRepository).deleteAll(deleted.capture());
        assertEquals(List.of(offen), deleted.getValue(),
                "würde der übersprungene Block hier verschwinden, käme sofort Ersatz");
    }

    /** Ein vom Scheduler verwalteter Block des Testnutzers. */
    private CalendarEvent storedOwned(EventType type) {
        User owner = new User();
        owner.setId(1L);

        CalendarEvent event = new CalendarEvent();
        event.setId(5L);
        event.setUser(owner);
        event.setTitle("Block");
        event.setEventType(type);
        event.setIsFixed(false);
        event.setStartTime(LocalDateTime.of(2026, 8, 4, 8, 0));
        event.setEndTime(LocalDateTime.of(2026, 8, 4, 9, 0));

        when(calendarEventRepository.findById(5L)).thenReturn(Optional.of(event));
        lenient().when(calendarEventRepository.save(any(CalendarEvent.class)))
                 .thenAnswer(i -> i.getArgument(0));
        return event;
    }

    @Test
    void eineVorlesungLaesstSichNichtLoeschen() {
        storedLecture();

        assertThrows(BadRequestException.class, () -> service.deleteEvent(5L, 1L));

        verify(calendarEventRepository, never()).delete(any());
    }

    @Test
    void clearClassEventsRaeumtNurAbgeleiteteTermineWeg() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 3, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 9, 23, 59, 59);

        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                1L, List.of(EventType.CLASS), false, start, end)).thenReturn(List.of());

        service.clearClassEvents(1L, start, end);

        // Getrennt von clearScheduledEvents: die Vorlesungen werden auch synchronisiert, wenn
        // der Solver gar nicht läuft, und dürfen dessen Aufräumabfrage nicht mitbenutzen.
        verify(calendarEventRepository).findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                1L, List.of(EventType.CLASS), false, start, end);
        verify(calendarEventRepository).deleteAll(List.of());
    }

    // ------------------------------------------------------------------
    // Abhaken: die Minuten eines Blocks werden GENAU EINMAL gutgeschrieben.
    //
    // Die Falle: pinnedMinutesPerTask summiert fixedEvents + frozenEvents. Wuerde ein
    // erledigter Block dort mitzaehlen und gleichzeitig ueber logHours gutgeschrieben, zoege
    // der Solver dieselben Minuten zweimal ab.
    // ------------------------------------------------------------------

    private static final long TASK_ID = 42L;

    /** Ein 90-Minuten-Aufgabenblock in der Zukunft, mit verdrahtetem Lookup. */
    private CalendarEvent taskBlock(Task task) {
        User owner = new User();
        owner.setId(1L);

        CalendarEvent event = new CalendarEvent();
        event.setId(9L);
        event.setUser(owner);
        event.setTitle("Lernen: Analysis I (1/4)");
        event.setEventType(EventType.TASK);
        event.setIsFixed(false);
        event.setStartTime(LocalDateTime.of(2026, 8, 6, 9, 0));
        event.setEndTime(LocalDateTime.of(2026, 8, 6, 10, 30));
        event.setRelatedTask(task);

        when(calendarEventRepository.findById(9L)).thenReturn(Optional.of(event));
        lenient().when(calendarEventRepository.save(event)).thenReturn(event);
        return event;
    }

    private Task task(int estimated, Integer done) {
        Task t = new Task();
        t.setId(TASK_ID);
        t.setEstimatedDurationMinutes(estimated);
        t.setCompletedMinutes(done);
        return t;
    }

    private Task capturedTaskPatch() {
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).updateTask(eq(TASK_ID), captor.capture());
        return captor.getValue();
    }

    @Test
    void einErledigterBlockUeberlebtDieNeuplanung() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 3, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 9, 23, 59, 59);

        CalendarEvent offen = new CalendarEvent();
        offen.setId(1L);
        CalendarEvent erledigt = new CalendarEvent();
        erledigt.setId(2L);
        erledigt.setCompletedAt(LocalDateTime.of(2026, 8, 5, 12, 0));

        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), any(), eq(false), eq(start), eq(end)))
                .thenReturn(List.of(offen, erledigt));

        service.clearScheduledEvents(1L, start, end);

        // Nur der offene Block faellt weg — der erledigte ist Protokoll, keine Planung.
        verify(calendarEventRepository).deleteAll(List.of(offen));
    }

    @Test
    void einErledigterBlockSchreibtCompletedMinutesFort() {
        taskBlock(task(360, 60));

        service.setCompleted(9L, 1L, true);

        assertEquals(150, capturedTaskPatch().getCompletedMinutes(), "60 vorher plus 90 Minuten Block");
        verifyNoInteractions(studyGoalService);
    }

    @Test
    void einErledigterLernblockSchreibtAnsZielUndNichtAnDenTask() {
        Task bridge = task(360, null);
        taskBlock(bridge);

        StudyGoal goal = new StudyGoal();
        goal.setId(7L);
        when(studyGoalRepository.findByTaskIdAndUserId(TASK_ID, 1L)).thenReturn(Optional.of(goal));

        service.setCompleted(9L, 1L, true);

        // 90 Minuten = 1,5 Stunden ans Ziel. logHours rechnet estimatedDurationMinutes neu —
        // zusaetzlich completedMinutes zu setzen waere derselbe Abzug ein zweites Mal.
        verify(studyGoalService).applyLoggedDelta(goal, 1.5);
        verify(taskService, never()).updateTask(anyLong(), any());
        verify(taskService, never()).completeTask(anyLong());
    }

    @Test
    void derLetzteBlockSchliesstDenTaskAb() {
        taskBlock(task(90, 0));

        service.setCompleted(9L, 1L, true);

        // Sonst liefert chunkSizes eine leere Liste: der Task haette keine Kalenderpraesenz
        // mehr und stuende trotzdem auf offen.
        verify(taskService).completeTask(TASK_ID);
    }

    @Test
    void zweimalAbhakenBuchtNurEinmal() {
        CalendarEvent event = taskBlock(task(360, 0));
        event.setCompletedAt(LocalDateTime.of(2026, 8, 5, 12, 0));

        CalendarEvent unveraendert = service.setCompleted(9L, 1L, true);

        assertSame(event, unveraendert);
        verifyNoInteractions(taskService);
        verify(calendarEventRepository, never()).save(any());
    }

    @Test
    void dasZuruecknehmenZiehtDieGutschriftAb() {
        CalendarEvent event = taskBlock(task(360, 150));
        event.setCompletedAt(LocalDateTime.of(2026, 8, 5, 12, 0));

        CalendarEvent saved = service.setCompleted(9L, 1L, false);

        assertEquals(60, capturedTaskPatch().getCompletedMinutes(), "150 minus 90 Minuten Block");
        assertNull(saved.getCompletedAt());
    }

    @Test
    void nurAufgabenbloeckeLassenSichAbhaken() {
        User owner = new User();
        owner.setId(1L);
        CalendarEvent habit = new CalendarEvent();
        habit.setId(9L);
        habit.setUser(owner);
        habit.setEventType(EventType.HABIT);
        when(calendarEventRepository.findById(9L)).thenReturn(Optional.of(habit));

        // Gewohnheiten haben ihren eigenen Abschlussweg; ein zweiter stritte darum.
        assertThrows(BadRequestException.class, () -> service.setCompleted(9L, 1L, true));
        verify(calendarEventRepository, never()).save(any());
    }

    @Test
    void eineVorlesungLaesstSichNichtAbhaken() {
        storedLecture();

        assertThrows(BadRequestException.class, () -> service.setCompleted(5L, 1L, true));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Legt ein gespeichertes Event an und verdrahtet Lookup und save() darauf. */
    private CalendarEvent existingEvent(EventType type, LocalDateTime start, LocalDateTime end) {
        User user = new User();
        user.setId(1L);

        CalendarEvent event = new CalendarEvent();
        event.setId(5L);
        event.setTitle("Bestehend");
        event.setUser(user);
        event.setEventType(type);
        event.setIsFixed(false);
        event.setStartTime(start);
        event.setEndTime(end);

        when(calendarEventRepository.findById(5L)).thenReturn(Optional.of(event));
        when(calendarEventRepository.save(event)).thenReturn(event);
        return event;
    }

    /**
     * Bildet nach, was beim Drop tatsächlich ankommt: das Frontend schickt das komplette
     * Event mit neuen Zeiten — inklusive {@code isFixed: false}, dem Stand VOR dem Ziehen.
     * Genau deshalb muss das Pinnen im Service passieren und nicht im Client.
     */
    private CalendarEvent patch(EventType type, LocalDateTime start, LocalDateTime end) {
        CalendarEvent patch = new CalendarEvent();
        patch.setTitle("Bestehend");
        patch.setEventType(type);
        patch.setIsFixed(false);
        patch.setStartTime(start);
        patch.setEndTime(end);
        return patch;
    }
}
