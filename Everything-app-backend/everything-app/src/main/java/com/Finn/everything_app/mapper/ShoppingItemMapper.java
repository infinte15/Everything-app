package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.ShoppingItemDTO;
import com.Finn.everything_app.model.ShoppingItem;
import org.springframework.stereotype.Component;

@Component
public class ShoppingItemMapper {

    public ShoppingItemDTO toDTO(ShoppingItem item) {
        if (item == null) return null;

        ShoppingItemDTO dto = new ShoppingItemDTO();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setAmount(item.getAmount());
        dto.setUnit(item.getUnit());
        dto.setCategory(item.getCategory());
        dto.setIsChecked(item.getIsChecked());
        dto.setCheckedAt(item.getCheckedAt());
        dto.setSource(item.getSource());
        dto.setCreatedAt(item.getCreatedAt());

        return dto;
    }
}
