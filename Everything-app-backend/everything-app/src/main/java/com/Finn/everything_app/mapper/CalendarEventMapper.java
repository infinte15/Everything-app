package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.CalendarEventDTO;
import com.Finn.everything_app.model.CalendarEvent;
import org.springframework.stereotype.Component;

@Component
public class CalendarEventMapper {

    public CalendarEventDTO toDTO(CalendarEvent event) {
        if (event == null) return null;

        CalendarEventDTO dto = new CalendarEventDTO();
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setDescription(event.getDescription());
        dto.setStartTime(event.getStartTime());
        dto.setEndTime(event.getEndTime());
        dto.setLocation(event.getLocation());
        dto.setEventType(event.getEventType());
        dto.setIsFixed(event.getIsFixed());
        dto.setColor(event.getColor());
        dto.setNotes(event.getNotes());


        if (event.getRelatedTask() != null) {
            dto.setRelatedTaskId(event.getRelatedTask().getId());
        }
        if (event.getRelatedHabit() != null) {
            dto.setRelatedHabitId(event.getRelatedHabit().getId());
        }
        if (event.getRelatedWorkout() != null) {
            dto.setRelatedWorkoutId(event.getRelatedWorkout().getId());
        }

        return dto;
    }

    public CalendarEvent toEntity(CalendarEventDTO dto) {
        if (dto == null) return null;

        CalendarEvent event = new CalendarEvent();
        event.setId(dto.getId());
        event.setTitle(dto.getTitle());
        event.setDescription(dto.getDescription());
        event.setStartTime(dto.getStartTime());
        event.setEndTime(dto.getEndTime());
        event.setLocation(dto.getLocation());
        event.setEventType(dto.getEventType());
        event.setIsFixed(dto.getIsFixed());
        event.setColor(dto.getColor());
        event.setNotes(dto.getNotes());

        return event;
    }
}