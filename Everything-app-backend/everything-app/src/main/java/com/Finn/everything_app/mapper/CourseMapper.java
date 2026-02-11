package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.CourseDTO;
import com.Finn.everything_app.model.Course;
import org.springframework.stereotype.Component;

@Component
public class CourseMapper {

    public CourseDTO toDTO(Course course) {
        if (course == null) return null;

        CourseDTO dto = new CourseDTO();
        dto.setId(course.getId());
        dto.setName(course.getName());
        dto.setCode(course.getCode());
        dto.setInstructor(course.getInstructor());
        dto.setSemester(course.getSemester());
        dto.setDescription(course.getDescription());
        dto.setStartDate(course.getStartDate());
        dto.setEndDate(course.getEndDate());
        dto.setColor(course.getColor());
        dto.setTotalNotes(course.getTotalNotes());
        dto.setTotalFlashcards(course.getTotalFlashcards());
        dto.setTotalAssignments(course.getTotalAssignments());
        dto.setCreatedAt(course.getCreatedAt());
        dto.setUpdatedAt(course.getUpdatedAt());

        return dto;
    }

    public Course toEntity(CourseDTO dto) {
        if (dto == null) return null;

        Course course = new Course();
        course.setId(dto.getId());
        course.setName(dto.getName());
        course.setCode(dto.getCode());
        course.setInstructor(dto.getInstructor());
        course.setSemester(dto.getSemester());
        course.setDescription(dto.getDescription());
        course.setStartDate(dto.getStartDate());
        course.setEndDate(dto.getEndDate());
        course.setColor(dto.getColor());

        return course;
    }
}