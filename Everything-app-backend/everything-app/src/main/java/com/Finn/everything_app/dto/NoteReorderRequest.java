package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Die neue Reihenfolge einer Ebene: orderIndex wird zur Position in dieser Liste. */
public record NoteReorderRequest(
        @NotEmpty(message = "noteIds erforderlich") List<Long> noteIds) {
}
