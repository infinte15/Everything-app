package com.Finn.everything_app.mapper;


import com.Finn.everything_app.dto.RecipeDTO;
import com.Finn.everything_app.model.Recipe;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class RecipeMapper {

    public RecipeDTO toDTO(Recipe recipe) {
        if (recipe == null) return null;

        RecipeDTO dto = new RecipeDTO();
        dto.setId(recipe.getId());
        dto.setName(recipe.getName());
        dto.setDescription(recipe.getDescription());
        dto.setPrepTimeMinutes(recipe.getPrepTimeMinutes());
        dto.setCookTimeMinutes(recipe.getCookTimeMinutes());
        dto.setServings(recipe.getServings());
        dto.setCategory(recipe.getCategory());
        dto.setIngredients(String.valueOf(recipe.getIngredients()));
        dto.setInstructions(recipe.getInstructions());
        dto.setCalories(recipe.getCalories());
        dto.setProtein(recipe.getProtein());
        dto.setCarbs(recipe.getCarbs());
        dto.setFat(recipe.getFat());
        dto.setDifficulty(recipe.getDifficulty());
        dto.setImageUrl(recipe.getImageUrl());
        dto.setTags(String.valueOf(recipe.getTags()));
        dto.setIsFavorite(recipe.getIsFavorite());
        dto.setCreatedAt(recipe.getCreatedAt());
        dto.setUpdatedAt(recipe.getUpdatedAt());

        return dto;
    }

    public Recipe toEntity(RecipeDTO dto) {
        if (dto == null) return null;

        Recipe recipe = new Recipe();
        recipe.setId(dto.getId());
        recipe.setName(dto.getName());
        recipe.setDescription(dto.getDescription());
        recipe.setPrepTimeMinutes(dto.getPrepTimeMinutes());
        recipe.setCookTimeMinutes(dto.getCookTimeMinutes());
        recipe.setServings(dto.getServings());
        recipe.setCategory(dto.getCategory());
        recipe.setIngredients(String.valueOf(Collections.singletonList(dto.getIngredients())));
        recipe.setInstructions(dto.getInstructions());
        recipe.setCalories(dto.getCalories());
        recipe.setProtein(dto.getProtein());
        recipe.setCarbs(dto.getCarbs());
        recipe.setFat(dto.getFat());
        recipe.setDifficulty(dto.getDifficulty());
        recipe.setImageUrl(dto.getImageUrl());
        recipe.setTags(String.valueOf(Collections.singletonList(dto.getTags())));
        recipe.setIsFavorite(dto.getIsFavorite());

        return recipe;
    }
}