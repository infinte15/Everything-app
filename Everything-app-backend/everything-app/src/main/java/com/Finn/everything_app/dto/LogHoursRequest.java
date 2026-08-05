package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Nachgetragene Lernstunden. Positiv, weil „0 Stunden erfassen" nur ein leerer Schreibvorgang wäre. */
public record LogHoursRequest(
        @NotNull(message = "Stundenzahl erforderlich")
        @Positive(message = "Stundenzahl muss größer als 0 sein")
        Double hours) {}
