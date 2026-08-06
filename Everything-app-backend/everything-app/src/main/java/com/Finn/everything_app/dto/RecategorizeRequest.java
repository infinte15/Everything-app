package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Korrektur einer Kategorie durch den Nutzer - Grundlage der gelernten Regel. */
@Data
public class RecategorizeRequest {

    @NotBlank(message = "Kategorie erforderlich")
    private String category;

    private String subcategory;

    /** Ob dieselbe Gegenpartei rueckwirkend umkategorisiert wird. */
    private boolean applyToPast;
}
