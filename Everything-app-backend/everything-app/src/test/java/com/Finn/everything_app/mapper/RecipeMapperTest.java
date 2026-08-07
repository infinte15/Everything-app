package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.RecipeDTO;
import com.Finn.everything_app.dto.RecipeIngredientDTO;
import com.Finn.everything_app.dto.RecipeStepDTO;
import com.Finn.everything_app.model.Recipe;
import com.Finn.everything_app.model.RecipeIngredient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeMapperTest {

    private final RecipeMapper mapper = new RecipeMapper();

    private RecipeDTO dto() {
        RecipeDTO dto = new RecipeDTO();
        dto.setName("Pfannkuchen");
        dto.setPrepTimeMinutes(10);
        dto.setCookTimeMinutes(20);
        dto.setServings(4);
        dto.setCategory("Hauptgericht");
        dto.setIngredients(List.of(
                new RecipeIngredientDTO(null, new BigDecimal("400"), "g", "Mehl", null, "400 g Mehl", null),
                new RecipeIngredientDTO(null, new BigDecimal("3"), null, "Ei(er)", null, "3 Ei(er)", null)
        ));
        dto.setSteps(List.of(
                new RecipeStepDTO(null, "Mehl sieben."),
                new RecipeStepDTO(null, "Eier unterrühren.")
        ));
        return dto;
    }

    // Die Regression, wegen der es diesen Test gibt: toEntity schrieb
    // String.valueOf(Collections.singletonList(text)) in die Spalte, sodass die erste Zutat
    // eine oeffnende und die letzte eine schliessende eckige Klammer bekam.
    @Test
    void hinUndZurueckLaesstKeineEckigenKlammernZurueck() {
        Recipe recipe = mapper.toEntity(dto());
        RecipeDTO back = mapper.toDTO(recipe);

        assertEquals("Mehl", back.getIngredients().get(0).getName());
        assertEquals("Ei(er)", back.getIngredients().get(1).getName());
        assertFalse(back.getIngredientsText().contains("["));
        assertFalse(back.getIngredientsText().contains("]"));
    }

    // toDTO benutzte String.valueOf(recipe.getTags()) - aus einem fehlenden tags wurde damit
    // die Zeichenfolge "null", die der Client brav als Schlagwort angezeigt haette.
    @Test
    void fehlendeSchlagworteBleibenLeerUndWerdenNichtZuNull() {
        Recipe recipe = mapper.toEntity(dto());
        assertNull(recipe.getTags());

        RecipeDTO back = mapper.toDTO(recipe);
        assertNull(back.getTags());
    }

    @Test
    void zutatenBekommenIhrePositionAusDerReihenfolgeImJson() {
        Recipe recipe = mapper.toEntity(dto());

        assertEquals(0, recipe.getIngredientList().get(0).getPosition());
        assertEquals(1, recipe.getIngredientList().get(1).getPosition());
        assertEquals(0, recipe.getSteps().get(0).getPosition());
        assertEquals(1, recipe.getSteps().get(1).getPosition());
        assertSame(recipe, recipe.getIngredientList().get(0).getRecipe());
    }

    // Der Klartext ist das, was ein Mensch beim Pruefen der Antwort im Terminal lesen will -
    // dreissig JSON-Objekte sagen einem nicht, ob der Import etwas Sinnvolles erzeugt hat.
    @Test
    void klartextSchreibtMengeEinheitUndNameInEinerZeile() {
        Recipe recipe = mapper.toEntity(dto());

        assertEquals("400 g Mehl\n3 Ei(er)", RecipeMapper.renderIngredients(recipe.getIngredientList()));
    }

    // "0 g Salz" waere schlimmer als "Salz": die 0 wuerde beim Umrechnen mitwandern.
    @Test
    void zutatOhneMengeStehtNurMitNamenDa() {
        RecipeIngredient salt = new RecipeIngredient();
        salt.setName("Salz");

        assertEquals("Salz", RecipeMapper.renderIngredients(List.of(salt)));
    }
}
