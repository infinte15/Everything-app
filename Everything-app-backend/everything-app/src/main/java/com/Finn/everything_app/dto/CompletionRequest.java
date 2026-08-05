package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Body für PUT /api/calendar/events/{id}/complete. */
@Data
public class CompletionRequest {

    @NotNull(message = "completed muss angegeben werden")
    private Boolean completed;
}
