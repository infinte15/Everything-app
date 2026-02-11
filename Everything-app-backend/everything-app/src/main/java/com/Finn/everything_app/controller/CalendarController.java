package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.mapper.CalendarEventMapper;
import com.Finn.everything_app.model.CalendarEvent;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.CalendarEventService;
import com.Finn.everything_app.service.SmartSchedulerService;
import com.Finn.everything_app.service.ScheduleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CalendarController {

    private final CalendarEventService calendarEventService;
    private final SmartSchedulerService smartSchedulerService;
    private final CalendarEventMapper calendarEventMapper;

    //GET /api/calendar/events --> Events in Zeitraum
    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventDTO>> getEvents(
            @CurrentUser Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDateTime start = LocalDateTime.parse(startDate);
        LocalDateTime end = LocalDateTime.parse(endDate);

        List<CalendarEvent> events = calendarEventService.getEventsInRange(userId, start, end);
        List<CalendarEventDTO> eventDTOs = events.stream()
                .map(calendarEventMapper::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(eventDTOs);
    }

    // POST /api/calendar/events --> manuelles Event
    @PostMapping("/events")
    public ResponseEntity<CalendarEventDTO> createEvent(
            @CurrentUser Long userId,
            @Valid @RequestBody CalendarEventDTO eventDTO) {

        CalendarEvent event = calendarEventMapper.toEntity(eventDTO);
        CalendarEvent created = calendarEventService.createEvent(userId, event);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                calendarEventMapper.toDTO(created)
        );
    }

    // POST /api/calendar/generate-schedule --> Schedule
    @PostMapping("/generate-schedule")
    public ResponseEntity<ScheduleResultDTO> generateSchedule(
            @CurrentUser Long userId,
            @Valid @RequestBody ScheduleRequest request) {

        ScheduleResult result = smartSchedulerService.generateOptimalSchedule(
                userId,
                request.getStartDate(),
                request.getEndDate()
        );

        ScheduleResultDTO resultDTO = new ScheduleResultDTO();
        resultDTO.setTotalTasksScheduled(result.getTotalTasksScheduled());
        resultDTO.setTotalHoursScheduled(result.getTotalHoursScheduled());
        resultDTO.setUnscheduledTasksCount(result.getUnscheduledTasks().size());

        return ResponseEntity.ok(resultDTO);
    }

    // DELETE /api/calendar/events/{id}  --> Lösche Event
    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        calendarEventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }
}