package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @Transactional
    public Course createCourse(Long userId, Course course) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        course.setUser(user);
        course.setTotalNotes(0);
        course.setTotalFlashcards(0);
        course.setTotalAssignments(0);

        return courseRepository.save(course);
    }

    public List<Course> getUserCourses(Long userId) {
        return courseRepository.findByUserId(userId);
    }

    public Course getCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kurs nicht gefunden"));
    }

    @Transactional
    public Course updateCourse(Long id, Course updatedCourse) {
        Course course = getCourseById(id);

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

        return courseRepository.save(course);
    }

    @Transactional
    public void deleteCourse(Long id) {
        Course course = getCourseById(id);
        courseRepository.delete(course);
    }

    @Transactional
    public void incrementNoteCount(Long courseId) {
        Course course = getCourseById(courseId);
        course.setTotalNotes(course.getTotalNotes() + 1);
        courseRepository.save(course);
    }

    @Transactional
    public void decrementNoteCount(Long courseId) {
        Course course = getCourseById(courseId);
        course.setTotalNotes(Math.max(0, course.getTotalNotes() - 1));
        courseRepository.save(course);
    }

    @Transactional
    public void incrementFlashcardCount(Long courseId, int count) {
        Course course = getCourseById(courseId);
        course.setTotalFlashcards(course.getTotalFlashcards() + count);
        courseRepository.save(course);
    }
}