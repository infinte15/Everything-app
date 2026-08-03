package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.WorkoutSessionDTO;
import com.Finn.everything_app.model.WorkoutSession;
import com.Finn.everything_app.repository.projection.SessionAggregateRow;
import org.springframework.stereotype.Component;

@Component
public class WorkoutSessionMapper {

    public WorkoutSessionDTO toDTO(WorkoutSession session) {
        return toDTO(session, null);
    }

    /**
     * @param aggregate vorab gesammelte Satz-/Volumen-Summe; {@code null}, wenn nicht geladen.
     */
    public WorkoutSessionDTO toDTO(WorkoutSession session, SessionAggregateRow aggregate) {
        if (session == null) return null;

        WorkoutSessionDTO dto = new WorkoutSessionDTO();
        fill(dto, session, aggregate);
        return dto;
    }

    /** Fuellt die gemeinsamen Felder - auch fuer {@code WorkoutSessionDetailDTO} nutzbar. */
    public void fill(WorkoutSessionDTO dto, WorkoutSession session, SessionAggregateRow aggregate) {
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
        dto.setRoutineId(session.getRoutine() != null ? session.getRoutine().getId() : null);
        dto.setRoutineName(session.getRoutine() != null ? session.getRoutine().getName() : null);
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());

        if (aggregate != null) {
            dto.setTotalSets(aggregate.getSetCount() != null ? aggregate.getSetCount().intValue() : 0);
            dto.setTotalVolumeKg(aggregate.getVolume() != null ? aggregate.getVolume() : 0d);
        } else {
            dto.setTotalSets(0);
            dto.setTotalVolumeKg(0d);
        }
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