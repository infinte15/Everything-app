package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import com.Finn.everything_app.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.Finn.everything_app.event.ScheduleChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final StudyGoalRepository studyGoalRepository;
    private final StudyGoalService studyGoalService;
    private final TaskService taskService;

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

    /**
     * Abgeleitete Termine: sie entstehen aus dem Stundenplan und werden bei jeder Neuplanung neu
     * erzeugt. Eine Änderung hier wäre spätestens nach zwei Sekunden wieder weg, und ein
     * gepinnter CLASS-Termin überlebte sogar {@link #clearClassEvents} dauerhaft und verdoppelte
     * sich bei jedem Lauf. Deshalb ein klarer Fehler statt stiller Wirkungslosigkeit.
     */
    private void requireEditableFromCalendar(CalendarEvent event) {
        if (event.getEventType() == EventType.CLASS) {
            throw new BadRequestException("Vorlesungen werden im Stundenplan bearbeitet.");
        }
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
        requireEditableFromCalendar(event);

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
        requireEditableFromCalendar(event);

        event.setIsFixed(pinned);
        CalendarEvent saved = calendarEventRepository.save(event);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return saved;
    }

    /**
     * Hakt einen Block ab oder nimmt das zurück und schreibt seine Minuten gut.
     *
     * <p>Genau <b>eine</b> Gutschrift je Block: gehört der Task zu einem Lernziel, wandern die
     * Minuten über {@link StudyGoalService#applyLoggedDelta} dorthin — dessen {@code syncTask}
     * rechnet {@code estimatedDurationMinutes} aus den Reststunden neu. Sonst wachsen
     * {@link Task#getCompletedMinutes()}. Beides zusammen wäre eine Doppelbuchung.
     *
     * <p>Der Block selbst zählt danach nirgends mehr als gepinnte Zeit (siehe
     * {@code SmartSchedulerService.pinnedMinutesPerTask}) — sonst wäre der Abzug wieder doppelt.
     * Er sperrt seine Zeit aber weiter, damit kein neuer Block darüber gelegt wird.
     *
     * <p>Angenehme Folge: einen bereits begonnenen Block abzuhaken ist für den Planer neutral
     * (er zählte vorher als eingefrorene Minuten, danach als Gutschrift derselben Höhe). Einen
     * künftigen Block abzuhaken verkleinert das Restbudget und hält die Zeit trotzdem belegt.
     */
    @Transactional
    public CalendarEvent setCompleted(Long id, Long userId, boolean completed) {
        CalendarEvent event = getEventById(id);
        requireOwner(event, userId);
        requireEditableFromCalendar(event);

        if (event.getEventType() != EventType.TASK) {
            // Gewohnheiten und Workouts haben eigene Abschlusswege
            // (POST /habits/{id}/complete bzw. PUT /sports/sessions/{id}/complete).
            // Ein zweiter Weg über den Kalender stritte mit ihnen um denselben Zustand.
            throw new BadRequestException("Nur Aufgabenblöcke lassen sich abhaken.");
        }

        // Ohne diesen Wächter bucht ein Doppeltipp zweimal.
        if ((event.getCompletedAt() != null) == completed) {
            return event;
        }

        creditBlock(event, userId, completed);

        event.setCompletedAt(completed ? LocalDateTime.now() : null);
        CalendarEvent saved = calendarEventRepository.save(event);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return saved;
    }

    /** Schreibt die Minuten des Blocks gut ({@code completed}) oder zieht sie wieder ab. */
    private void creditBlock(CalendarEvent event, Long userId, boolean completed) {
        Task task = event.getRelatedTask();
        if (task == null || event.getStartTime() == null || event.getEndTime() == null) return;

        int minutes = (int) java.time.temporal.ChronoUnit.MINUTES
                .between(event.getStartTime(), event.getEndTime());
        int delta = completed ? minutes : -minutes;

        Optional<StudyGoal> goal = studyGoalRepository.findByTaskIdAndUserId(task.getId(), userId);
        if (goal.isPresent()) {
            studyGoalService.applyLoggedDelta(goal.get(), delta / 60.0);
            return;
        }

        int done = task.getCompletedMinutes() != null ? task.getCompletedMinutes() : 0;
        int updated = Math.max(0, done + delta);

        Task patch = new Task();
        patch.setCompletedMinutes(updated);
        // Ist alles abgehakt, liefert chunkSizes eine leere Liste: der Task haette dann gar
        // keine Kalenderpraesenz mehr und stuende trotzdem auf offen. Also gleich abschliessen.
        int estimated = task.getEstimatedDurationMinutes() != null
                ? task.getEstimatedDurationMinutes() : 0;
        if (completed && estimated > 0 && updated >= estimated) {
            taskService.updateTask(task.getId(), patch);
            taskService.completeTask(task.getId());
        } else {
            patch.setStatus(TaskStatus.TODO);
            taskService.updateTask(task.getId(), patch);
        }
    }

    @Transactional
    public void deleteEvent(Long id, Long userId) {
        CalendarEvent event = getEventById(id);
        requireOwner(event, userId);
        requireEditableFromCalendar(event);
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
        // Erledigte Blöcke bleiben stehen. Sie sind Protokoll, keine Planung — die entprellte
        // Neuplanung soll sie nicht zwei Sekunden nach dem Haken wieder wegräumen.
        //
        // Gefiltert in eine neue Liste statt removeIf: was das Repository zurückgibt, muss
        // nicht veränderbar sein.
        calendarEventRepository.deleteAll(events.stream()
                .filter(e -> e.getCompletedAt() == null)
                .toList());
    }

    /**
     * Räumt die aus dem Stundenplan abgeleiteten Termine weg.
     *
     * Bewusst getrennt von {@link #clearScheduledEvents}: die Vorlesungen werden auch dann
     * synchronisiert, wenn der Solver gar nicht läuft, und dürfen die Aufräumabfrage der
     * geplanten Blöcke nicht mitbenutzen.
     */
    @Transactional
    public void clearClassEvents(Long userId, LocalDateTime start, LocalDateTime end) {
        calendarEventRepository.deleteAll(
                calendarEventRepository.findByUserIdAndEventTypeInAndIsFixedAndStartTimeBetween(
                        userId, List.of(EventType.CLASS), false, start, end));
    }
}