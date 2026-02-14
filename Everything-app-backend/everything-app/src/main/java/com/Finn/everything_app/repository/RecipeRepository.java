package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    // Alle Rezepte
    List<Recipe> findByUserId(Long userId);

    // Rezepte nach Name
    List<Recipe> findByUserIdAndNameContainingIgnoreCase(Long userId, String name);

    // Rezepte nach Kategorie
    List<Recipe> findByUserIdAndCategory(Long userId, String category);

    // Favoriten
    List<Recipe> findByUserIdAndIsFavoriteTrue(Long userId);

    // Rezepte nach Zubereitungszeit
    @Query("SELECT r FROM Recipe r " +
            "WHERE r.user.id = :userId " +
            "ORDER BY (r.prepTimeMinutes + r.cookTimeMinutes) ASC")
    List<Recipe> findByUserIdOrderByTotalTimeAsc(@Param("userId") Long userId);

    // Schnelle Rezepte
    @Query("SELECT r FROM Recipe r " +
            "WHERE r.user.id = :userId " +
            "AND (r.prepTimeMinutes + r.cookTimeMinutes) <= :maxMinutes")
    List<Recipe> findQuickRecipes(
            @Param("userId") Long userId,
            @Param("maxMinutes") Integer maxMinutes
    );

    // Rezepte nach Name erstellt
    List<Recipe> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Alphabetisch sortiert
    List<Recipe> findByUserIdOrderByNameAsc(Long userId);
}