package com.Finn.everything_app.service.recipe;

import java.math.BigDecimal;

/**
 * Das Ergebnis einer zerlegten Zutatenzeile.
 *
 * @param amount  Menge, oder {@code null} - "Salz" hat keine
 * @param unit    kanonische Einheit aus {@link UnitVocabulary}, oder {@code null}
 * @param name    was gekauft wird; im schlechtesten Fall die ganze Zeile
 * @param note    Zusatz, der den Einkauf nicht veraendert ("gehäuft", "zum Ausrollen")
 * @param rawText die Zeile, wie sie ankam
 */
public record ParsedIngredient(
        BigDecimal amount,
        String unit,
        String name,
        String note,
        String rawText
) {
}
