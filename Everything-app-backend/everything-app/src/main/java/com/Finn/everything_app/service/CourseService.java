package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import com.Finn.everything_app.event.ScheduleChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final SemesterRepository semesterRepository;
    private final FlashcardReviewRepository reviewRepository;
    private final CourseScheduleRepository scheduleRepository;
    private final StudyNoteService studyNoteService;
    private final StudyGoalService studyGoalService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Course createCourse(Long userId, Course course, Long semesterId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        course.setUser(user);
        course.setTotalNotes(0);
        course.setTotalFlashcards(0);
        course.setTotalAssignments(0);
        if (course.getEctsCredits() == null) {
            course.setEctsCredits(0);
        }
        if (semesterId != null) {
            applySemester(userId, course, semesterId);
        }

        return courseRepository.save(course);
    }

    /**
     * Ordnet das Modul einem Semester zu, {@code semesterId == null} hebt die Zuordnung auf.
     * Der Freitext {@code semester} wird mitgeführt, damit die bestehenden Filter weiter
     * funktionieren.
     */
    @Transactional
    public Course assignSemester(Long userId, Long courseId, Long semesterId) {
        Course course = getCourse(userId, courseId);
        applySemester(userId, course, semesterId);
        return courseRepository.save(course);
    }

    private void applySemester(Long userId, Course course, Long semesterId) {
        if (semesterId == null) {
            course.setSemesterRef(null);
            course.setSemester(null);
            return;
        }
        // findByIdAndUserId: sonst ließe sich das eigene Modul in ein fremdes Semester hängen.
        Semester semester = semesterRepository.findByIdAndUserId(semesterId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Semester nicht gefunden"));
        course.setSemesterRef(semester);
        course.setSemester(semester.getLabel());
    }

    @Transactional(readOnly = true)
    public List<Course> getUserCourses(Long userId) {
        return courseRepository.findByUserId(userId);
    }

    /**
     * Einziges Eingangstor für Einzelzugriffe. Die userId steckt in der Query, nicht in einer
     * nachgelagerten Prüfung — ein fremder Kurs ist damit nicht einmal als "existiert"
     * erkennbar, deshalb 404 und nicht 403.
     */
    @Transactional(readOnly = true)
    public Course getCourse(Long userId, Long id) {
        return courseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kurs nicht gefunden"));
    }

    @Transactional
    public Course updateCourse(Long userId, Long id, Course updatedCourse) {
        Course course = getCourse(userId, id);

        if (updatedCourse.getName() != null) {
            course.setName(updatedCourse.getName());
        }
        if (updatedCourse.getCode() != null) {
            course.setCode(updatedCourse.getCode());
        }
        if (updatedCourse.getInstructor() != null) {
            course.setInstructor(updatedCourse.getInstructor());
        }
        if (updatedCourse.getSemester() != null) {
            course.setSemester(updatedCourse.getSemester());
        }
        if (updatedCourse.getDescription() != null) {
            course.setDescription(updatedCourse.getDescription());
        }
        if (updatedCourse.getStartDate() != null) {
            course.setStartDate(updatedCourse.getStartDate());
        }
        if (updatedCourse.getEndDate() != null) {
            course.setEndDate(updatedCourse.getEndDate());
        }
        if (updatedCourse.getColor() != null) {
            course.setColor(updatedCourse.getColor());
        }
        if (updatedCourse.getEctsCredits() != null) {
            course.setEctsCredits(updatedCourse.getEctsCredits());
        }

        return courseRepository.save(course);
    }

    @Transactional
    public void deleteCourse(Long userId, Long id) {
        Course course = getCourse(userId, id);
        // Course.decks kaskadiert auf die Decks und von dort auf die Karten. Das Review-Protokoll
        // hängt per Fremdschlüssel an den Karten und wird von keiner dieser Kaskaden erfasst —
        // ohne diese Zeile scheitert jedes Löschen eines Kurses mit Karteikarten.
        reviewRepository.deleteByCourseId(id);
        // Course.notes kaskadiert bewusst nicht mehr (der Seitenbaum ließe sonst Kinder mit
        // verschwundener Elternseite zurück), also hier explizit über den Baum löschen.
        studyNoteService.deleteNotesOfCourse(userId, id);
        // Der Stundenplan hängt per Fremdschlüssel am Kurs, und Course hat bewusst keine
        // schedules-Collection (der SmartScheduler liest sie über das Repository). Ohne diese
        // Zeile scheitert jedes Löschen eines Moduls, an dem eine Veranstaltung hängt.
        scheduleRepository.deleteAll(scheduleRepository
                .findByCourseIdAndCourseUserIdOrderByDayOfWeekAscStartTimeAsc(id, userId));
        // Dasselbe für die Lernziele: study_goals.course_id ist ein Fremdschlüssel, und der
        // Brücken-Task des Ziels muss mit weg, sonst plant der Solver ihn ewig weiter.
        studyGoalService.deleteGoalsOfCourse(id);
        courseRepository.delete(course);

        // Anders als CourseScheduleService löscht diese Stelle die Veranstaltungen direkt am
        // Repository vorbei am Service — ohne diese Meldung blieben die daraus abgeleiteten
        // Vorlesungstermine des gelöschten Moduls für immer im Kalender stehen.
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
    }

    // --- Interne Zähler. Kein userId-Parameter, weil der Aufrufer den Besitz bereits über die
    // Notiz bzw. das Deck geprüft hat; eine zweite Prüfung wäre nur Ritual.
    //
    // Der Notizzähler wohnt in StudyNoteService, nicht hier: sonst hinge StudyNoteService an
    // CourseService und CourseService (für das Löschen der Notizen eines Kurses) an
    // StudyNoteService — ein Zyklus, den Spring beim Start ablehnt. ---

    @Transactional
    public void incrementFlashcardCount(Long courseId, int count) {
        courseRepository.findById(courseId).ifPresent(course -> {
            course.setTotalFlashcards(Math.max(0, course.getTotalFlashcards() + count));
            courseRepository.save(course);
        });
    }
}
