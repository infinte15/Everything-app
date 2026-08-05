package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.CourseScheduleDTO;
import com.Finn.everything_app.model.Course;
import com.Finn.everything_app.model.CourseSchedule;
import org.springframework.stereotype.Component;

@Component
public class CourseScheduleMapper {

    public CourseScheduleDTO toDTO(CourseSchedule schedule) {
        if (schedule == null) return null;

        CourseScheduleDTO dto = new CourseScheduleDTO();
        dto.setId(schedule.getId());
        dto.setDayOfWeek(schedule.getDayOfWeek());
        dto.setStartTime(schedule.getStartTime());
        dto.setEndTime(schedule.getEndTime());
        dto.setLocation(schedule.getLocation());

        Course course = schedule.getCourse();
        if (course != null) {
            dto.setCourseId(course.getId());
            dto.setCourseName(course.getName());
            dto.setCourseColor(course.getColor());
            dto.setCourseInstructor(course.getInstructor());
            dto.setSemesterLabel(course.getSemesterRef() != null
                    ? course.getSemesterRef().getLabel()
                    : null);
        }

        return dto;
    }

    public CourseSchedule toEntity(CourseScheduleDTO dto) {
        if (dto == null) return null;

        CourseSchedule schedule = new CourseSchedule();
        schedule.setDayOfWeek(dto.getDayOfWeek());
        schedule.setStartTime(dto.getStartTime());
        schedule.setEndTime(dto.getEndTime());
        schedule.setLocation(dto.getLocation());
        // Kurs und ID bewusst nicht: der Kurs kommt aus dem Pfad und wird über
        // findByIdAndUserId aufgelöst, die ID vergibt die Datenbank.

        return schedule;
    }
}
