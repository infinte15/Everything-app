package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Exercise;
import com.Finn.everything_app.model.MuscleGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    List<Exercise> findByCreatedById(Long userId);
    List<Exercise> findByDifficulty(String difficulty);

    Optional<Exercise> findByExternalId(String externalId);

    /** Alle Katalog-Zeilen einer Herkunft, z.B. zum Umstellen auf einen neuen Datensatz. */
    List<Exercise> findBySource(String source);

    /** Nur die Schluessel laden - der Seeder braucht pro Start genau diese eine Abfrage. */
    @Query("select e.externalId from Exercise e where e.externalId is not null")
    Set<String> findAllExternalIds();

    /**
     * Katalog-Suche. Die vorletzte Bedingung ist wichtig: ohne sie liefert der Katalog auch
     * die eigenen Uebungen anderer User aus.
     *
     * <p>{@code byProfile} schaltet den Ausruestungsfilter zu. Der Schalter ist noetig, weil
     * ein {@code in ()} mit leerer Liste kein gueltiges SQL ist - ohne aktives Profil wird
     * {@code equipmentIn} deshalb nie ausgewertet.
     */
    @Query("""
            select distinct e from Exercise e
            left join e.primaryMuscles pm
            where (:search is null or lower(e.name) like lower(concat('%', cast(:search as string), '%')))
              and (:muscle is null or pm = :muscle)
              and (:equipment is null or e.equipment = :equipment)
              and (:category is null or e.category = :category)
              and (:difficulty is null or e.difficulty = :difficulty)
              and (:byProfile = false or e.equipment in :equipmentIn)
              and (e.createdBy is null or e.createdBy.id = :userId)
            """)
    Page<Exercise> search(@Param("search") String search,
                          @Param("muscle") MuscleGroup muscle,
                          @Param("equipment") String equipment,
                          @Param("category") String category,
                          @Param("difficulty") String difficulty,
                          @Param("byProfile") boolean byProfile,
                          @Param("equipmentIn") Collection<String> equipmentIn,
                          @Param("userId") Long userId,
                          Pageable pageable);

    @Query("select distinct e.equipment from Exercise e where e.equipment is not null order by e.equipment")
    List<String> findDistinctEquipment();

    @Query("select distinct e.category from Exercise e where e.category is not null order by e.category")
    List<String> findDistinctCategories();

    @Query("select distinct e.difficulty from Exercise e where e.difficulty is not null order by e.difficulty")
    List<String> findDistinctDifficulties();
}
