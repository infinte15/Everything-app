package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecipeImportRequest {

    @NotBlank(message = "Adresse erforderlich")
    private String url;
}
