package com.Finn.everything_app.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine Uebung im laufenden Training: Zielvorgaben plus die letzte Leistung.
 *
 * <p>{@link #previous} wird beim Start in einer einzigen Abfrage fuer alle Uebungen geladen -
 * so kann der Client die graue "vorher"-Spalte anzeigen, ohne pro Uebung nachzufragen.
 */
@Data
public class PlannedExerciseDTO {
    private Long exerciseId;
    private String name;
    private String imageUrl;
    private String imageUrlEnd;
    private String equipment;
    private List<String> primaryMuscles = new ArrayList<>();
    private List<String> secondaryMuscles = new ArrayList<>();

    private Long routineExerciseId;
    private Integer orderIndex;
    private Integer restSeconds;
    private Integer targetSets;
    private Integer targetRepsMin;
    private Integer targetRepsMax;
    private Double targetWeight;
    private Integer targetDurationSeconds;
    private String notes;
    private Integer supersetGroup;

    private List<ExerciseSetDTO> previous = new ArrayList<>();

    /** Bestes je bewegtes Gewicht dieser Uebung - fuer die Bestleistungs-Markierung. */
    private Double personalRecordWeight;
}
