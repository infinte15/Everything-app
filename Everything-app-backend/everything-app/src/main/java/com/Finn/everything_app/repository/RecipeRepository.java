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

    // Rezepte nach Tag
    @Query("SELECT r FROM Recipe r JOIN r.tags t " +
            "WHERE r.user.id = :userId AND t = :tag")
    List<Recipe> findByUserIdAndTag(
            @Param("userId") Long userId,
            @Param("tag") String tag
    );

    List<Recipe> findByUserIdAndCategory(Long userId, String category);

    // Rezepte nach Zubereitungszeit
    @Query("SELECT r FROM Recipe r " +
            "WHERE r.user.id = :userId " +
            "ORDER BY (r.prepTimeMinutes + r.cookTimeMinutes) ASC")
    List<Recipe> findByUserIdOrderByTotalTimeAsc(@Param("userId") Long userId);

    List<Recipe> findByUserIdAndIsFavoriteTrue(Long userId);

    // Rezepte nach Rating
    List<Recipe> findByUserIdOrderByRatingDesc(Long userId);

    // Rezepte mit Bild
    @Query("SELECT r FROM Recipe r " +
            "WHERE r.user.id = :userId " +
            "AND r.imageUrl IS NOT NULL")
    List<Recipe> findRecipesWithImages(@Param("userId") Long userId);

    // Schnelle Rezepte
    @Query("SELECT r FROM Recipe r " +
            "WHERE r.user.id = :userId " +
            "AND (r.prepTimeMinutes + r.cookTimeMinutes) <= :maxMinutes")
    List<Recipe> findQuickRecipes(
            @Param("userId") Long userId,
            @Param("maxMinutes") Integer maxMinutes
    );

    List<Recipe> findByUserIdAndNameContaining(Long userId, String query);

    List<Recipe> findByUserIdAndPrepTimeMinutesLessThanEqual(Long userId, Integer maxTime);

    List<Recipe> findByUserIdAndDifficulty(Long userId, String difficulty);

    List<Recipe> findByUserIdAndCaloriesLessThanEqual(Long userId, Integer maxCalories);

    // Rezepte nach Portionen
    List<Recipe> findByUserIdAndServings(Long userId, Integer servings);

    // Alphabetisch sortiert
    List<Recipe> findByUserIdOrderByNameAsc(Long userId);
}