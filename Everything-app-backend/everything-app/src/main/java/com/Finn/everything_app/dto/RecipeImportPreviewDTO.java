package com.Finn.everything_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Was ein Import gefunden hat - noch nicht gespeichert.
 *
 * <p>Der Import laeuft in zwei Schritten, und das ist Absicht: ein Import, der still ein
 * halbfalsches Rezept anlegt, ist schlimmer als gar keiner. Hier steht das Ergebnis zur
 * Ansicht, {@link #warnings} sagt, was nicht sauber gelesen werden konnte, und erst die
 * Bestaetigung des Nutzers geht ueber den normalen Anlege-Weg.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeImportPreviewDTO {

    /** Das gefundene Rezept, {@code id} ist null. */
    private RecipeDTO recipe;

    /** Deutsche Klartexthinweise, die im Import-Sheet unter dem Rezept stehen. */
    private List<String> warnings = new ArrayList<>();
}
