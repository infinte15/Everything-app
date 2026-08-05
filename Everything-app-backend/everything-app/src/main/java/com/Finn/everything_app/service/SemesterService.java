package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.Course;
import com.Finn.everything_app.model.Semester;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.CourseRepository;
import com.Finn.everything_app.repository.SemesterRepository;
import com.Finn.everything_app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemesterService {

    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    /**
     * Semester des Nutzers, aufsteigend sortiert. Legt beim ersten Aufruf die Semester aus den
     * vorhandenen Freitext-Angaben an (siehe {@link #ensureSemestersBackfilled}).
     */
    @Transactional
    public List<Semester> getSemesters(Long userId) {
        ensureSemestersBackfilled(userId);
        return semesterRepository.findByUserIdOrderByOrderIndexAscIdAsc(userId);
    }

    @Transactional(readOnly = true)
    public Semester getSemester(Long userId, Long id) {
        return semesterRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Semester nicht gefunden"));
    }

    /**
     * Legt für jede bisher nur als Freitext vorhandene Semesterangabe ein Semester an und
     * verknüpft die Module.
     *
     * Idempotent über die Zählprüfung: sobald der Nutzer ein Semester hat — sei es
     * nachgezogen oder selbst angelegt — passiert hier nichts mehr. Bewusst hier und nicht in
     * einem ApplicationRunner: der liefe beim Start für jeden Nutzer der Datenbank.
     */
    @Transactional
    public void ensureSemestersBackfilled(Long userId) {
        if (semesterRepository.countByUserId(userId) > 0) return;

        List<Course> courses = courseRepository.findByUserId(userId);
        List<String> labels = courses.stream()
                .map(Course::getSemester)
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        if (labels.isEmpty()) return;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        Map<String, Semester> created = new LinkedHashMap<>();
        for (int i = 0; i < labels.size(); i++) {
            Semester semester = new Semester();
            semester.setLabel(labels.get(i));
            semester.setOrderIndex(i);
            semester.setIsCurrent(false);
            semester.setUser(user);
            created.put(labels.get(i), semesterRepository.save(semester));
        }

        for (Course course : courses) {
            if (course.getSemester() == null || course.getSemester().isBlank()) continue;
            Semester match = created.get(course.getSemester().trim());
            if (match != null) {
                course.setSemesterRef(match);
                courseRepository.save(course);
            }
        }

        log.info("{} Semester für User {} aus Freitext-Angaben nachgezogen", labels.size(), userId);
    }

    @Transactional
    public Semester createSemester(Long userId, Semester semester) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        semester.setId(null);
        semester.setUser(user);
        semester.setOrderIndex(semesterRepository.findMaxOrderIndex(userId) + 1);

        Semester saved = semesterRepository.save(semester);
        if (Boolean.TRUE.equals(saved.getIsCurrent())) {
            semesterRepository.clearCurrentFlagExcept(userId, saved.getId());
        }
        return saved;
    }

    @Transactional
    public Semester updateSemester(Long userId, Long id, Semester updated) {
        Semester semester = getSemester(userId, id);
        boolean labelChanged = updated.getLabel() != null
                && !updated.getLabel().equals(semester.getLabel());

        if (updated.getLabel() != null)    semester.setLabel(updated.getLabel());
        if (updated.getStartDate() != null) semester.setStartDate(updated.getStartDate());
        if (updated.getEndDate() != null)   semester.setEndDate(updated.getEndDate());
        if (updated.getIsCurrent() != null) semester.setIsCurrent(updated.getIsCurrent());

        Semester saved = semesterRepository.save(semester);

        if (Boolean.TRUE.equals(saved.getIsCurrent())) {
            semesterRepository.clearCurrentFlagExcept(userId, saved.getId());
        }
        // Der Freitext an den Modulen muss mitwandern, sonst zeigt die Fächerliste weiter die
        // alte Bezeichnung an.
        if (labelChanged) {
            syncLabelToModules(userId, saved);
        }
        return saved;
    }

    private void syncLabelToModules(Long userId, Semester semester) {
        for (Course course : courseRepository.findByUserIdAndSemesterRefId(userId, semester.getId())) {
            course.setSemester(semester.getLabel());
            courseRepository.save(course);
        }
    }

    @Transactional
    public Semester setCurrent(Long userId, Long id) {
        Semester semester = getSemester(userId, id);
        semester.setIsCurrent(true);
        Semester saved = semesterRepository.save(semester);
        semesterRepository.clearCurrentFlagExcept(userId, id);
        return saved;
    }

    /**
     * Löscht das Semester und hängt seine Module ab. Module werden NIE mitgelöscht — an ihnen
     * hängen Noten, Notizen und Karteikarten.
     */
    @Transactional
    public void deleteSemester(Long userId, Long id) {
        Semester semester = getSemester(userId, id);

        for (Course course : courseRepository.findByUserIdAndSemesterRefId(userId, id)) {
            course.setSemesterRef(null);
            courseRepository.save(course);
        }

        semesterRepository.delete(semester);
    }

    /** Die Position in der Liste ist der neue Index — wie bei RoutineService.reorderRoutines. */
    @Transactional
    public void reorderSemesters(Long userId, List<Long> semesterIds) {
        if (semesterIds == null || semesterIds.isEmpty()) return;
        for (int i = 0; i < semesterIds.size(); i++) {
            Semester semester = getSemester(userId, semesterIds.get(i));   // Besitz je Eintrag
            semester.setOrderIndex(i);
            semesterRepository.save(semester);
        }
    }

    /** Module je Semester, in einer Abfrage — damit die Liste nicht pro Semester nachlädt. */
    @Transactional(readOnly = true)
    public Map<Long, List<Course>> modulesBySemester(Long userId) {
        return courseRepository.findByUserId(userId).stream()
                .filter(c -> c.getSemesterRef() != null)
                .collect(Collectors.groupingBy(c -> c.getSemesterRef().getId()));
    }
}
