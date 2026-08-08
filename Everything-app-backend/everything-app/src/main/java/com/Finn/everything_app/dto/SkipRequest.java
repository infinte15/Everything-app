package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Body für PUT /api/calendar/events/{id}/skip. */
@Data
public class SkipRequest {

    @NotNull(message = "skipped muss angegeben werden")
    private Boolean skipped;
}
