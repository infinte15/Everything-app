package com.Finn.everything_app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /** ISO-Wochentag (1 = Montag ... 7 = Sonntag). null bedeutet "kein Wunschtag". */
    @Min(value = 1, message = "Wochentag muss zwischen 1 (Montag) und 7 (Sonntag) liegen")
    @Max(value = 7, message = "Wochentag muss zwischen 1 (Montag) und 7 (Sonntag) liegen")
    private Integer preferredWeekday;
    private Integer estimatedDurationMinutes;
    private Long workoutPlanId;
    private Boolean isArchived;

    @Valid
    private List<RoutineExerciseDTO> exercises = new ArrayList<>();
}
