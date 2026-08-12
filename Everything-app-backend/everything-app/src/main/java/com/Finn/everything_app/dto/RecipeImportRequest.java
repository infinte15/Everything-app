package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RecipeImportRequest {

    /**
     * Die Adresse der Rezeptseite.
     *
     * <p>Die Laengenbegrenzung ist kein Schoenheitswunsch: ohne sie laeuft eine megabytegrosse
     * Zeichenkette bis in {@code new URI(...)} und durch die Regexe, bevor irgendetwas sie
     * ablehnt. Laenger als 2000 Zeichen ist keine Adresse, die ein Mensch eingefuegt hat.
     */
    @NotBlank(message = "Adresse erforderlich")
    @Size(max = 2000, message = "Adresse zu lang")
    private String url;
}
