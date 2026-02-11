package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    @Transactional
    public Recipe createRecipe(Long userId, Recipe recipe) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        recipe.setUser(user);
        recipe.setIsFavorite(recipe.getIsFavorite() != null ? recipe.getIsFavorite() : false);

        return recipeRepository.save(recipe);
    }

    public List<Recipe> getUserRecipes(Long userId) {
        return recipeRepository.findByUserId(userId);
    }

    public Recipe getRecipeById(Long id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rezept nicht gefunden"));
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
        return recipeRepository.findQuickRecipes(userId,maxMinutes);
    }

    @Transactional
    public Recipe updateRecipe(Long id, Recipe updatedRecipe) {
        Recipe recipe = getRecipeById(id);

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
        if (updatedRecipe.getIngredients() != null) {
            recipe.setIngredients(updatedRecipe.getIngredients());
        }
        if (updatedRecipe.getInstructions() != null) {
            recipe.setInstructions(updatedRecipe.getInstructions());
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

        return recipeRepository.save(recipe);
    }

    @Transactional
    public Recipe toggleFavorite(Long id) {
        Recipe recipe = getRecipeById(id);
        recipe.setIsFavorite(!recipe.getIsFavorite());
        return recipeRepository.save(recipe);
    }

    @Transactional
    public void deleteRecipe(Long id) {
        Recipe recipe = getRecipeById(id);
        recipeRepository.delete(recipe);
    }
}