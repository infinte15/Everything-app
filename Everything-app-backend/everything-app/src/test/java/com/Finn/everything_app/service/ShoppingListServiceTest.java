package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import com.Finn.everything_app.service.recipe.IngredientAisleClassifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Der Kern der Einkaufsliste ist das Zusammenfassen. Der Vorgaenger warf die Zutaten in ein
 * {@code HashSet} - "200 g Mehl" und "300 g Mehl" wurden zu einer Zeile ohne Summe.
 */
@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

    @Mock ShoppingItemRepository shoppingItemRepository;
    @Mock MealPlanRepository mealPlanRepository;
    @Mock UserRepository userRepository;

    private ShoppingListService service;

    @BeforeEach
    void setUp() {
        // Der Regal-Klassifikator ist echt: er liest eine mitgelieferte Ressource, und ob die
        // Stichwoerter passen, ist Teil dessen, was hier geprueft wird.
        service = new ShoppingListService(shoppingItemRepository, mealPlanRepository,
                userRepository, new IngredientAisleClassifier(new ObjectMapper()));
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        return user;
    }

    private RecipeIngredient ingredient(String name, String amount, String unit) {
        RecipeIngredient ingredient = new RecipeIngredient();
        ingredient.setName(name);
        ingredient.setAmount(amount == null ? null : new BigDecimal(amount));
        ingredient.setUnit(unit);
        return ingredient;
    }

    private MealPlan mealPlan(int recipeServings, Integer plannedServings, RecipeIngredient... items) {
        Recipe recipe = new Recipe();
        recipe.setId(1L);
        recipe.setName("Rezept");
        recipe.setServings(recipeServings);
        recipe.replaceIngredients(new ArrayList<>(List.of(items)));

        MealPlan plan = new MealPlan();
        plan.setRecipe(recipe);
        plan.setPlannedServings(plannedServings);
        plan.setDate(LocalDate.now());
        plan.setMealType(MealType.ABENDESSEN);
        return plan;
    }

    private List<ShoppingItem> rebuildWith(List<MealPlan> plans) {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(mealPlanRepository.findByUserIdAndDateBetween(eq(1L), any(), any())).thenReturn(plans);
        when(shoppingItemRepository.findByUserIdAndSourceAndIsChecked(
                1L, ShoppingItemSource.MEAL_PLAN, false)).thenReturn(List.of());
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(i -> i.getArgument(0));

        return service.rebuildFromMealPlan(1L, LocalDate.now(), LocalDate.now().plusDays(7));
    }

    private ShoppingItem byName(List<ShoppingItem> items, String name) {
        return items.stream().filter(i -> i.getName().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Keine Zeile fuer " + name + " in " + items));
    }

    @Test
    void gleicheZutatWirdZuEinerZeileMitSumme() {
        List<ShoppingItem> items = rebuildWith(List.of(
                mealPlan(4, 4, ingredient("Mehl", "200", "g")),
                mealPlan(4, 4, ingredient("Mehl", "300", "g"))
        ));

        assertEquals(1, items.size());
        assertEquals(0, new BigDecimal("500").compareTo(byName(items, "Mehl").getAmount()));
        assertEquals("g", byName(items, "Mehl").getUnit());
    }

    @Test
    void kilogrammUndGrammWerdenZusammengerechnet() {
        List<ShoppingItem> items = rebuildWith(List.of(
                mealPlan(4, 4, ingredient("Mehl", "1", "kg")),
                mealPlan(4, 4, ingredient("Mehl", "500", "g"))
        ));

        assertEquals(1, items.size());
        assertEquals(0, new BigDecimal("1500").compareTo(byName(items, "Mehl").getAmount()));
        assertEquals("g", byName(items, "Mehl").getUnit());
    }

    // Ohne die Dichte des Oels ist jede Summe geraten, und eine falsche Zahl auf einem
    // Einkaufszettel ist schlimmer als zwei Zeilen, die man selbst zusammenzaehlt.
    @Test
    void unvereinbareEinheitenBleibenZweiZeilen() {
        List<ShoppingItem> items = rebuildWith(List.of(
                mealPlan(4, 4, ingredient("Öl", "2", "EL")),
                mealPlan(4, 4, ingredient("Öl", "100", "ml"))
        ));

        assertEquals(2, items.size());
    }

    // Vorher wurde gar nicht skaliert: wer ein Vier-Personen-Rezept fuer acht einplante,
    // bekam die Zutaten fuer vier auf den Zettel.
    @Test
    void diePortionenDesWochenplansSkalierenDieMengen() {
        List<ShoppingItem> items = rebuildWith(List.of(
                mealPlan(4, 8, ingredient("Mehl", "200", "g"))
        ));

        assertEquals(0, new BigDecimal("400").compareTo(byName(items, "Mehl").getAmount()));
    }

    // "0 g Salz" waere schlimmer als "Salz".
    @Test
    void zutatenOhneMengeBleibenOhneMenge() {
        List<ShoppingItem> items = rebuildWith(List.of(
                mealPlan(4, 4, ingredient("Salz", null, null)),
                mealPlan(4, 4, ingredient("Salz", null, null))
        ));

        assertEquals(1, items.size());
        assertNull(byName(items, "Salz").getAmount());
    }

    @Test
    void grossKleinschreibungTrenntNichtZweiZeilen() {
        List<ShoppingItem> items = rebuildWith(List.of(
                mealPlan(4, 4, ingredient("Mehl", "200", "g")),
                mealPlan(4, 4, ingredient("mehl", "100", "g"))
        ));

        assertEquals(1, items.size());
        assertEquals(0, new BigDecimal("300").compareTo(items.get(0).getAmount()));
    }

    @Test
    void zutatenLandenImPassendenRegal() {
        List<ShoppingItem> items = rebuildWith(List.of(mealPlan(4, 4,
                ingredient("Mehl", "200", "g"),
                ingredient("Tomate", "3", null),
                ingredient("Butter", null, null),
                ingredient("Rinderhackfleisch", "500", "g"))));

        assertEquals("Trockenware", byName(items, "Mehl").getCategory());
        assertEquals("Obst & Gemüse", byName(items, "Tomate").getCategory());
        assertEquals("Kühlregal", byName(items, "Butter").getCategory());
        assertEquals("Fleisch & Fisch", byName(items, "Rinderhackfleisch").getCategory());
    }

    // Sonst kommt Kokosmilch wegen "milch" ins Kuehlregal - und wer davor steht, findet sie
    // dort nicht.
    @Test
    void dasLaengsteStichwortGewinnt() {
        IngredientAisleClassifier classifier = new IngredientAisleClassifier(new ObjectMapper());

        assertEquals("Konserven", classifier.classify("Kokosmilch"));
        assertEquals("Kühlregal", classifier.classify("Milch"));
        assertEquals("Sonstiges", classifier.classify("Wunderpulver"));
    }

    // Ein Klick auf "aktualisieren" mitten im Supermarkt darf nicht die eigene Liste
    // wegwerfen und nicht den Fortschritt loeschen.
    @Test
    void derNeuaufbauFasstNurUnerledigteWochenplanZeilenAn() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(mealPlanRepository.findByUserIdAndDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(shoppingItemRepository.findByUserIdAndSourceAndIsChecked(
                1L, ShoppingItemSource.MEAL_PLAN, false)).thenReturn(List.of(new ShoppingItem()));

        service.rebuildFromMealPlan(1L, LocalDate.now(), LocalDate.now().plusDays(7));

        // Genau diese eine Abfrage - MANUAL und Abgehaktes wird nie geholt und nie geloescht.
        verify(shoppingItemRepository)
                .findByUserIdAndSourceAndIsChecked(1L, ShoppingItemSource.MEAL_PLAN, false);
        verify(shoppingItemRepository, never()).findByUserIdAndIsChecked(any(), any());
    }

    @Test
    void erzeugteZeilenSindAlsAusDemWochenplanErkennbar() {
        List<ShoppingItem> items = rebuildWith(List.of(
                mealPlan(4, 4, ingredient("Mehl", "200", "g"))));

        assertEquals(ShoppingItemSource.MEAL_PLAN, items.get(0).getSource());
        assertFalse(items.get(0).getIsChecked());
    }
}
