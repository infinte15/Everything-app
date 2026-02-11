package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Data
public class ExerciseDTO {
    private Long id;

    @NotBlank(message = "Übungsname erforderlich")
    @Size(max = 200, message = "Name darf maximal 200 Zeichen lang sein")
    private String name;

    private String description;
    private String instructions;

    @NotBlank(message = "Muskelgruppe erforderlich")
    private String muscleGroup;

    private String equipment;

    private String difficulty;

    private String videoUrl;
    private String imageUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
