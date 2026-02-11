package com.Finn.everything_app.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkoutProgressDTO {
    private Integer totalWorkouts;
    private Integer completedWorkouts;
    private Double completionRate;

    private Integer totalMinutesTrained;
    private Double averageIntensity;
    private Integer totalCaloriesBurned;

    private Map<String, Integer> workoutsByType;
    private Map<String, Integer> exercisesByMuscleGroup;

    private String mostFrequentWorkoutType;
    private String mostTrainedMuscleGroup;
}
