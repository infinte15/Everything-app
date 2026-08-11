package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.MealType;
import com.Finn.everything_app.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /**
     * Ein Rezept, aber nur das eigene.
     *
     * <p>Alle Einzelzugriffe laufen hierueber. Vorher gab es nur {@code findById}, und damit
     * konnte jeder angemeldete Nutzer fremde Rezepte lesen, aendern und loeschen, wenn er die
     * Id kannte.
     */
    Optional<Recipe> findByIdAndUserId(Long id, Long userId);

    // Alle Rezepte
    List<Recipe> findByUserId(Long userId);

    /**
     * Freitextsuche ueber Name, Tags und Zutaten.
     *
     * <p>Die Zutaten gehoeren dazu, weil das Suchfeld im Frontend "Rezepte, Zutaten suchen"
     * heisst - und weil die haeufigste Frage an ein eigenes Kochbuch lautet, was sich aus dem
     * machen laesst, was noch da ist. Eine Suche nur ueber den Namen findet "Feta" in keinem
     * Rezept, das nicht Feta heisst.
     *
     * <p>{@code DISTINCT} ist noetig: ohne es kommt ein Rezept mit drei passenden Zutaten
     * dreimal zurueck.
     */
    @Query("SELECT DISTINCT r FROM Recipe r LEFT JOIN r.ingredientList i " +
            "WHERE r.user.id = :userId AND (" +
            " LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            " LOWER(COALESCE(r.tags, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            " LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY r.name ASC")
    List<Recipe> search(@Param("userId") Long userId, @Param("query") String query);

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
            "AND (r.prepTimeMinutes + r.cookTimeMinutes) <= :maxMinutes " +
            "ORDER BY (r.prepTimeMinutes + r.cookTimeMinutes) ASC")
    List<Recipe> findQuickRecipes(
            @Param("userId") Long userId,
            @Param("maxMinutes") Integer maxMinutes
    );

    // Rezepte nach Name erstellt
    List<Recipe> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Alphabetisch sortiert
    List<Recipe> findByUserIdOrderByNameAsc(Long userId);

    // ── Entdecken ─────────────────────────────────────────────────────────────────────────

    /** Zuletzt gekocht - was noch nie gekocht wurde, gehoert nicht in diese Reihe. */
    List<Recipe> findByUserIdAndLastCookedAtIsNotNullOrderByLastCookedAtDesc(Long userId);

    /**
     * Lange nicht gekocht.
     *
     * <p>{@code cookCount > 0} ist die entscheidende Bedingung: ohne sie wird daraus
     * "nie gekocht", und die Reihe listet das halbe Kochbuch.
     */
    @Query("SELECT r FROM Recipe r " +
            "WHERE r.user.id = :userId " +
            "AND r.cookCount > 0 " +
            "AND r.lastCookedAt < :before " +
            "ORDER BY r.lastCookedAt ASC")
    List<Recipe> findNotCookedSince(
            @Param("userId") Long userId,
            @Param("before") LocalDateTime before
    );

    /** Nie ausprobiert. */
    List<Recipe> findByUserIdAndCookCountOrderByCreatedAtDesc(Long userId, Integer cookCount);

    /** Die eigenen besten. */
    @Query("SELECT r FROM Recipe r " +
            "WHERE r.user.id = :userId AND r.rating >= :minRating " +
            "ORDER BY r.rating DESC, r.cookCount DESC")
    List<Recipe> findBestRated(
            @Param("userId") Long userId,
            @Param("minRating") Short minRating
    );

    /** Kandidaten fuer die Wochenplanung: passend zur Mahlzeit, lange nicht Gekochtes zuerst. */
    @Query("SELECT r FROM Recipe r JOIN r.suitableFor m " +
            "WHERE r.user.id = :userId AND m = :mealType " +
            "ORDER BY r.lastCookedAt ASC NULLS FIRST, r.id ASC")
    List<Recipe> findSuitableFor(
            @Param("userId") Long userId,
            @Param("mealType") MealType mealType
    );
}
