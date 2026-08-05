package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.StudyGoalDTO;
import com.Finn.everything_app.model.Course;
import com.Finn.everything_app.model.StudyGoal;
import org.springframework.stereotype.Component;

@Component
public class StudyGoalMapper {

    public StudyGoalDTO toDTO(StudyGoal goal) {
        if (goal == null) return null;

        StudyGoalDTO dto = new StudyGoalDTO();
        dto.setId(goal.getId());
        dto.setEmoji(goal.getEmoji());
        dto.setWeeklyGoalHours(goal.getWeeklyGoalHours());
        dto.setLoggedHours(goal.getLoggedHours());
        dto.setRemainingHours(goal.getRemainingHours());

        double target = goal.getWeeklyGoalHours() != null ? goal.getWeeklyGoalHours() : 0.0;
        double done   = goal.getLoggedHours() != null ? goal.getLoggedHours() : 0.0;
        dto.setProgress(target > 0 ? Math.min(1.0, done / target) : 0.0);

        // Name und Farbe kommen immer aus dem Modul, nie vom Client — sonst driften die
        // Anzeigen im Study Space auseinander, sobald ein Modul umbenannt wird.
        Course course = goal.getCourse();
        if (course != null) {
            dto.setCourseId(course.getId());
            dto.setCourseName(course.getName());
            dto.setCourseColor(course.getColor());
        }
        if (goal.getTask() != null) dto.setTaskId(goal.getTask().getId());

        dto.setCreatedAt(goal.getCreatedAt());
        dto.setUpdatedAt(goal.getUpdatedAt());
        return dto;
    }

    /** Nur die vom Client gesetzten Felder; Modul, Nutzer und Task hängt der Service an. */
    public StudyGoal toEntity(StudyGoalDTO dto) {
        if (dto == null) return null;

        StudyGoal goal = new StudyGoal();
        goal.setEmoji(dto.getEmoji());
        goal.setWeeklyGoalHours(dto.getWeeklyGoalHours());
        if (dto.getLoggedHours() != null) goal.setLoggedHours(dto.getLoggedHours());
        return goal;
    }
}
