package com.Finn.everything_app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Anlegen und Aendern einer Routine.
 *
 * <p>Bei PUT ersetzt {@link #exercises} die bestehende Liste vollstaendig; die Reihenfolge
 * ergibt sich aus der Listenposition, ein mitgeschicktes {@code orderIndex} wird ignoriert.
 */
@Data
public class RoutineUpsertRequest {

    @NotBlank(message = "Name erforderlich")
    @Size(max = 200, message = "Name darf maximal 200 Zeichen lang sein")
    private String name;

    private String description;
    private String imageUrl;
    private String colorHex;
    private String dayLabel;
    private Integer estimatedDurationMinutes;
    private Long workoutPlanId;
    private Boolean isArchived;

    @Valid
    private List<RoutineExerciseDTO> exercises = new ArrayList<>();
}
