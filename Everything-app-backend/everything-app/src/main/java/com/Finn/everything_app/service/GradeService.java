package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GradeService {

    private final GradeRepository gradeRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    @Transactional
    public Grade createGrade(Long userId, Grade grade, Long courseId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Kurs nicht gefunden"));

        grade.setUser(user);
        grade.setCourse(course);

        if (grade.getWeight() == null) {
            grade.setWeight(100); // Default: volle Gewichtung
        }

        return gradeRepository.save(grade);
    }

    public List<Grade> getUserGrades(Long userId) {
        return gradeRepository.findByUserId(userId);
    }

    public Grade getGradeById(Long id) {
        return gradeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Note nicht gefunden"));
    }

    public List<Grade> getGradesByCourse(Long userId, Long courseId) {
        return gradeRepository.findByUserIdAndCourseId(userId, courseId);
    }

    @Transactional
    public Grade updateGrade(Long id, Grade updatedGrade) {
        Grade grade = getGradeById(id);

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
        if (updatedGrade.getNotes() != null) {
            grade.setNotes(updatedGrade.getNotes());
        }

        return gradeRepository.save(grade);
    }

    @Transactional
    public void deleteGrade(Long id) {
        Grade grade = getGradeById(id);
        gradeRepository.delete(grade);
    }


    public Double calculateAverageGrade(Long userId) {
        List<Grade> grades = getUserGrades(userId);

        if (grades.isEmpty()) {
            return null;
        }

        double weightedSum = 0.0;
        int totalWeight = 0;

        for (Grade grade : grades) {
            weightedSum += grade.getGrade() * grade.getWeight();
            totalWeight += grade.getWeight();
        }

        if (totalWeight == 0) {
            return null;
        }

        double average = weightedSum / totalWeight;

        // Runde auf 2 Nachkommastellen
        return Math.round(average * 100.0) / 100.0;
    }


    public Double calculateCourseAverageGrade(Long userId, Long courseId) {
        List<Grade> grades = getGradesByCourse(userId, courseId);

        if (grades.isEmpty()) {
            return null;
        }

        double weightedSum = 0.0;
        int totalWeight = 0;

        for (Grade grade : grades) {
            weightedSum += grade.getGrade() * grade.getWeight();
            totalWeight += grade.getWeight();
        }

        if (totalWeight == 0) {
            return null;
        }

        double average = weightedSum / totalWeight;
        return Math.round(average * 100.0) / 100.0;
    }
}