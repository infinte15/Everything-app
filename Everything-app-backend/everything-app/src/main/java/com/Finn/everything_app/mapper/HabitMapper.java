package com.Finn.everything_app.mapper;


import com.Finn.everything_app.dto.HabitDTO;
import com.Finn.everything_app.model.Habit;
import org.springframework.stereotype.Component;

@Component
public class HabitMapper {

    public HabitDTO toDTO(Habit habit) {
        if (habit == null) return null;

        HabitDTO dto = new HabitDTO();
        dto.setId(habit.getId());
        dto.setName(habit.getName());
        dto.setDescription(habit.getDescription());
        dto.setFrequency(habit.getFrequency());

        dto.setMonday(habit.getMonday());
        dto.setTuesday(habit.getTuesday());
        dto.setWednesday(habit.getWednesday());
        dto.setThursday(habit.getThursday());
        dto.setFriday(habit.getFriday());
        dto.setSaturday(habit.getSaturday());
        dto.setSunday(habit.getSunday());

        dto.setPreferredTime(habit.getPreferredTime());
        dto.setDurationMinutes(habit.getDurationMinutes());
        dto.setStartDate(habit.getStartDate());
        dto.setEndDate(habit.getEndDate());
        dto.setCurrentStreak(habit.getCurrentStreak());
        dto.setLongestStreak(habit.getLongestStreak());

        return dto;
    }

    public Habit toEntity(HabitDTO dto) {
        if (dto == null) return null;

        Habit habit = new Habit();
        habit.setId(dto.getId());
        habit.setName(dto.getName());
        habit.setDescription(dto.getDescription());
        habit.setFrequency(dto.getFrequency());

        habit.setMonday(dto.getMonday());
        habit.setTuesday(dto.getTuesday());
        habit.setWednesday(dto.getWednesday());
        habit.setThursday(dto.getThursday());
        habit.setFriday(dto.getFriday());
        habit.setSaturday(dto.getSaturday());
        habit.setSunday(dto.getSunday());

        habit.setPreferredTime(dto.getPreferredTime());
        habit.setDurationMinutes(dto.getDurationMinutes());
        habit.setStartDate(dto.getStartDate());
        habit.setEndDate(dto.getEndDate());

        return habit;
    }
}