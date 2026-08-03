package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.Finn.everything_app.event.ScheduleChangedEvent;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final WorkoutSessionRepository workoutSessionRepository;

    private final ApplicationEventPublisher eventPublisher;

    public List<CalendarEvent> getEventsInRange(Long userId, LocalDateTime start, LocalDateTime end) {
        return calendarEventRepository.findByUserIdAndStartTimeBetween(userId, start, end);
    }

    public List<CalendarEvent> getFixedEvents(Long userId, LocalDateTime start, LocalDateTime end) {
        return calendarEventRepository.findFixedEvents(userId, start, end);
    }

    public boolean isTimeSlotFree(Long userId, LocalDateTime start, LocalDateTime end) {
        Long overlapping = calendarEventRepository.countOverlappingEvents(userId, start, end);
        return overlapping == 0;
    }

    @Transactional
    public CalendarEvent createEvent(Long userId, CalendarEvent event) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        event.setUser(user);

        if (event.getIsFixed() == null) {
            event.setIsFixed(false);
        }

        if (event.getEventType() == null) {
            event.setEventType(EventType.OTHER);
        }

        // When creating a TASK-type event, also create a corresponding Task
        if (event.getEventType() == EventType.TASK && event.getRelatedTask() == null) {
            Task task = new Task();
            task.setUser(user);
            task.setTitle(event.getTitle());
            task.setDescription(event.getDescription());
            task.setPriority(3);
            task.setEstimatedDurationMinutes(
                    (int) java.time.Duration.between(event.getStartTime(), event.getEndTime()).toMinutes());
            task.setDeadline(event.getEndTime());
            task.setScheduledStartTime(event.getStartTime());
            task.setScheduledEndTime(event.getEndTime());
            task.setStatus(TaskStatus.TODO);
            task.setSpaceType(SpaceType.TASKS);
            task.setCreatedAt(LocalDateTime.now());

            Task savedTask = taskRepository.save(task);
            event.setRelatedTask(savedTask);
        }

        CalendarEvent savedEvent = calendarEventRepository.save(event);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return savedEvent;
    }

    public CalendarEvent getEventById(Long id) {
        return calendarEventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event nicht gefunden"));
    }

    /**
     * Typen, die der Smart Scheduler selbst anlegt und bei jeder Neuplanung wieder wegräumt
     * (siehe {@link #clearScheduledEvents}). Genau diese müssen beim manuellen Verschieben
     * gepinnt werden, damit die Änderung den nächsten Solver-Lauf überlebt.
     */
    private boolean isSchedulerOwned(EventType type) {
        return type == EventType.TASK || type == EventType.HABIT || type == EventType.WORKOUT;
    }

    /** Stellt sicher, dass ein Event dem anfragenden Nutzer gehört. */
    private void requireOwner(CalendarEvent event, Long userId) {
        if (userId == null || event.getUser() == null || !userId.equals(event.getUser().getId())) {
            throw new RuntimeException("Kein Zugriff auf dieses Event");
        }
    }

    @Transactional
    public CalendarEvent updateEvent(Long id, Long userId, CalendarEvent updatedEvent) {
        CalendarEvent event = getEventById(id);
        requireOwner(event, userId);

        // Vor dem Patchen merken, damit unten unterschieden werden kann zwischen
        // "verschoben" (pinnen) und "nur umbenannt" (nicht pinnen).
        LocalDateTime originalStart = event.getStartTime();
        LocalDateTime originalEnd   = event.getEndTime();

        if (updatedEvent.getTitle() != null) {
            event.setTitle(updatedEvent.getTitle());
        }
        if (updatedEvent.getDescription() != null) {
            event.setDescription(updatedEvent.getDescription());
        }
        if (updatedEvent.getStartTime() != null) {
            event.setStartTime(updatedEvent.getStartTime());
        }
        if (updatedEvent.getEndTime() != null) {
            event.setEndTime(updatedEvent.getEndTime());
        }
        if (updatedEvent.getLocation() != null) {
            event.setLocation(updatedEvent.getLocation());
        }
        if (updatedEvent.getEventType() != null) {
            event.setEventType(updatedEvent.getEventType());
        }
        if (updatedEvent.getIsFixed() != null) {
            event.setIsFixed(updatedEvent.getIsFixed());
        }
        if (updatedEvent.getColor() != null) {
            event.setColor(updatedEvent.getColor());
        }
        if (updatedEvent.getNotes() != null) {
            event.setNotes(updatedEvent.getNotes());
        }

        // Nur ein tatsächliches Verschieben pinnt den Termin. Vorher hat auch ein reines Umbenennen
        // aus dem Edit-Sheet den Task festgenagelt, sodass der Scheduler ihn nie wieder anfasste.
        boolean timesChanged = !java.util.Objects.equals(originalStart, event.getStartTime())
                || !java.util.Objects.equals(originalEnd, event.getEndTime());
        if (timesChanged && isSchedulerOwned(event.getEventType())) {
            // Ohne dieses Pinnen war Drag-and-Drop für HABIT und WORKOUT wirkungslos: die
            // entprellte Neuplanung löscht wenige Sekunden später alle nicht gepinnten
            // Scheduler-Events und legt sie an der vom Solver gewählten Stelle wieder an —
            // der Block sprang also vor den Augen des Nutzers an seinen alten Platz zurück.
            event.setIsFixed(true);
        }

        // Der Solver liest Workouts aus der WorkoutSession, nicht aus dem Kalender-Event.
        // Ohne diesen Abgleich bliebe die Session an ihrer alten Zeit stehen und würde als
        // blockierter Bereich an der falschen Stelle im Modell landen.
        if (timesChanged && event.getRelatedWorkout() != null) {
            WorkoutSession session = event.getRelatedWorkout();
            session.setStartTime(event.getStartTime());
            session.setEndTime(event.getEndTime());
            workoutSessionRepository.save(session);
        }

        CalendarEvent savedEvent = calendarEventRepository.save(event);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, event.getUser().getId()));
        return savedEvent;
    }

    /**
     * Pinnt ein Event fest oder gibt es wieder frei.
     *
     * Das Freigeben allein genügt: das Pin-Protokoll ist "es existiert kein fixes CalendarEvent
     * für diesen Task", und clearScheduledEvents löscht beim nächsten Lauf genau die nicht-fixen
     * Events und legt sie an der vom Solver gewählten Stelle neu an.
     */
    @Transactional
    public CalendarEvent setPinned(Long id, Long userId, boolean pinned) {
        CalendarEvent event = getEventById(id);
        requireOwner(event, userId);

        event.setIsFixed(pinned);
        CalendarEvent saved = calendarEventRepository.save(event);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return saved;
    }

    @Transactional
    public void deleteEvent(Long id, Long userId) {
        CalendarEvent event = getEventById(id);
        requireOwner(event, userId);
        calendarEventRepository.delete(event);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
    }

    @Transactional
    public CalendarEvent createEventFromTask(Task task, LocalDateTime startTime, LocalDateTime endTime) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(task.getUser());
        event.setTitle(task.getTitle());
        event.setDescription(task.getDescription());
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setEventType(EventType.TASK);
        event.setIsFixed(false);
        event.setRelatedTask(task);

        return calendarEventRepository.save(event);
    }

    @Transactional
    public CalendarEvent createEventFromHabit(Habit habit, LocalDateTime startTime, LocalDateTime endTime) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(habit.getUser());
        event.setTitle(habit.getName());
        event.setDescription(habit.getDescription());
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setEventType(EventType.HABIT);
        event.setIsFixed(false);
        event.setRelatedHabit(habit);

        return calendarEventRepository.save(event);
    }

    @Transactional
    public CalendarEvent createEventFromWorkout(WorkoutSession workout) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(workout.getUser());
        event.setTitle(workout.getName());
        event.setDescription(workout.getDescription());
        event.setStartTime(workout.getStartTime());
        event.setEndTime(workout.getEndTime());
        event.setLocation(workout.getLocation());
        event.setEventType(EventType.WORKOUT);
        event.setIsFixed(false);
        event.setRelatedWorkout(workout);

        return calendarEventRepository.save(event);
    }

    @Transactional
    public void deleteNonFixedEventsInRange(Long userId, LocalDateTime start, LocalDateTime end) {
        List<CalendarEvent> events = getEventsInRange(userId, start, end);

        for (CalendarEvent event : events) {
            if (!event.getIsFixed()) {
                calendarEventRepository.delete(event);
            }
        }
    }

    @Transactional
    public void clearScheduledEvents(Long userId, LocalDateTime start, LocalDateTime end) {
        List<CalendarEvent> events = calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                userId,
                List.of(EventType.TASK, EventType.HABIT, EventType.WORKOUT),
                false,
                start,
                end);
        calendarEventRepository.deleteAll(events);
    }
}