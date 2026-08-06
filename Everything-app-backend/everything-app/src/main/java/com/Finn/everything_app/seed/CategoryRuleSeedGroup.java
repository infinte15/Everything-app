package com.Finn.everything_app.seed;

import com.Finn.everything_app.model.RuleMatchField;
import com.Finn.everything_app.model.RuleMatchType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Eine Gruppe von Standardregeln aus {@code data/category-rules.json}.
 *
 * <p>Gruppiert statt flach: eine flache Liste waeren rund 180 Objekte mit jeweils denselben
 * matchField/matchType/priority-Angaben - ueber tausend repetitive Zeilen, in denen ein Tippfehler
 * nicht auffaellt.
 *
 * <p>{@code matchField} und {@code matchType} sind absichtlich enum-typisiert: ein verschriebenes
 * "COUNTERPART" scheitert dann beim Start mit einem Jackson-Fehler und nicht still beim
 * Kategorisieren. Fehlende Felder fuellt der Seeder mit seinen Vorgaben.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryRuleSeedGroup(
        String category,
        String subcategory,
        Integer priority,
        RuleMatchField matchField,
        RuleMatchType matchType,
        List<String> patterns) {
}
