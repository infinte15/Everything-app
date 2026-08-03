package com.Finn.everything_app.dto;

import java.util.List;

/** Belegte Filterwerte des Katalogs, damit der Client seine Chips nicht hart kodieren muss. */
public record ExerciseFiltersDTO(
        List<String> equipment,
        List<String> categories,
        List<String> difficulties
) {
}
