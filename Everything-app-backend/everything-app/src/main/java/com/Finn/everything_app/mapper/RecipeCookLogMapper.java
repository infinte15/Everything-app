package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.RecipeCookLogDTO;
import com.Finn.everything_app.model.RecipeCookLog;
import org.springframework.stereotype.Component;

@Component
public class RecipeCookLogMapper {

    public RecipeCookLogDTO toDTO(RecipeCookLog log) {
        if (log == null) return null;

        RecipeCookLogDTO dto = new RecipeCookLogDTO();
        dto.setId(log.getId());
        dto.setRecipeId(log.getRecipe() != null ? log.getRecipe().getId() : null);
        dto.setRecipeName(log.getRecipe() != null ? log.getRecipe().getName() : null);
        dto.setCookedAt(log.getCookedAt());
        dto.setRating(log.getRating());
        dto.setServings(log.getServings());
        dto.setNote(log.getNote());

        return dto;
    }
}
