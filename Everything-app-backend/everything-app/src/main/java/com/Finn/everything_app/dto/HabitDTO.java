package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.HabitFrequency;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;

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

    private LocalDate startDate;
    private LocalDate endDate;

    private Integer currentStreak;
    private Integer longestStreak;
}