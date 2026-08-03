package com.Finn.everything_app.service;

import com.Finn.everything_app.model.CalendarEvent;
import com.Finn.everything_app.model.EventType;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.model.WorkoutSession;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarEventServiceTest {

    @Mock CalendarEventRepository calendarEventRepository;
    @Mock UserRepository userRepository;
    @Mock TaskRepository taskRepository;
    @Mock WorkoutSessionRepository workoutSessionRepository;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    CalendarEventService service;

    // Guards the bug fix: repeated schedule regeneration must not duplicate HABIT/WORKOUT
    // calendar events, since the old query only ever cleared TASK-typed events.
    @Test
    void clearScheduledEventsCoversTaskHabitAndWorkoutWithinBounds() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 3, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 9, 23, 59, 59);

        when(calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                eq(1L), eq(List.of(EventType.TASK, EventType.HABIT, EventType.WORKOUT)), eq(false), eq(start), eq(end)))
                .thenReturn(List.of());

        service.clearScheduledEvents(1L, start, end);

        verify(calendarEventRepository).findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                1L, List.of(EventType.TASK, EventType.HABIT, EventType.WORKOUT), false, start, end);
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
