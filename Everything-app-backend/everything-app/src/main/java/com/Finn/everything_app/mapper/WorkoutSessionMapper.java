package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.WorkoutSessionDTO;
import com.Finn.everything_app.model.WorkoutSession;
import org.springframework.stereotype.Component;

@Component
public class WorkoutSessionMapper {

    public WorkoutSessionDTO toDTO(WorkoutSession session) {
        if (session == null) return null;

        WorkoutSessionDTO dto = new WorkoutSessionDTO();
        dto.setId(session.getId());
        dto.setName(session.getName());
        dto.setDescription(session.getDescription());
        dto.setWorkoutPlanId(session.getWorkoutPlan() != null ? session.getWorkoutPlan().getId() : null);
        dto.setWorkoutPlanName(session.getWorkoutPlan() != null ? session.getWorkoutPlan().getName() : null);
        dto.setStartTime(session.getStartTime());
        dto.setEndTime(session.getEndTime());
        dto.setDurationMinutes(session.getDurationMinutes());
        dto.setWorkoutType(session.getWorkoutType());
        dto.setIntensity(session.getIntensity());
        dto.setCaloriesBurned(session.getCaloriesBurned());
        dto.setNotes(session.getNotes());
        dto.setLocation(session.getLocation());
        dto.setIsCompleted(session.getIsCompleted());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());

        return dto;
    }

    public WorkoutSession toEntity(WorkoutSessionDTO dto) {
        if (dto == null) return null;

        WorkoutSession session = new WorkoutSession();
        session.setId(dto.getId());
        session.setName(dto.getName());
        session.setDescription(dto.getDescription());
        session.setStartTime(dto.getStartTime());
        session.setEndTime(dto.getEndTime());
        session.setDurationMinutes(dto.getDurationMinutes());
        session.setWorkoutType(dto.getWorkoutType());
        session.setIntensity(dto.getIntensity());
        session.setCaloriesBurned(dto.getCaloriesBurned());
        session.setNotes(dto.getNotes());
        session.setLocation(dto.getLocation());
        session.setIsCompleted(dto.getIsCompleted());

        return session;
    }
}