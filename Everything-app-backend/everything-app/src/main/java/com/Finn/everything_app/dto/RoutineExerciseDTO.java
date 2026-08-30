package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.ProgressionPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RoutineExerciseDTO {
    private Long id;

    @NotNull(message = "Übung erforderlich")
    private Long exerciseId;

    private String exerciseName;
    private String imageUrl;
    private String animationUrl;
    private String equipment;
    private List<String> primaryMuscles = new ArrayList<>();
    private List<String> secondaryMuscles = new ArrayList<>();

    private Integer orderIndex;
    private Integer targetSets;
    private Integer targetRepsMin;
    private Integer targetRepsMax;
    private Double targetWeight;
    private Integer targetDurationSeconds;
    private Integer restSeconds;
    private String notes;
    private Integer supersetGroup;

    /** null wird als {@code OFF} gelesen - siehe {@code ProgressionPolicy}. */
    private ProgressionPolicy progressionPolicy;

    @Min(value = 0, message = "Sprung kann nicht negativ sein")
    private Double incrementKg;

    private Boolean isBodyweight;
}
