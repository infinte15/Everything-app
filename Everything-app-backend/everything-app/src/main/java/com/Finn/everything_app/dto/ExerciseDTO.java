package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExerciseDTO {
    private Long id;

    @NotBlank(message = "Übungsname erforderlich")
    @Size(max = 200, message = "Name darf maximal 200 Zeichen lang sein")
    private String name;

    private String description;
    private String instructions;

    /**
     * Primaere Muskelgruppe als Slug. Kein {@code @NotBlank} mehr: wenn der Client
     * {@link #primaryMuscles} schickt, leitet der Server den Wert selbst ab.
     */
    private String muscleGroup;

    /** Slugs aus {@code MuscleGroup}, z.B. ["chest", "triceps"]. */
    private List<String> primaryMuscles = new ArrayList<>();
    private List<String> secondaryMuscles = new ArrayList<>();

    private String equipment;
    private String difficulty;
    private String category;
    private String force;
    private String mechanic;

    private String videoUrl;
    private String imageUrl;
    private String imageUrlEnd;

    private String externalId;
    private Integer defaultRestSeconds;
    private Boolean isSystem;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
