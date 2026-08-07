package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.RecipeCookLogDTO;
import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock RecipeRepository recipeRepository;
    @Mock UserRepository userRepository;
    @Mock RecipeCookLogRepository cookLogRepository;

    @InjectMocks
    RecipeService service;

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private RecipeIngredient ingredient(String name, String amount, String unit) {
        RecipeIngredient ingredient = new RecipeIngredient();
        ingredient.setName(name);
        ingredient.setAmount(amount == null ? null : new BigDecimal(amount));
        ingredient.setUnit(unit);
        return ingredient;
    }

    private Recipe recipe(long id, String category) {
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName("Pfannkuchen");
        recipe.setPrepTimeMinutes(10);
        recipe.setCookTimeMinutes(20);
        recipe.setServings(4);
        recipe.setCategory(category);
        recipe.replaceIngredients(new ArrayList<>(List.of(ingredient("Mehl", "400", "g"))));
        recipe.replaceSteps(new ArrayList<>());
        return recipe;
    }

    // Fremde Rezepte duerfen nicht einmal als "existiert" erkennbar sein - deshalb 404.
    // Vorher lief der Zugriff ueber findById und lieferte sie einfach aus.
    @Test
    void fremdesRezeptIstNichtGefunden() {
        when(recipeRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getRecipeById(1L, 7L));
    }

    @Test
    void fremdesRezeptLaesstSichNichtLoeschen() {
        when(recipeRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteRecipe(1L, 7L));
        verify(recipeRepository, never()).delete(any());
    }

    // Zeilen aus der Zeit vor der Vorgabe tragen kein is_favorite - !null war eine NPE.
    @Test
    void favoritSchaltenVertraegtEinenLeerenWert() {
        Recipe recipe = recipe(1L, "Hauptgericht");
        recipe.setIsFavorite(null);
        when(recipeRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(recipe));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> i.getArgument(0));

        Recipe result = service.toggleFavorite(1L, 1L);

        assertTrue(result.getIsFavorite());
    }

    @Test
    void neuesRezeptErbtDieMahlzeitenAusSeinerKategorie() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> i.getArgument(0));

        Recipe created = service.createRecipe(1L, recipe(1L, "Frühstück"));

        assertEquals(java.util.Set.of(MealType.FRUEHSTUECK), created.getSuitableFor());
    }

    @Test
    void eineMitgegebeneEignungWirdNichtUeberschrieben() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> i.getArgument(0));

        Recipe input = recipe(1L, "Frühstück");
        input.getSuitableFor().add(MealType.ABENDESSEN);

        Recipe created = service.createRecipe(1L, input);

        assertEquals(java.util.Set.of(MealType.ABENDESSEN), created.getSuitableFor());
    }

    // Eine Zutatenliste wird ersetzt, nicht gemischt: sonst waere "Zutat entfernen" nicht
    // ausdrueckbar.
    @Test
    void aendernErsetztDieZutatenlisteUndNummeriertNeuDurch() {
        Recipe stored = recipe(1L, "Hauptgericht");
        when(recipeRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(stored));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> i.getArgument(0));

        Recipe update = new Recipe();
        update.replaceIngredients(new ArrayList<>(List.of(
                ingredient("Zucker", "2", "EL"),
                ingredient("Salz", null, null)
        )));

        Recipe result = service.updateRecipe(1L, 1L, update);

        assertEquals(2, result.getIngredientList().size());
        assertEquals("Zucker", result.getIngredientList().get(0).getName());
        assertEquals(0, result.getIngredientList().get(0).getPosition());
        assertEquals("Salz", result.getIngredientList().get(1).getName());
        assertEquals(1, result.getIngredientList().get(1).getPosition());
    }

    // Teil-Aktualisierung: wer nur den Namen schickt, verliert seine Zutaten nicht.
    @Test
    void aendernOhneZutatenLaesstDieAltenStehen() {
        Recipe stored = recipe(1L, "Hauptgericht");
        when(recipeRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(stored));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> i.getArgument(0));

        Recipe update = new Recipe();
        update.setName("Omas Pfannkuchen");

        Recipe result = service.updateRecipe(1L, 1L, update);

        assertEquals("Omas Pfannkuchen", result.getName());
        assertEquals(1, result.getIngredientList().size());
        assertEquals("Mehl", result.getIngredientList().get(0).getName());
    }

    @Test
    void neuesRezeptStartetMitNullMalGekocht() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> i.getArgument(0));

        Recipe created = service.createRecipe(1L, recipe(1L, "Hauptgericht"));

        assertEquals(0, created.getCookCount());
        assertNull(created.getLastCookedAt());
        assertFalse(created.getIsFavorite());
    }

    // ── Bewertung und Kochprotokoll ───────────────────────────────────────────────────────

    @Test
    void gekochtZaehltHochUndMerktSichDenZeitpunkt() {
        Recipe recipe = recipe(1L, "Hauptgericht");
        when(recipeRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(recipe));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> i.getArgument(0));

        Recipe result = service.logCooked(1L, 1L, new RecipeCookLogDTO());

        assertEquals(1, result.getCookCount());
        assertNotNull(result.getLastCookedAt());
        verify(cookLogRepository).save(any(RecipeCookLog.class));
    }

    // Wer einen alten Termin nachtraegt, soll "zuletzt gekocht" nicht zurueckdrehen.
    @Test
    void einNachgetragenerAlterTerminDrehtZuletztGekochtNichtZurueck() {
        Recipe recipe = recipe(1L, "Hauptgericht");
        LocalDateTime gestern = LocalDateTime.now().minusDays(1);
        recipe.setLastCookedAt(gestern);
        recipe.setCookCount(1);
        when(recipeRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(recipe));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> i.getArgument(0));

        RecipeCookLogDTO entry = new RecipeCookLogDTO();
        entry.setCookedAt(LocalDateTime.now().minusDays(30));
        Recipe result = service.logCooked(1L, 1L, entry);

        assertEquals(gestern, result.getLastCookedAt());
        assertEquals(2, result.getCookCount());
    }

    @Test
    void eineBewertungBeimKochenGiltAuchFuerDasRezept() {
        Recipe recipe = recipe(1L, "Hauptgericht");
        when(recipeRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(recipe));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> i.getArgument(0));

        RecipeCookLogDTO entry = new RecipeCookLogDTO();
        entry.setRating((short) 5);

        assertEquals((short) 5, service.logCooked(1L, 1L, entry).getRating());
    }

    @Test
    void eineBewertungAusserhalbVonEinsBisFuenfWirdAbgelehnt() {
        assertThrows(BadRequestException.class, () -> service.rate(1L, 1L, (short) 6));
        assertThrows(BadRequestException.class, () -> service.rate(1L, 1L, (short) 0));
        // Und nichts davon hat das Rezept ueberhaupt geladen.
        verify(recipeRepository, never()).save(any());
    }

    // Ein Vertipper darf ein dreimal gekochtes Rezept nicht aus "zuletzt gekocht" werfen -
    // deshalb wird der Stand aus dem verbleibenden Protokoll neu bestimmt.
    @Test
    void einGeloeschterProtokolleintragSetztDieZaehlerNeuStattSieZuLeeren() {
        Recipe recipe = recipe(1L, "Hauptgericht");
        recipe.setCookCount(2);
        recipe.setLastCookedAt(LocalDateTime.now());

        RecipeCookLog toDelete = new RecipeCookLog();
        toDelete.setId(9L);
        toDelete.setRecipe(recipe);

        LocalDateTime frueher = LocalDateTime.now().minusDays(10);
        RecipeCookLog remaining = new RecipeCookLog();
        remaining.setCookedAt(frueher);

        when(cookLogRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(toDelete));
        when(cookLogRepository.findByRecipeIdAndUserIdOrderByCookedAtDesc(1L, 1L))
                .thenReturn(List.of(remaining));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> i.getArgument(0));

        service.deleteCookLog(1L, 9L);

        assertEquals(1, recipe.getCookCount());
        assertEquals(frueher, recipe.getLastCookedAt());
    }
}
