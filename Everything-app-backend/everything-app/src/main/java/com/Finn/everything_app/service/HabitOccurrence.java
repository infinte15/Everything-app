package com.Finn.everything_app.service;

import com.Finn.everything_app.model.Habit;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

/** One required occurrence of a recurring Habit on a specific day, expanded for the CP-SAT solver. */
@Data
public class HabitOccurrence {
    private Habit habit;
    private LocalDate date;
    private Integer durationMinutes;
    private LocalTime preferredTime;
}
