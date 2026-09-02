package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.mapper.CalendarEventMapper;
import com.Finn.everything_app.model.CalendarEvent;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.CalendarEventService;
import com.Finn.everything_app.service.ScheduleRunNotifier;
import com.Finn.everything_app.service.SmartSchedulerService;
import com.Finn.everything_app.service.ScheduleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarEventService calendarEventService;
    private final SmartSchedulerService smartSchedulerService;
    private final CalendarEventMapper calendarEventMapper;
    private final ScheduleRunNotifier scheduleRunNotifier;

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

    // PUT /api/calendar/events/{id} --> Event aktualisieren (z.B. Drag-and-Drop Reschedule)
    @PutMapping("/events/{id}")
    public ResponseEntity<CalendarEventDTO> updateEvent(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody CalendarEventDTO eventDTO) {

        CalendarEvent updatedEvent = calendarEventMapper.toEntity(eventDTO);
        CalendarEvent saved = calendarEventService.updateEvent(id, userId, updatedEvent);

        return ResponseEntity.ok(calendarEventMapper.toDTO(saved));
    }

    // PUT /api/calendar/events/{id}/pin --> Event anpinnen bzw. wieder freigeben.
    // Eigener Endpunkt, weil updateEvent einen verschobenen TASK bewusst anpinnt — ein
    // isFixed=false im normalen Payload würde dort sofort wieder überschrieben.
    @PutMapping("/events/{id}/pin")
    public ResponseEntity<CalendarEventDTO> setPinned(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody PinRequest request) {

        CalendarEvent saved = calendarEventService.setPinned(id, userId, request.getPinned());
        return ResponseEntity.ok(calendarEventMapper.toDTO(saved));
    }

    // Überspringen einer Ausführung. Eigener Endpunkt aus demselben Grund wie beim Abhaken:
    // der Mapper trägt skippedAt nur nach außen, damit ein gewöhnliches PUT es nicht setzen kann.
    @PutMapping("/events/{id}/skip")
    public ResponseEntity<CalendarEventDTO> setSkipped(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody SkipRequest request) {

        CalendarEvent saved = calendarEventService.setSkipped(id, userId, request.getSkipped());
        return ResponseEntity.ok(calendarEventMapper.toDTO(saved));
    }

    // Eigener Endpunkt statt eines Feldes im normalen Payload: das Abhaken schreibt Minuten
    // gut (ans Lernziel oder an den Task) und darf nicht versehentlich durch ein gewöhnliches
    // PUT ausgelöst werden — der Mapper überträgt completedAt deshalb nur nach außen.
    @PutMapping("/events/{id}/complete")
    public ResponseEntity<CalendarEventDTO> setCompleted(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody CompletionRequest request) {

        CalendarEvent saved = calendarEventService.setCompleted(id, userId, request.getCompleted());
        return ResponseEntity.ok(calendarEventMapper.toDTO(saved));
    }

    // POST /api/calendar/generate-schedule --> Schedule
    @PostMapping("/generate-schedule")
    public ResponseEntity<ScheduleResultDTO> generateSchedule(
            @CurrentUser Long userId,
            @Valid @RequestBody ScheduleRequest request) {

        boolean eigenerZeitraum = request.getEndDate() != null;
        LocalDate endDate = eigenerZeitraum
                ? request.getEndDate()
                : smartSchedulerService.defaultHorizonEnd(request.getStartDate());

        // Der Stundenplan reicht weiter als das Planungsfenster — aber nur, wenn der Aufrufer
        // keinen eigenen Zeitraum verlangt hat. Wer "plane mir diese Woche" schickt, soll keine
        // Vorlesungstermine bis in den übernächsten Monat zurückbekommen.
        LocalDate classEndDate = eigenerZeitraum
                ? endDate
                : smartSchedulerService.classHorizonEnd(request.getStartDate());

        ScheduleResult result = smartSchedulerService.generateOptimalSchedule(
                userId,
                request.getStartDate(),
                endDate,
                classEndDate
        );

        ScheduleResultDTO resultDTO = new ScheduleResultDTO();
        resultDTO.setTotalTasksScheduled(result.getTotalTasksScheduled());
        resultDTO.setTotalHoursScheduled(result.getTotalHoursScheduled());
        resultDTO.setUnscheduledTasksCount(result.getUnscheduledTasks().size());
        resultDTO.setMessage(result.getMessage());
        resultDTO.setSolverStatus(result.getSolverStatus());
        resultDTO.setAtRisk(result.getAtRisk().stream()
                .map(a -> new AtRiskItemDTO(a.getTaskId(), a.getHabitId(), a.getTitle(),
                        a.getMinutes(), a.getReason() != null ? a.getReason().name() : null,
                        a.getPlannedStart()))
                .collect(Collectors.toList()));

        return ResponseEntity.ok(resultDTO);
    }

    /**
     * GET /api/calendar/schedule-status --> Ergebnis des letzten Laufs.
     *
     * Billiger Endpunkt zum Nachfassen: nach einer Änderung plant der Scheduler entprellt im
     * Hintergrund neu, und das Frontend muss wissen, wann es so weit ist. Den ganzen Monat dafür
     * neu zu laden wäre Verschwendung — hier ändert sich {@code lastRunAt}, und erst dann lohnt
     * der teure Abruf.
     *
     * Zugleich der einzige Weg, an die At-Risk-Liste eines HINTERGRUND-Laufs zu kommen: die
     * Antwort von /generate-schedule sieht nur, wer selbst geplant hat.
     */
    @GetMapping("/schedule-status")
    public ResponseEntity<ScheduleStatusDTO> getScheduleStatus(@CurrentUser Long userId) {
        // Die Abbildung liegt im Notifier, damit beide Endpunkte dieselbe Antwort bauen.
        return ResponseEntity.ok(scheduleRunNotifier.statusVon(userId));
    }

    /**
     * GET /api/calendar/schedule-status/await --> dasselbe, aber es wartet.
     *
     * Die Anfrage wird geparkt, bis für den Nutzer ein Lauf fertig ist, der neuer ist als
     * {@code since} — und erst dann beantwortet. Vorher fragte das Frontend in einer Retry-Leiter
     * nach und traf den Fertig-Zeitpunkt nur auf einige hundert Millisekunden genau; diese
     * Rundung war der größte verbliebene Posten der spürbaren Wartezeit.
     *
     * <p>{@code since} weglassen heißt "ich habe noch nichts gesehen" (App-Start): dann wird auf
     * das nächste Ereignis gewartet, statt sofort den letzten bekannten Lauf zu melden.
     *
     * <p>Antwortet nach {@code scheduler.await-timeout-ms} mit <b>204</b>, wenn nichts passiert
     * ist. Der Client-Timeout muss größer sein als dieser Wert, damit immer der Server zuerst
     * antwortet und die App ein sauberes 204 sieht statt eines Socket-Fehlers.
     */
    @GetMapping("/schedule-status/await")
    public DeferredResult<ResponseEntity<ScheduleStatusDTO>> awaitScheduleStatus(
            @CurrentUser Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return scheduleRunNotifier.awaitRun(userId, since);
    }

    // DELETE /api/calendar/events/{id}  --> Lösche Event
    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> deleteEvent(@CurrentUser Long userId, @PathVariable Long id) {
        calendarEventService.deleteEvent(id, userId);
        return ResponseEntity.noContent().build();
    }
}