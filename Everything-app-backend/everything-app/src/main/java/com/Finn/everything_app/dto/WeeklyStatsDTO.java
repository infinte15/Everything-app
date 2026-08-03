package com.Finn.everything_app.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Wochenbilanz plus die Volumen-Kurve der letzten Wochen. */
@Data
public class WeeklyStatsDTO {
    private LocalDate weekStart;
    private int workoutsCompleted;
    private Integer workoutGoal;
    private int totalMinutes;
    private double totalVolumeKg;
    private int totalSets;
    private int currentStreakWeeks;
    private int longestStreakWeeks;

    private List<VolumePointDTO> volumeSeries = new ArrayList<>();
}
