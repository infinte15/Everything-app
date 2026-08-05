package com.Finn.everything_app.service;

import com.Finn.everything_app.event.ScheduleChangedEvent;
import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.Course;
import com.Finn.everything_app.model.CourseSchedule;
import com.Finn.everything_app.repository.CourseRepository;
import com.Finn.everything_app.repository.CourseScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Der Stundenplan ist der einzige Ort im Study Space, an dem Daten entstehen, die in die
 * Kalenderplanung eingehen. Deshalb steht hier neben der Eigentumsprüfung vor allem eines:
 * jede Änderung muss ein ScheduleChangedEvent publizieren, sonst bleibt der Kalender veraltet.
 */
@ExtendWith(MockitoExtension.class)
class CourseScheduleServiceTest {

    @Mock CourseScheduleRepository scheduleRepository;
    @Mock CourseRepository courseRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks CourseScheduleService service;

    private Course course(long id) {
        Course c = new Course();
        c.setId(id);
        c.setName("Analysis I");
        return c;
    }

    private CourseSchedule schedule(long id, LocalTime start, LocalTime end) {
        CourseSchedule s = new CourseSchedule();
        s.setId(id);
        s.setCourse(course(5L));
        s.setDayOfWeek(DayOfWeek.MONDAY);
        s.setStartTime(start);
        s.setEndTime(end);
        return s;
    }

    /**
     * Der Captor ist auf ScheduleChangedEvent typisiert, nicht auf Object: ApplicationEventPublisher
     * hat beide Ueberladungen publishEvent(ApplicationEvent) und publishEvent(Object), und ein
     * Object-Captor prueft die falsche davon.
     */
    private void assertScheduleRegenerationRequested() {
        ArgumentCaptor<ScheduleChangedEvent> captor =
                ArgumentCaptor.forClass(ScheduleChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
    }

    @Test
    void creatingAScheduleTriggersAScheduleRegeneration() {
        CourseSchedule saved = schedule(9L, LocalTime.of(8, 0), LocalTime.of(10, 0));
        when(courseRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(course(5L)));
        when(scheduleRepository.save(any(CourseSchedule.class))).thenReturn(saved);
        // Nach dem Speichern liest der Service noch in der Transaktion neu, damit der Mapper
        // course.semesterRef bekommt und nicht nur einen Proxy.
        when(scheduleRepository.findByIdAndCourseUserId(9L, 1L)).thenReturn(Optional.of(saved));

        CourseSchedule created = service.createSchedule(
                1L, 5L, schedule(0L, LocalTime.of(8, 0), LocalTime.of(10, 0)));

        assertEquals(5L, created.getCourse().getId());
        assertScheduleRegenerationRequested();
    }

    @Test
    void updatingAScheduleTriggersAScheduleRegeneration() {
        CourseSchedule existing = schedule(9L, LocalTime.of(8, 0), LocalTime.of(10, 0));
        when(scheduleRepository.findByIdAndCourseUserId(9L, 1L)).thenReturn(Optional.of(existing));
        when(scheduleRepository.save(any(CourseSchedule.class))).thenAnswer(i -> i.getArgument(0));

        CourseSchedule updated = schedule(9L, LocalTime.of(9, 0), null);
        updated.setDayOfWeek(null);
        service.updateSchedule(1L, 9L, updated);

        assertEquals(LocalTime.of(9, 0), existing.getStartTime());
        assertEquals(DayOfWeek.MONDAY, existing.getDayOfWeek(), "null im DTO heisst unveraendert");
        assertScheduleRegenerationRequested();
    }

    @Test
    void deletingAScheduleTriggersAScheduleRegeneration() {
        CourseSchedule existing = schedule(9L, LocalTime.of(8, 0), LocalTime.of(10, 0));
        when(scheduleRepository.findByIdAndCourseUserId(9L, 1L)).thenReturn(Optional.of(existing));

        service.deleteSchedule(1L, 9L);

        verify(scheduleRepository).delete(existing);
        assertScheduleRegenerationRequested();
    }

    // Der Besitz haengt am Kurs - CourseSchedule hat bewusst keine eigene user-Spalte.
    @Test
    void aForeignScheduleIsNotFound() {
        when(scheduleRepository.findByIdAndCourseUserId(9L, 2L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getSchedule(2L, 9L));
    }

    @Test
    void aScheduleCannotBeAddedToAForeignCourse() {
        when(courseRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createSchedule(1L, 99L,
                        schedule(0L, LocalTime.of(8, 0), LocalTime.of(10, 0))));
        verify(scheduleRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(ScheduleChangedEvent.class));
    }

    // Eine Blockzeit, die nicht nach ihrem Anfang endet, verschwaende im Scheduler still.
    @Test
    void anEndBeforeTheStartIsRejected() {
        when(courseRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(course(5L)));

        assertThrows(BadRequestException.class,
                () -> service.createSchedule(1L, 5L,
                        schedule(0L, LocalTime.of(12, 0), LocalTime.of(10, 0))));
        verify(eventPublisher, never()).publishEvent(any(ScheduleChangedEvent.class));
    }

    @Test
    void aZeroLengthSlotIsRejected() {
        when(courseRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(course(5L)));

        assertThrows(BadRequestException.class,
                () -> service.createSchedule(1L, 5L,
                        schedule(0L, LocalTime.of(10, 0), LocalTime.of(10, 0))));
    }

    @Test
    void listingTheSchedulesOfAForeignCourseIsNotFound() {
        when(courseRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getSchedulesOfCourse(1L, 99L));
    }
}
