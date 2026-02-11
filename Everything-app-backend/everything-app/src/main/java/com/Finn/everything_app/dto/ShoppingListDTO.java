package com.Finn.everything_app.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShoppingListDTO {
    private Map<String, List<String>> ingredientsByCategory;
    private Integer totalItems;
    private List<String> allIngredients;
}
