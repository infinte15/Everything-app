package com.Finn.everything_app.service;

import com.Finn.everything_app.model.EventType;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarEventServiceTest {

    @Mock CalendarEventRepository calendarEventRepository;
    @Mock UserRepository userRepository;
    @Mock TaskRepository taskRepository;
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
}
