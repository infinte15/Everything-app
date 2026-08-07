package com.Finn.everything_app.seed.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Löscht sämtliche Daten eines Nutzers, damit {@link DemoDataSeeder} einen frischen Bestand
 * aufbauen kann.
 *
 * <p>Bewusst SQL statt JPA-Kaskaden: die Kaskaden an {@code User} decken nur einen Teil der
 * Tabellen ab (Kurse, Semester, Routinen, Verträge, Einkaufsliste hängen nicht daran), und ein
 * {@code deleteAll} über zwei Dutzend Repositories wäre genau dieselbe Reihenfolge-Frage, nur
 * verteilt über zwanzig Aufrufe. Die Reihenfolge unten ist die einzige inhaltliche Aussage
 * dieser Klasse: Kinder vor Eltern.
 *
 * <p>Nur für den Demo-Bestand gedacht. Der Aufrufer ist der einzige Ort, der entscheidet, wen
 * es trifft — hier steht keine Sicherung, die eine falsche User-ID abfinge.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemoDataReset {

    private final JdbcTemplate jdbc;

    /**
     * Kinder vor Eltern. Die Tabellen ohne eigene {@code user_id} hängen über ihren
     * Fremdschlüssel an einer Tabelle, die weiter unten in der Liste steht.
     */
    private static final String[] STATEMENTS = {
            // --- Lernen ---
            "DELETE FROM flashcard_reviews WHERE user_id = ?",
            "DELETE FROM flashcards WHERE deck_id IN (SELECT id FROM flashcard_decks WHERE user_id = ?)",
            "DELETE FROM flashcard_decks WHERE user_id = ?",
            "DELETE FROM study_goals WHERE user_id = ?",
            "DELETE FROM grades WHERE user_id = ?",
            "DELETE FROM course_schedules WHERE course_id IN (SELECT id FROM courses WHERE user_id = ?)",

            // --- Sport ---
            "DELETE FROM exercise_sets WHERE workout_session_id IN (SELECT id FROM workout_sessions WHERE user_id = ?)",
            "DELETE FROM routine_exercises WHERE routine_id IN (SELECT id FROM routines WHERE user_id = ?)",

            // --- Gewohnheiten ---
            "DELETE FROM habit_completions WHERE habit_id IN (SELECT id FROM habits WHERE user_id = ?)",

            // --- Rezepte ---
            "DELETE FROM recipe_meal_types WHERE recipe_id IN (SELECT id FROM recipes WHERE user_id = ?)",
            "DELETE FROM recipe_ingredients WHERE recipe_id IN (SELECT id FROM recipes WHERE user_id = ?)",
            "DELETE FROM recipe_steps WHERE recipe_id IN (SELECT id FROM recipes WHERE user_id = ?)",
            "DELETE FROM recipe_cook_logs WHERE user_id = ?",
            "DELETE FROM meal_plans WHERE user_id = ?",
            "DELETE FROM shopping_items WHERE user_id = ?",

            // --- Finanzen ---
            "DELETE FROM finance_transactions WHERE user_id = ?",
            "DELETE FROM contracts WHERE user_id = ?",
            "DELETE FROM budget_categories WHERE user_id = ?",
            "DELETE FROM bank_accounts WHERE user_id = ?",
            "DELETE FROM bank_connections WHERE user_id = ?",

            // Kalendereinträge zeigen auf Task, Habit, Workout und Projekt - vor allen vieren weg.
            "DELETE FROM calendar_events WHERE user_id = ?",

            "DELETE FROM workout_sessions WHERE user_id = ?",
            "DELETE FROM routines WHERE user_id = ?",
            "DELETE FROM workout_plans WHERE user_id = ?",
            "DELETE FROM recipes WHERE user_id = ?",
            "DELETE FROM habits WHERE user_id = ?",
            "DELETE FROM tasks WHERE user_id = ?",
            "DELETE FROM projects WHERE user_id = ?",

            // study_notes zeigt auf sich selbst (parent_id); ohne das Lösen der Kante scheitert
            // das DELETE an der eigenen Kindzeile.
            "UPDATE study_notes SET parent_id = NULL WHERE user_id = ?",
            "DELETE FROM study_notes WHERE user_id = ?",

            "DELETE FROM courses WHERE user_id = ?",
            "DELETE FROM semesters WHERE user_id = ?",
    };

    @Transactional
    public void wipe(Long userId) {
        int total = 0;
        for (String sql : STATEMENTS) {
            total += jdbc.update(sql, userId);
        }
        log.info("Demo-Reset: {} Zeilen für User {} gelöscht", total, userId);
    }
}
