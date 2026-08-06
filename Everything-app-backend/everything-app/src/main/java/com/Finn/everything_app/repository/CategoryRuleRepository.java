package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.CategoryRule;
import com.Finn.everything_app.model.RuleMatchField;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    /**
     * Alle Regeln, die fuer diesen Nutzer gelten - eigene und ausgelieferte - in Anwendungsreihenfolge.
     * Der Kategorisierer nimmt den ersten Treffer.
     *
     * <p>Die Sortierung ist in dieser Reihenfolge zu lesen:
     * <ol>
     *   <li>Eine eigene Regel schlaegt jede globale Regel, <em>unabhaengig von priority</em>. Das ist
     *       der einzige Override-Mechanismus (globale Regeln sind nicht loeschbar, vgl.
     *       {@link CategoryRule}), und eine ausgelieferte Regel mit hoher Prioritaet darf ihn nicht
     *       aushebeln. Mit LAZY-Beziehung uebersetzt sich {@code r.user is null} zu
     *       {@code user_id is null} - ohne Join.</li>
     *   <li>Innerhalb der Gruppe gewinnt die hoehere priority.</li>
     *   <li>id absteigend als deterministischer Gleichstand; die neuere gelernte Regel ist dann
     *       der bessere Tipp.</li>
     * </ol>
     *
     * <p>{@code active = true} steht hier und nicht beim Aufrufer, damit eine abgeschaltete Regel
     * ueber keinen Codepfad durchrutschen kann.
     */
    @Query("""
            SELECT r FROM CategoryRule r
            WHERE r.active = true
              AND (r.user IS NULL OR r.user.id = :userId)
            ORDER BY CASE WHEN r.user IS NULL THEN 1 ELSE 0 END ASC,
                     r.priority DESC,
                     r.id DESC
            """)
    List<CategoryRule> findApplicableRules(@Param("userId") Long userId);

    // Eigene Regeln des Nutzers fuer die Oberflaeche
    List<CategoryRule> findByUserIdOrderByPriorityDescIdDesc(Long userId);

    Optional<CategoryRule> findByIdAndUserId(Long id, Long userId);

    // Beim Lernen: vorhandene Regel aktualisieren statt eine zweite anzulegen
    Optional<CategoryRule> findByUserIdAndPatternAndMatchField(Long userId, String pattern, RuleMatchField matchField);

    /** Nur die Schluessel - der Seeder braucht genau diese eine Abfrage fuer seine Idempotenz. */
    @Query("SELECT r.pattern FROM CategoryRule r WHERE r.user IS NULL")
    Set<String> findGlobalPatterns();
}
