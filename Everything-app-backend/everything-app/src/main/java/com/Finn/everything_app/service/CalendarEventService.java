package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;



@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final HabitRepository habitRepository;
    private final WorkoutSessionRepository workoutSessionRepository;


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

        return calendarEventRepository.save(event);
    }

    public CalendarEvent getEventById(Long id) {
        return calendarEventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event nicht gefunden"));
    }

    @Transactional
    public CalendarEvent updateEvent(Long id, CalendarEvent updatedEvent) {
        CalendarEvent event = getEventById(id);

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

        return calendarEventRepository.save(event);
    }

    @Transactional
    public void deleteEvent(Long id) {
        CalendarEvent event = getEventById(id);
        calendarEventRepository.delete(event);
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
    public void clearScheduledEvents(Long userId) {
        List<CalendarEvent> events = calendarEventRepository.findByUserIdAndEventType(
                userId,
                EventType.TASK
        );
        calendarEventRepository.deleteAll(events);
    }
}