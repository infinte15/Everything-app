package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeIngredientDTO {

    private Long id;

    /**
     * Menge, bezogen auf {@code RecipeDTO.servings}.
     *
     * <p>Nullbar und ohne Untergrenze-Validierung: "Salz" hat keine Menge, und eine erzwungene
     * 0 wuerde beim Umrechnen mitwandern und als "0 g Salz" auf dem Einkaufszettel landen.
     */
    private BigDecimal amount;

    @Size(max = 30)
    private String unit;

    @NotBlank(message = "Zutat braucht einen Namen")
    @Size(max = 200)
    private String name;

    @Size(max = 200)
    private String note;

    /** Was urspruenglich dastand. Wird nur gelesen und beim Import mitgegeben. */
    @Size(max = 300)
    private String rawText;

    @Size(max = 100)
    private String groupLabel;
}
