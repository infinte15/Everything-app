package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Hier wird bewusst NICHT gerechnet.
 *
 * Der gewichtete Schnitt lebt ausschließlich in lib/utils/study_grade_calculator.dart: der
 * Zielschnitt-Slider im Notenrechner rechnet bei jeder Bewegung neu, das verträgt keinen
 * Roundtrip. Die frühere Serverkopie (calculateAverageGrade / calculateCourseAverageGrade und
 * die Aggregat-Queries im Repository) war ohnehin toter Code — kein Client hat sie je
 * aufgerufen. Zwei Implementierungen derselben Formel wären irgendwann uneinig geworden.
 */
@Service
@RequiredArgsConstructor
public class GradeService {

    private final GradeRepository gradeRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    @Transactional
    public Grade createGrade(Long userId, Grade grade, Long courseId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        // findByIdAndUserId: sonst ließe sich eine Note an einen fremden Kurs hängen.
        Course course = courseRepository.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kurs nicht gefunden"));

        grade.setUser(user);
        grade.setCourse(course);

        if (grade.getWeight() == null) {
            grade.setWeight(100);
        }
        if (grade.getCountsTowardGrade() == null) {
            grade.setCountsTowardGrade(true);
        }

        return gradeRepository.save(grade);
    }

    @Transactional(readOnly = true)
    public List<Grade> getUserGrades(Long userId) {
        return gradeRepository.findByUserId(userId);
    }

    /** Einziges Eingangstor für Einzelzugriffe — userId in der Query. */
    @Transactional(readOnly = true)
    public Grade getGrade(Long userId, Long id) {
        return gradeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Note nicht gefunden"));
    }

    @Transactional(readOnly = true)
    public List<Grade> getGradesByCourse(Long userId, Long courseId) {
        return gradeRepository.findByUserIdAndCourseId(userId, courseId);
    }

    @Transactional
    public Grade updateGrade(Long userId, Long id, Grade updatedGrade) {
        Grade grade = getGrade(userId, id);

        if (updatedGrade.getExamName() != null) {
            grade.setExamName(updatedGrade.getExamName());
        }
        if (updatedGrade.getGrade() != null) {
            grade.setGrade(updatedGrade.getGrade());
        }
        if (updatedGrade.getWeight() != null) {
            grade.setWeight(updatedGrade.getWeight());
        }
        if (updatedGrade.getExamDate() != null) {
            grade.setExamDate(updatedGrade.getExamDate());
        }
        if (updatedGrade.getExamType() != null) {
            grade.setExamType(updatedGrade.getExamType());
        }
        if (updatedGrade.getCountsTowardGrade() != null) {
            grade.setCountsTowardGrade(updatedGrade.getCountsTowardGrade());
        }
        if (updatedGrade.getNotes() != null) {
            grade.setNotes(updatedGrade.getNotes());
        }

        return gradeRepository.save(grade);
    }

    @Transactional
    public void deleteGrade(Long userId, Long id) {
        Grade grade = getGrade(userId, id);
        gradeRepository.delete(grade);
    }
}
