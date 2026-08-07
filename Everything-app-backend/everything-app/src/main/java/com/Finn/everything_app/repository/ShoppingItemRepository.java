package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.ShoppingItem;
import com.Finn.everything_app.model.ShoppingItemSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {

    List<ShoppingItem> findByUserIdOrderByCategoryAscSortOrderAscIdAsc(Long userId);

    Optional<ShoppingItem> findByIdAndUserId(Long id, Long userId);

    /** Die Zeilen, die ein Neuaufbau aus dem Wochenplan ersetzen darf. */
    List<ShoppingItem> findByUserIdAndSourceAndIsChecked(
            Long userId, ShoppingItemSource source, Boolean isChecked);

    List<ShoppingItem> findByUserIdAndIsChecked(Long userId, Boolean isChecked);
}
