package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeStepDTO {

    private Long id;

    @NotBlank(message = "Schritt darf nicht leer sein")
    private String text;
}
