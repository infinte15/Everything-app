package com.Finn.everything_app.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Ein Uebungsblock innerhalb einer protokollierten Trainingseinheit. */
@Data
public class SessionExerciseDTO {
    private Long exerciseId;
    private String name;
    private String imageUrl;
    private String imageUrlEnd;
    private String equipment;
    private List<String> primaryMuscles = new ArrayList<>();
    private List<String> secondaryMuscles = new ArrayList<>();

    private Integer orderIndex;
    private Integer restSeconds;

    private List<ExerciseSetDTO> sets = new ArrayList<>();

    private double totalVolumeKg;
    private Double bestSetWeight;
}
