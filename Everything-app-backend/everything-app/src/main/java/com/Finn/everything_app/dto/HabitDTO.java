package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.HabitFrequency;
import com.Finn.everything_app.model.HabitWindow;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class HabitDTO {
    private Long id;

    @NotBlank
    private String name;

    private String description;
    private HabitFrequency frequency;

    private Boolean monday;
    private Boolean tuesday;
    private Boolean wednesday;
    private Boolean thursday;
    private Boolean friday;
    private Boolean saturday;
    private Boolean sunday;

    private LocalTime preferredTime;
    private Integer durationMinutes;

    // --- Flexible Planung ---
    @Min(value = 1, message = "Häufigkeit muss mindestens 1 pro Woche sein")
    @Max(value = 7, message = "Häufigkeit darf höchstens 7 pro Woche sein")
    private Integer timesPerWeek;

    private HabitWindow idealWindow;
    private LocalTime idealWindowStart;
    private LocalTime idealWindowEnd;

    private LocalDate startDate;
    private LocalDate endDate;

    private Integer currentStreak;
    private Integer longestStreak;

    private String color;
    private Integer priority;
    private String category;

    private List<String> completedDates;
}