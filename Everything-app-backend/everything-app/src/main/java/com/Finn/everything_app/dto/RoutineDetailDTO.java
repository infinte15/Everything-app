package com.Finn.everything_app.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/** Routine samt vollstaendiger Uebungsliste. */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoutineDetailDTO extends RoutineSummaryDTO {
    private List<RoutineExerciseDTO> exercises = new ArrayList<>();
}
