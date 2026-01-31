package com.Finn.everything_app.service;

import com.Finn.everything_app.model.CalendarEvent;
import com.Finn.everything_app.model.EventType;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;



@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final UserService userService;

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
        User user = userService.findById(userId);
        event.setUser(user);

        if (event.getStartTime().isAfter(event.getEndTime())) {
            throw new RuntimeException("Startzeit muss vor Endzeit liegen");
        }

        if (Boolean.TRUE.equals(event.getIsFixed())) {
            if (!isTimeSlotFree(userId, event.getStartTime(), event.getEndTime())) {
                throw new RuntimeException("Zeitraum ist bereits belegt");
            }
        }

        return calendarEventRepository.save(event);
    }

    @Transactional
    public void deleteEvent(Long eventId) {
        calendarEventRepository.deleteById(eventId);
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