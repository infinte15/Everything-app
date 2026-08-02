package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Body für PUT /api/calendar/events/{id}/pin. */
@Data
public class PinRequest {

    @NotNull(message = "pinned muss angegeben werden")
    private Boolean pinned;
}
