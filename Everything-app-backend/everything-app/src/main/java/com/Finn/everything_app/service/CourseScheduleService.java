package com.Finn.everything_app.service;

import com.Finn.everything_app.event.ScheduleChangedEvent;
import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.Course;
import com.Finn.everything_app.model.CourseSchedule;
import com.Finn.everything_app.repository.CourseRepository;
import com.Finn.everything_app.repository.CourseScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Stundenplan-CRUD.
 *
 * Bis hierher gab es zu {@code CourseSchedule} weder Service noch Endpunkt — Zeilen waren nur
 * von Hand in die Datenbank einfügbar, obwohl der SmartScheduler sie längst als Blockzeiten las.
 *
 * <p><b>Jede Änderung publiziert {@link ScheduleChangedEvent}.</b> Der Scheduler expandiert
 * Stundenpläne in wiederkehrende Blockzeiten; ohne das Ereignis bliebe der Kalender nach jeder
 * Stundenplanänderung auf dem alten Stand, bis ihn zufällig etwas anderes neu berechnet. Kein
 * anderer Study-Service publiziert es — hier ist es Pflicht, weil hier als einzigem im Study
 * Space Daten entstehen, die in die Kalenderplanung eingehen.
 */
@Service
@RequiredArgsConstructor
public class CourseScheduleService {

    private final CourseScheduleRepository scheduleRepository;
    private final CourseRepository courseRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<CourseSchedule> getUserSchedules(Long userId) {
        return scheduleRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<CourseSchedule> getSchedulesOfCourse(Long userId, Long courseId) {
        requireCourse(userId, courseId);
        return scheduleRepository.findByCourseIdAndCourseUserIdOrderByDayOfWeekAscStartTimeAsc(
                courseId, userId);
    }

    /** Einziges Eingangstor für Einzelzugriffe — der Besitz hängt am Kurs. */
    @Transactional(readOnly = true)
    public CourseSchedule getSchedule(Long userId, Long id) {
        return scheduleRepository.findByIdAndCourseUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Stundenplan-Eintrag nicht gefunden"));
    }

    @Transactional
    public CourseSchedule createSchedule(Long userId, Long courseId, CourseSchedule schedule) {
        Course course = requireCourse(userId, courseId);
        requireValidTimes(schedule);

        schedule.setCourse(course);
        CourseSchedule saved = scheduleRepository.save(schedule);

        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        // Noch in der Transaktion neu lesen: der Mapper braucht course.semesterRef, und am frisch
        // gespeicherten Objekt hängt dafür nur ein nicht initialisierter Proxy.
        return getSchedule(userId, saved.getId());
    }

    @Transactional
    public CourseSchedule updateSchedule(Long userId, Long id, CourseSchedule updated) {
        CourseSchedule schedule = getSchedule(userId, id);

        if (updated.getDayOfWeek() != null)  schedule.setDayOfWeek(updated.getDayOfWeek());
        if (updated.getStartTime() != null)  schedule.setStartTime(updated.getStartTime());
        if (updated.getEndTime() != null)    schedule.setEndTime(updated.getEndTime());
        if (updated.getLocation() != null)   schedule.setLocation(updated.getLocation());
        requireValidTimes(schedule);

        CourseSchedule saved = scheduleRepository.save(schedule);

        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return saved;
    }

    @Transactional
    public void deleteSchedule(Long userId, Long id) {
        CourseSchedule schedule = getSchedule(userId, id);
        scheduleRepository.delete(schedule);

        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
    }

    private Course requireCourse(Long userId, Long courseId) {
        return courseRepository.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kurs nicht gefunden"));
    }

    /**
     * Ein Termin, der nicht nach seinem Anfang endet, würde im Scheduler eine leere oder negative
     * Blockzeit erzeugen und dort still verschwinden — besser hier mit 400 abweisen.
     */
    private void requireValidTimes(CourseSchedule schedule) {
        if (schedule.getStartTime() != null && schedule.getEndTime() != null
                && !schedule.getEndTime().isAfter(schedule.getStartTime())) {
            throw new BadRequestException("Die Endzeit muss nach der Startzeit liegen");
        }
    }
}
