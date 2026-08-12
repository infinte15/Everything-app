package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Eingefuegter Rezepttext - typisch eine Instagram-Bildunterschrift.
 *
 * <p>Anders als {@link RecipeImportRequest} traegt diese Anfrage den Inhalt selbst und keine
 * Adresse. Genau darin liegt der Unterschied: hier ruft der Server nichts ab.
 */
@Data
public class RecipeTextImportRequest {

    @NotBlank(message = "Text erforderlich")
    @Size(max = 20000, message = "Der Text ist zu lang - das ist wohl kein Rezept.")
    private String text;

    /** Woher der Text stammt. Leer heisst "Instagram". */
    @Size(max = 100)
    private String sourceName;

    /**
     * Wird nur gespeichert, nie abgerufen - das ist der ganze Unterschied zum Adress-Import.
     *
     * <p>Gesetzt vor allem, wenn der Instagram-Abruf an einer Anmeldeseite gescheitert ist und
     * der Nutzer die Bildunterschrift von Hand eingefuegt hat: der Text kam dann aus der
     * Zwischenablage, die Herkunft soll trotzdem am Rezept stehen.
     */
    @Size(max = 500)
    private String sourceUrl;
}
