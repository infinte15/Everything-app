package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Mehrere Zutatenzeilen auf einmal, zum Zerlegen ohne zu speichern.
 *
 * <p>Damit die von Hand eingefuegten Zeilen durch denselben Parser laufen wie die
 * importierten - zwei Implementierungen wuerden auseinanderdriften.
 */
@Data
public class IngredientParseRequest {

    @NotNull(message = "Text erforderlich")
    private String text;
}
