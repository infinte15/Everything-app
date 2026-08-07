package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.ShoppingItemSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ShoppingItemDTO {

    private Long id;

    @NotBlank(message = "Name erforderlich")
    @Size(max = 200)
    private String name;

    private BigDecimal amount;

    @Size(max = 30)
    private String unit;

    /** Regal. Leer heisst: vom Server einsortieren lassen. */
    @Size(max = 50)
    private String category;

    private Boolean isChecked;
    private LocalDateTime checkedAt;

    private ShoppingItemSource source;

    private LocalDateTime createdAt;
}
