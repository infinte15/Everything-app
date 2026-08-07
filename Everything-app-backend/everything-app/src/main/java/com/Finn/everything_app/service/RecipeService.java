package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.mapper.RecipeMapper;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

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
        syncLegacyText(recipe);

        return recipeRepository.save(recipe);
    }

    public List<Recipe> getUserRecipes(Long userId) {
        return recipeRepository.findByUserId(userId);
    }

    public Recipe getRecipeById(Long userId, Long id) {
        return recipeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Rezept nicht gefunden"));
    }

    public List<Recipe> getRecipesByCategory(Long userId, String category) {
        return recipeRepository.findByUserIdAndCategory(userId, category);
    }

    public List<Recipe> getFavoriteRecipes(Long userId) {
        return recipeRepository.findByUserIdAndIsFavoriteTrue(userId);
    }

    public List<Recipe> searchRecipes(Long userId, String query) {
        return recipeRepository.findByUserIdAndNameContainingIgnoreCase(userId, query);
    }

    public List<Recipe> getQuickRecipes(Long userId, Integer maxMinutes) {
        return recipeRepository.findQuickRecipes(userId, maxMinutes);
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
        syncLegacyText(recipe);

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

    /**
     * Schreibt den Klartext-Spiegel in die Altspalten.
     *
     * <p>Uebergangsloesung fuer genau eine Phase: solange {@code recipes.ingredients} und
     * {@code recipes.instructions} noch existieren, halten sie den alten Client und einen
     * Rollback auf die vorige Jar am Leben. Gelesen werden sie nirgends mehr. Die Methode
     * faellt mit {@code 2026-08-07-recipe-drop-legacy-text.sql} weg.
     */
    private void syncLegacyText(Recipe recipe) {
        recipe.setIngredients(RecipeMapper.renderIngredients(recipe.getIngredientList()));
        recipe.setInstructions(RecipeMapper.renderSteps(recipe.getSteps()));
    }
}
