-- Einmalige Handmigration fuer bestehende Entwicklungs-Datenbanken.
--
-- Die Tabelle "exercises" traegt noch Spalten aus einem viel frueheren Schema, in dem eine
-- Uebung direkt an einer Trainingseinheit hing und Saetze/Wiederholungen/Gewicht selbst
-- speicherte. Diese Felder leben laengst in "exercise_sets"; im Entity Exercise ist keine
-- davon noch abgebildet.
--
-- Weil spring.jpa.hibernate.ddl-auto=update niemals Spalten entfernt, sind sie stehen
-- geblieben - inklusive workout_session_id NOT NULL. Diese eine Bedingung macht jedes
-- Insert in den Uebungs-Katalog unmoeglich (der Seeder scheitert mit
-- "null value in column workout_session_id violates not-null constraint").
--
-- Frisch angelegte Datenbanken sind nicht betroffen; dort erzeugt Hibernate die Tabelle
-- direkt korrekt. Dieses Skript ist nur fuer Umgebungen noetig, die es schon vorher gab.
--
-- Ausfuehren mit:
--   psql -h localhost -U postgres -d everything_app -f src/main/resources/db/manual/2026-08-03-exercises-legacy-columns.sql

BEGIN;

ALTER TABLE exercises
  DROP COLUMN IF EXISTS workout_session_id,
  DROP COLUMN IF EXISTS completed,
  DROP COLUMN IF EXISTS duration_seconds,
  DROP COLUMN IF EXISTS notes,
  DROP COLUMN IF EXISTS reps,
  DROP COLUMN IF EXISTS sets,
  DROP COLUMN IF EXISTS weight;

COMMIT;

-- Indizes, die Hibernate auf bereits bestehenden Tabellen nicht mehr nachtraegt.
-- Alle Statistik-Abfragen des Gym-Bereichs haengen daran.
CREATE INDEX IF NOT EXISTS idx_sets_session ON exercise_sets (workout_session_id);
CREATE INDEX IF NOT EXISTS idx_sets_exercise ON exercise_sets (exercise_id);
CREATE INDEX IF NOT EXISTS idx_ws_user_start ON workout_sessions (user_id, start_time);
