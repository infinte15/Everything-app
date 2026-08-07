package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.RecipeCookLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecipeCookLogRepository extends JpaRepository<RecipeCookLog, Long> {

    List<RecipeCookLog> findByRecipeIdAndUserIdOrderByCookedAtDesc(Long recipeId, Long userId);

    Optional<RecipeCookLog> findByIdAndUserId(Long id, Long userId);

    long countByRecipeIdAndUserId(Long recipeId, Long userId);
}
