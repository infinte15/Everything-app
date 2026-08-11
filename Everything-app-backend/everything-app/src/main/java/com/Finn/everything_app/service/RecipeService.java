package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.RecipeCookLogDTO;
import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rezepte anlegen, lesen, aendern, loeschen.
 *
 * <p>Jede Methode nimmt die {@code userId} entgegen und laedt ueber
 * {@link RecipeRepository#findByIdAndUserId}. Vorher gingen {@code getRecipeById},
 * {@code updateRecipe}, {@code toggleFavorite} und {@code deleteRecipe} allein ueber die Id -
 * wer eine fremde Id kannte, kam an fremde Rezepte. Ein fremdes Rezept gibt hier 404, nicht
 * 403: was einem nicht gehoert, soll nicht einmal als existent erkennbar sein.
 *
 * <p><b>Warum die Lesemethoden {@code @Transactional} sind und die Sammlungen anfassen.</b>
 * {@code spring.jpa.open-in-view=false} - die Sitzung endet also mit dem Repository-Aufruf und
 * nicht mit der Antwort. {@link com.Finn.everything_app.mapper.RecipeMapper#toDTO} liest aber
 * {@code ingredientList} und {@code steps}, und beide sind LAZY. Ohne diese Klammer wurde
 * jeder Lesezugriff auf ein Rezept zu einem 500 mit
 * {@code LazyInitializationException} - die Liste, die Suche, die Detailseite, alles. Sichtbar
 * war das nur an einem einzigen Test, weil kein anderer die Zutaten der Antwort geprueft hat.
 */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final RecipeCookLogRepository cookLogRepository;

    @Transactional
    public Recipe createRecipe(Long userId, Recipe recipe) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        recipe.setUser(user);
        recipe.setIsFavorite(recipe.getIsFavorite() != null ? recipe.getIsFavorite() : false);
        recipe.setCookCount(recipe.getCookCount() != null ? recipe.getCookCount() : 0);
        if (recipe.getSuitableFor().isEmpty()) {
            recipe.setSuitableFor(defaultMealTypes(recipe.getCategory()));
        }
        return recipeRepository.save(recipe);
    }

    @Transactional(readOnly = true)
    public List<Recipe> getUserRecipes(Long userId) {
        return withContent(recipeRepository.findByUserId(userId));
    }

    @Transactional(readOnly = true)
    public Recipe getRecipeById(Long userId, Long id) {
        return withContent(recipeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Rezept nicht gefunden")));
    }

    @Transactional(readOnly = true)
    public List<Recipe> getRecipesByCategory(Long userId, String category) {
        return withContent(recipeRepository.findByUserIdAndCategory(userId, category));
    }

    @Transactional(readOnly = true)
    public List<Recipe> getFavoriteRecipes(Long userId) {
        return withContent(recipeRepository.findByUserIdAndIsFavoriteTrue(userId));
    }

    /** Sucht ueber Name, Tags und Zutaten. Ein leerer Suchbegriff gibt nichts zurueck. */
    @Transactional(readOnly = true)
    public List<Recipe> searchRecipes(Long userId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return withContent(recipeRepository.search(userId, query.trim()));
    }

    @Transactional(readOnly = true)
    public List<Recipe> getQuickRecipes(Long userId, Integer maxMinutes) {
        return withContent(recipeRepository.findQuickRecipes(userId, maxMinutes));
    }

    /**
     * Laedt Zutaten und Schritte nach, solange die Sitzung noch offen ist.
     *
     * <p>Kein {@code JOIN FETCH} in der Abfrage: es sind zwei Sammlungen ohne Sortierspalte,
     * und Hibernate lehnt zwei solche Verbunde in einer Abfrage mit
     * {@code MultipleBagFetchException} ab. Die Sammlungen tragen deshalb {@code @BatchSize},
     * damit hier nicht pro Rezept zwei Abfragen entstehen, sondern zwei je Seite.
     */
    private static List<Recipe> withContent(List<Recipe> recipes) {
        recipes.forEach(RecipeService::withContent);
        return recipes;
    }

    private static Recipe withContent(Recipe recipe) {
        recipe.getIngredientList().size();
        recipe.getSteps().size();
        return recipe;
    }

    /**
     * Aendert ein Rezept.
     *
     * <p>Skalare Felder werden nur uebernommen, wenn sie gesetzt sind - das erlaubt
     * Teil-Aktualisierungen. Zutaten und Schritte dagegen werden **ersetzt**, sobald sie
     * mitgeschickt werden: eine Liste zu mischen ist nicht definierbar, und "Zutat entfernen"
     * waere anders gar nicht ausdrueckbar.
     */
    @Transactional
    public Recipe updateRecipe(Long userId, Long id, Recipe updatedRecipe) {
        Recipe recipe = getRecipeById(userId, id);

        if (updatedRecipe.getName() != null) {
            recipe.setName(updatedRecipe.getName());
        }
        if (updatedRecipe.getDescription() != null) {
            recipe.setDescription(updatedRecipe.getDescription());
        }
        if (updatedRecipe.getPrepTimeMinutes() != null) {
            recipe.setPrepTimeMinutes(updatedRecipe.getPrepTimeMinutes());
        }
        if (updatedRecipe.getCookTimeMinutes() != null) {
            recipe.setCookTimeMinutes(updatedRecipe.getCookTimeMinutes());
        }
        if (updatedRecipe.getServings() != null) {
            recipe.setServings(updatedRecipe.getServings());
        }
        if (updatedRecipe.getCategory() != null) {
            recipe.setCategory(updatedRecipe.getCategory());
        }
        if (!updatedRecipe.getSuitableFor().isEmpty()) {
            recipe.setSuitableFor(new LinkedHashSet<>(updatedRecipe.getSuitableFor()));
        }
        if (!updatedRecipe.getIngredientList().isEmpty()) {
            recipe.replaceIngredients(updatedRecipe.getIngredientList());
        }
        if (!updatedRecipe.getSteps().isEmpty()) {
            recipe.replaceSteps(updatedRecipe.getSteps());
        }
        if (updatedRecipe.getCalories() != null) {
            recipe.setCalories(updatedRecipe.getCalories());
        }
        if (updatedRecipe.getProtein() != null) {
            recipe.setProtein(updatedRecipe.getProtein());
        }
        if (updatedRecipe.getCarbs() != null) {
            recipe.setCarbs(updatedRecipe.getCarbs());
        }
        if (updatedRecipe.getFat() != null) {
            recipe.setFat(updatedRecipe.getFat());
        }
        if (updatedRecipe.getDifficulty() != null) {
            recipe.setDifficulty(updatedRecipe.getDifficulty());
        }
        if (updatedRecipe.getImageUrl() != null) {
            recipe.setImageUrl(updatedRecipe.getImageUrl());
        }
        if (updatedRecipe.getTags() != null) {
            recipe.setTags(updatedRecipe.getTags());
        }
        if (updatedRecipe.getNotes() != null) {
            recipe.setNotes(updatedRecipe.getNotes());
        }
        if (updatedRecipe.getSourceUrl() != null) {
            recipe.setSourceUrl(updatedRecipe.getSourceUrl());
        }
        return recipeRepository.save(recipe);
    }

    @Transactional
    public Recipe toggleFavorite(Long userId, Long id) {
        Recipe recipe = getRecipeById(userId, id);
        // Null-sicher: Zeilen aus der Zeit vor der Vorgabe tragen dort nichts, und
        // !null war eine NullPointerException.
        recipe.setIsFavorite(!Boolean.TRUE.equals(recipe.getIsFavorite()));
        return recipeRepository.save(recipe);
    }

    @Transactional
    public void deleteRecipe(Long userId, Long id) {
        Recipe recipe = getRecipeById(userId, id);
        recipeRepository.delete(recipe);
    }

    // ── Bewertung und Kochprotokoll ───────────────────────────────────────────────────────

    @Transactional
    public Recipe rate(Long userId, Long id, Short rating) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BadRequestException("Bewertung liegt zwischen 1 und 5.");
        }
        Recipe recipe = getRecipeById(userId, id);
        recipe.setRating(rating);
        return recipeRepository.save(recipe);
    }

    /**
     * Haelt fest, dass das Rezept gekocht wurde.
     *
     * <p>Schreibt den Protokolleintrag und zieht die Zaehler am Rezept in derselben
     * Transaktion nach. Die Zaehler sind bewusst denormalisiert: die Entdecken-Reihen wollen
     * ein {@code ORDER BY last_cooked_at}, kein Group-by ueber das Protokoll bei jedem Aufruf.
     *
     * <p>Eine mitgegebene Bewertung gilt auch als aktuelle Bewertung des Rezepts - wer nach
     * dem Kochen Sterne vergibt, meint das Rezept, nicht nur diesen Abend.
     */
    @Transactional
    public Recipe logCooked(Long userId, Long id, RecipeCookLogDTO entry) {
        Recipe recipe = getRecipeById(userId, id);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        if (entry.getRating() != null && (entry.getRating() < 1 || entry.getRating() > 5)) {
            throw new BadRequestException("Bewertung liegt zwischen 1 und 5.");
        }

        LocalDateTime cookedAt = entry.getCookedAt() != null ? entry.getCookedAt() : LocalDateTime.now();

        RecipeCookLog log = new RecipeCookLog();
        log.setRecipe(recipe);
        log.setUser(user);
        log.setCookedAt(cookedAt);
        log.setRating(entry.getRating());
        log.setServings(entry.getServings() != null ? entry.getServings() : recipe.getServings());
        log.setNote(entry.getNote());
        cookLogRepository.save(log);

        recipe.setCookCount((recipe.getCookCount() == null ? 0 : recipe.getCookCount()) + 1);
        // Nachtragen eines aelteren Termins darf "zuletzt gekocht" nicht zurueckdrehen.
        if (recipe.getLastCookedAt() == null || cookedAt.isAfter(recipe.getLastCookedAt())) {
            recipe.setLastCookedAt(cookedAt);
        }
        if (entry.getRating() != null) {
            recipe.setRating(entry.getRating());
        }

        return recipeRepository.save(recipe);
    }

    /** Lesend transaktional, weil der Mapper ueber {@code log.getRecipe()} den Namen holt. */
    @Transactional(readOnly = true)
    public List<RecipeCookLog> getCookLog(Long userId, Long recipeId) {
        getRecipeById(userId, recipeId);
        List<RecipeCookLog> logs =
                cookLogRepository.findByRecipeIdAndUserIdOrderByCookedAtDesc(recipeId, userId);
        logs.forEach(log -> log.getRecipe().getName());
        return logs;
    }

    /**
     * Nimmt einen Protokolleintrag zurueck und korrigiert die Zaehler.
     *
     * <p>{@code lastCookedAt} wird aus dem verbleibenden Protokoll neu bestimmt, nicht
     * einfach auf null gesetzt - sonst verschwindet ein Rezept nach einem Vertipper aus
     * "zuletzt gekocht", obwohl es dreimal gekocht wurde.
     */
    @Transactional
    public void deleteCookLog(Long userId, Long logId) {
        RecipeCookLog log = cookLogRepository.findByIdAndUserId(logId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Eintrag nicht gefunden"));

        Recipe recipe = log.getRecipe();
        cookLogRepository.delete(log);

        List<RecipeCookLog> remaining =
                cookLogRepository.findByRecipeIdAndUserIdOrderByCookedAtDesc(recipe.getId(), userId);
        recipe.setCookCount(remaining.size());
        recipe.setLastCookedAt(remaining.isEmpty() ? null : remaining.get(0).getCookedAt());
        recipeRepository.save(recipe);
    }

    // ── Entdecken ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Recipe> getRecentlyCooked(Long userId) {
        return withContent(
                recipeRepository.findByUserIdAndLastCookedAtIsNotNullOrderByLastCookedAtDesc(userId));
    }

    /** Lange nicht gekocht - siehe die Bedingung in der Abfrage, sie ist der Kern. */
    @Transactional(readOnly = true)
    public List<Recipe> getNotCookedInAWhile(Long userId, int days) {
        return withContent(
                recipeRepository.findNotCookedSince(userId, LocalDateTime.now().minusDays(days)));
    }

    @Transactional(readOnly = true)
    public List<Recipe> getNeverCooked(Long userId) {
        return withContent(recipeRepository.findByUserIdAndCookCountOrderByCreatedAtDesc(userId, 0));
    }

    @Transactional(readOnly = true)
    public List<Recipe> getBestRated(Long userId, short minRating) {
        return withContent(recipeRepository.findBestRated(userId, minRating));
    }

    // ── Mahlzeiten-Eignung ────────────────────────────────────────────────────────────────

    /**
     * Vorbelegung aus der Kategorie, wenn der Client nichts mitgibt.
     *
     * <p>Eine Vorgabe, keine Regel: die Menge ist danach editierbar. Sie existiert, damit der
     * Wochenplan direkt nach dem ersten Import etwas zu tun hat - ohne Eignung findet er
     * naemlich gar nichts.
     */
    static Set<MealType> defaultMealTypes(String category) {
        Set<MealType> types = new LinkedHashSet<>();
        if (category == null) {
            types.add(MealType.MITTAGESSEN);
            types.add(MealType.ABENDESSEN);
            return types;
        }
        switch (category) {
            case "Frühstück" -> types.add(MealType.FRUEHSTUECK);
            case "Backen", "Dessert", "Vorspeise & Snack", "Getränk" -> types.add(MealType.SNACK);
            case "Beilage & Sauce" -> {
                types.add(MealType.MITTAGESSEN);
                types.add(MealType.ABENDESSEN);
            }
            default -> {
                types.add(MealType.MITTAGESSEN);
                types.add(MealType.ABENDESSEN);
            }
        }
        return types;
    }
}
