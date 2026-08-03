package com.Finn.everything_app.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Ein Datensatz aus {@code data/exercises.json} (free-exercise-db, CC0).
 *
 * <p>Beispiel:
 * <pre>
 * {"name":"3/4 Sit-Up","force":"pull","level":"beginner","mechanic":"compound",
 *  "equipment":"body only","primaryMuscles":["abdominals"],"secondaryMuscles":[],
 *  "instructions":["..."],"category":"strength",
 *  "images":["3_4_Sit-Up/0.jpg","3_4_Sit-Up/1.jpg"],"id":"3_4_Sit-Up"}
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FreeExerciseDbEntry(
        String id,
        String name,
        String force,
        String level,
        String mechanic,
        String equipment,
        List<String> primaryMuscles,
        List<String> secondaryMuscles,
        List<String> instructions,
        String category,
        List<String> images
) {
}
