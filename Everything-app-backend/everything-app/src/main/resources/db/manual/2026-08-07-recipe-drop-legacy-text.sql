-- Einmalige Handmigration, Teil zwei. Laeuft NACH
-- 2026-08-07-recipe-structured-content.sql und nachdem geprueft wurde, dass die Rezepte in
-- recipe_ingredients und recipe_steps vollstaendig angekommen sind.
--
-- recipes.ingredients und recipes.instructions werden seit Phase 1 nur noch gespiegelt
-- beschrieben und nirgends mehr gelesen. Ab Phase 3 schreibt sie auch niemand mehr. Sie
-- stehenzulassen waere die naechste Falle: ddl-auto=update entfernt keine Spalte, und in ein
-- paar Monaten sieht ein NOT-NULL-loses TEXT-Feld namens "ingredients" aus wie die Quelle
-- der Wahrheit.
--
-- Vor dem Ausfuehren zur Sicherheit pruefen, dass nichts verloren geht:
--
--   SELECT r.id, r.name
--   FROM recipes r
--   WHERE coalesce(btrim(r.ingredients), '') <> ''
--     AND NOT EXISTS (SELECT 1 FROM recipe_ingredients ri WHERE ri.recipe_id = r.id);
--
-- Die Abfrage muss leer sein. Liefert sie Zeilen, erst
-- 2026-08-07-recipe-structured-content.sql nachholen.
--
-- Ausfuehren mit:
--   psql -h localhost -U postgres -d everything_app -f src/main/resources/db/manual/2026-08-07-recipe-drop-legacy-text.sql

BEGIN;

ALTER TABLE recipes DROP COLUMN IF EXISTS ingredients;
ALTER TABLE recipes DROP COLUMN IF EXISTS instructions;

COMMIT;
