-- Einmalige Handmigration fuer bestehende Entwicklungs-Datenbanken.
--
-- Der Rezept-Space bekommt strukturierte Zutaten und Schritte: statt zweier TEXT-Spalten am
-- Rezept gibt es die Kindtabellen recipe_ingredients und recipe_steps. Drei Dinge kann
-- spring.jpa.hibernate.ddl-auto=update dabei nicht, und genau die stehen hier:
--
--  1. Bestehende Zeilen in die Kindtabellen uebernehmen. Hibernate legt leere Tabellen an,
--     mehr nicht.
--  2. NOT NULL von recipes.ingredients und recipes.instructions nehmen. ddl-auto=update
--     lockert eine bestehende Bedingung nie - ohne diesen Schritt scheitert jedes Insert,
--     sobald der Klartext-Spiegel entfaellt.
--  3. Die CHECK-Bedingung auf meal_plans.meal_type an das neue Enum anpassen. Dasselbe
--     Problem wie in 2026-08-05-project-enum-constraints.sql.
--
-- Beim Uebernehmen werden eckige Klammern abgestreift: der alte RecipeMapper schrieb
-- String.valueOf(Collections.singletonList(text)) in die Spalte, sodass dort
-- "[200 g Mehl\n1 Ei]" stand - mit einer Klammer an der ersten und der letzten Zutat.
--
-- Die Zeilen landen unparsed in name und raw_text. Der Zutaten-Parser kommt in der naechsten
-- Phase; bis dahin sieht ein altes Rezept aus wie vorher, statt dass geraten wird.
--
-- WICHTIG: Die Anwendung muss einmal mit der neuen Jar gestartet worden sein, BEVOR dieses
-- Skript laeuft - sonst existieren die Kindtabellen noch nicht.
--
-- Frisch angelegte Datenbanken brauchen das Skript nicht.
--
-- Ausfuehren mit:
--   psql -h localhost -U postgres -d everything_app -f src/main/resources/db/manual/2026-08-07-recipe-structured-content.sql

BEGIN;

-- 1. Zutaten uebernehmen (nur, wenn noch nichts drinsteht - das Skript bleibt wiederholbar)
INSERT INTO recipe_ingredients (recipe_id, position, name, raw_text)
SELECT r.id, t.ord - 1, btrim(t.line), btrim(t.line)
FROM recipes r
CROSS JOIN LATERAL unnest(string_to_array(btrim(coalesce(r.ingredients, ''), '[]'), E'\n'))
     WITH ORDINALITY AS t(line, ord)
WHERE btrim(t.line) <> ''
  AND NOT EXISTS (SELECT 1 FROM recipe_ingredients ri WHERE ri.recipe_id = r.id);

INSERT INTO recipe_steps (recipe_id, position, text)
SELECT r.id, t.ord - 1, btrim(t.line)
FROM recipes r
CROSS JOIN LATERAL unnest(string_to_array(btrim(coalesce(r.instructions, ''), '[]'), E'\n'))
     WITH ORDINALITY AS t(line, ord)
WHERE btrim(t.line) <> ''
  AND NOT EXISTS (SELECT 1 FROM recipe_steps rs WHERE rs.recipe_id = r.id);

-- 2. Altspalten duerfen leer sein
ALTER TABLE recipes ALTER COLUMN ingredients DROP NOT NULL;
ALTER TABLE recipes ALTER COLUMN instructions DROP NOT NULL;

-- 3. Mahlzeitentyp auf das Enum normalisieren.
--    Bisher stand dort je nach Aufrufer FRÜHSTÜCK, BREAKFAST oder Breakfast.
UPDATE meal_plans SET meal_type = 'FRUEHSTUECK'
  WHERE upper(meal_type) IN ('FRÜHSTÜCK', 'FRUEHSTUECK', 'BREAKFAST');
UPDATE meal_plans SET meal_type = 'MITTAGESSEN'
  WHERE upper(meal_type) IN ('MITTAGESSEN', 'LUNCH');
UPDATE meal_plans SET meal_type = 'ABENDESSEN'
  WHERE upper(meal_type) IN ('ABENDESSEN', 'DINNER', 'SUPPER');
UPDATE meal_plans SET meal_type = 'SNACK'
  WHERE meal_type NOT IN ('FRUEHSTUECK', 'MITTAGESSEN', 'ABENDESSEN');

ALTER TABLE meal_plans DROP CONSTRAINT IF EXISTS meal_plans_meal_type_check;
ALTER TABLE meal_plans ADD CONSTRAINT meal_plans_meal_type_check
  CHECK (meal_type IN ('FRUEHSTUECK', 'MITTAGESSEN', 'ABENDESSEN', 'SNACK'));

-- 4. Vorbelegung der Mahlzeiten-Eignung fuer Bestandsrezepte.
--    Ohne sie findet die Wochenplanung nach dem Umstieg gar nichts.
INSERT INTO recipe_meal_types (recipe_id, meal_type)
SELECT r.id, m.meal_type
FROM recipes r
CROSS JOIN LATERAL (
  SELECT CASE
    WHEN r.category = 'Frühstück' THEN ARRAY['FRUEHSTUECK']
    WHEN r.category IN ('Backen', 'Dessert', 'Vorspeise & Snack', 'Getränk') THEN ARRAY['SNACK']
    ELSE ARRAY['MITTAGESSEN', 'ABENDESSEN']
  END AS types
) c
CROSS JOIN LATERAL unnest(c.types) AS m(meal_type)
WHERE NOT EXISTS (SELECT 1 FROM recipe_meal_types t WHERE t.recipe_id = r.id);

-- 5. Indizes, die Hibernate an bestehenden Tabellen nicht nachtraegt
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_name ON recipe_ingredients (lower(name));
CREATE INDEX IF NOT EXISTS idx_recipes_user_category ON recipes (user_id, category);
CREATE INDEX IF NOT EXISTS idx_recipes_user_last_cooked ON recipes (user_id, last_cooked_at);

COMMIT;
