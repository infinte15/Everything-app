package com.Finn.everything_app.mapper;


import com.Finn.everything_app.dto.RecipeDTO;
import com.Finn.everything_app.dto.RecipeIngredientDTO;
import com.Finn.everything_app.dto.RecipeStepDTO;
import com.Finn.everything_app.model.Recipe;
import com.Finn.everything_app.model.RecipeIngredient;
import com.Finn.everything_app.model.RecipeStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Uebersetzung zwischen {@link Recipe} und {@link RecipeDTO}.
 *
 * <p>Die Vorgaengerversion hat bei jedem Schreibvorgang Daten zerstoert:
 * {@code String.valueOf(Collections.singletonList(dto.getIngredients()))} verpackte den Text in
 * eine einelementige Liste und machte daraus wieder einen String, sodass aus
 * {@code "200 g Mehl\n1 Ei"} in der Datenbank {@code "[200 g Mehl\n1 Ei]"} wurde - mit einer
 * eckigen Klammer an der ersten und der letzten Zutat. Beim Lesen machte
 * {@code String.valueOf(recipe.getTags())} aus einem fehlenden {@code tags} die Zeichenfolge
 * {@code "null"}. Beides ist ersatzlos entfallen.
 */
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
        dto.setSuitableFor(new LinkedHashSet<>(recipe.getSuitableFor()));

        dto.setIngredients(recipe.getIngredientList().stream()
                .map(RecipeMapper::toIngredientDTO)
                .collect(Collectors.toList()));
        dto.setSteps(recipe.getSteps().stream()
                .map(RecipeMapper::toStepDTO)
                .collect(Collectors.toList()));

        dto.setIngredientsText(renderIngredients(recipe.getIngredientList()));
        dto.setInstructionsText(renderSteps(recipe.getSteps()));

        dto.setCalories(recipe.getCalories());
        dto.setProtein(recipe.getProtein());
        dto.setCarbs(recipe.getCarbs());
        dto.setFat(recipe.getFat());
        dto.setDifficulty(recipe.getDifficulty());
        dto.setImageUrl(recipe.getImageUrl());
        dto.setTags(recipe.getTags());
        dto.setIsFavorite(recipe.getIsFavorite());
        dto.setRating(recipe.getRating());
        dto.setCookCount(recipe.getCookCount());
        dto.setLastCookedAt(recipe.getLastCookedAt());
        dto.setNotes(recipe.getNotes());
        dto.setSourceUrl(recipe.getSourceUrl());
        dto.setSourceName(recipe.getSourceName());
        dto.setCreatedAt(recipe.getCreatedAt());
        dto.setUpdatedAt(recipe.getUpdatedAt());

        return dto;
    }

    /**
     * Baut ein loses {@link Recipe} aus dem DTO - ohne Benutzer, ohne Id-Aufloesung.
     *
     * <p>Die Zutaten- und Schrittlisten werden ueber
     * {@link Recipe#replaceIngredients(List)} gesetzt, damit die Positionen luecklos bei 0
     * beginnen. Ob der Client sie mitgeschickt hat, spielt keine Rolle: die Reihenfolge im
     * JSON ist die Reihenfolge im Rezept.
     */
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
        if (dto.getSuitableFor() != null) {
            recipe.setSuitableFor(new LinkedHashSet<>(dto.getSuitableFor()));
        }

        recipe.replaceIngredients(dto.getIngredients() == null
                ? new ArrayList<>()
                : dto.getIngredients().stream()
                        .map(RecipeMapper::toIngredientEntity)
                        .collect(Collectors.toList()));
        recipe.replaceSteps(dto.getSteps() == null
                ? new ArrayList<>()
                : dto.getSteps().stream()
                        .map(RecipeMapper::toStepEntity)
                        .collect(Collectors.toList()));

        recipe.setCalories(dto.getCalories());
        recipe.setProtein(dto.getProtein());
        recipe.setCarbs(dto.getCarbs());
        recipe.setFat(dto.getFat());
        recipe.setDifficulty(dto.getDifficulty());
        recipe.setImageUrl(dto.getImageUrl());
        recipe.setTags(dto.getTags());
        recipe.setIsFavorite(dto.getIsFavorite());
        recipe.setRating(dto.getRating());
        recipe.setNotes(dto.getNotes());
        recipe.setSourceUrl(dto.getSourceUrl());
        recipe.setSourceName(dto.getSourceName());

        return recipe;
    }

    // ── Kinder ────────────────────────────────────────────────────────────────────────────

    private static RecipeIngredientDTO toIngredientDTO(RecipeIngredient ingredient) {
        RecipeIngredientDTO dto = new RecipeIngredientDTO();
        dto.setId(ingredient.getId());
        dto.setAmount(ingredient.getAmount());
        dto.setUnit(ingredient.getUnit());
        dto.setName(ingredient.getName());
        dto.setNote(ingredient.getNote());
        dto.setRawText(ingredient.getRawText());
        dto.setGroupLabel(ingredient.getGroupLabel());
        return dto;
    }

    private static RecipeIngredient toIngredientEntity(RecipeIngredientDTO dto) {
        RecipeIngredient ingredient = new RecipeIngredient();
        ingredient.setAmount(dto.getAmount());
        ingredient.setUnit(blankToNull(dto.getUnit()));
        ingredient.setName(dto.getName() == null ? null : dto.getName().trim());
        ingredient.setNote(blankToNull(dto.getNote()));
        ingredient.setRawText(blankToNull(dto.getRawText()));
        ingredient.setGroupLabel(blankToNull(dto.getGroupLabel()));
        return ingredient;
    }

    private static RecipeStepDTO toStepDTO(RecipeStep step) {
        RecipeStepDTO dto = new RecipeStepDTO();
        dto.setId(step.getId());
        dto.setText(step.getText());
        return dto;
    }

    private static RecipeStep toStepEntity(RecipeStepDTO dto) {
        RecipeStep step = new RecipeStep();
        step.setText(dto.getText() == null ? null : dto.getText().trim());
        return step;
    }

    // ── Klartext ──────────────────────────────────────────────────────────────────────────

    /**
     * Zutaten als Klartext, eine je Zeile: "400 g Mehl", "3 Ei(er)", "Salz".
     *
     * <p>Dient zwei Zwecken: der lesbaren Antwort ({@code ingredientsText}) und - solange die
     * Altspalten noch existieren - dem Spiegel, den {@code RecipeService} dorthin schreibt.
     */
    public static String renderIngredients(List<RecipeIngredient> ingredients) {
        return ingredients.stream().map(RecipeMapper::renderIngredient)
                .collect(Collectors.joining("\n"));
    }

    private static String renderIngredient(RecipeIngredient ingredient) {
        StringBuilder line = new StringBuilder();
        if (ingredient.getAmount() != null) {
            line.append(ingredient.getAmount().stripTrailingZeros().toPlainString());
        }
        if (ingredient.getUnit() != null) {
            if (!line.isEmpty()) line.append(' ');
            line.append(ingredient.getUnit());
        }
        if (!line.isEmpty()) line.append(' ');
        line.append(ingredient.getName());
        if (ingredient.getNote() != null) {
            line.append(" (").append(ingredient.getNote()).append(')');
        }
        return line.toString();
    }

    public static String renderSteps(List<RecipeStep> steps) {
        return steps.stream().map(RecipeStep::getText).collect(Collectors.joining("\n"));
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
