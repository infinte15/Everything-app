package com.Finn.everything_app.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/** Trainingseinheit samt allen protokollierten Uebungen und Saetzen. */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkoutSessionDetailDTO extends WorkoutSessionDTO {
    private List<SessionExerciseDTO> exercises = new ArrayList<>();
}
