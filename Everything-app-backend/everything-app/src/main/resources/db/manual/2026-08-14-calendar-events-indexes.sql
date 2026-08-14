-- Indizes fuer calendar_events.
--
-- Die Tabelle hatte bis hierher KEINEN einzigen Index ausser dem Primaerschluessel. Sie waechst
-- pro Nutzer und Scheduler-Lauf um mehrere hundert Zeilen, und jeder Lauf liest sie mehrfach:
-- fixe Termine, gepinnte Bloecke, der bisherige Plan als Stabilitaetsanker, dazu das Aufraeumen.
-- Jede dieser Abfragen war ein Seq Scan ueber den gesamten Bestand.
--
-- ddl-auto=update legt Indizes auf einer BESTEHENDEN Tabelle nicht nachtraeglich an — die
-- @Index-Angaben an der Entity greifen nur bei einer frisch erzeugten Tabelle. Deshalb dieses
-- Skript; es ist gefahrlos wiederholbar (IF NOT EXISTS) und aendert keine Daten.
--
-- Reihenfolge wie sonst auch: Anwendung einmal starten, dann dieses Skript laufen lassen.
--
-- Ausfuehren mit:
--   psql -h localhost -U postgres -d everything_app -f src/main/resources/db/manual/2026-08-14-calendar-events-indexes.sql

BEGIN;

-- Der Monatsabruf des Kalenders und das Aufraeumfenster des Schedulers.
CREATE INDEX IF NOT EXISTS idx_calendar_events_user_start
    ON calendar_events (user_id, start_time);

-- Die Scheduler-Abfragen: generierte Bloecke eines Typs in einem Zeitraum.
CREATE INDEX IF NOT EXISTS idx_calendar_events_user_type_fixed_start
    ON calendar_events (user_id, event_type, is_fixed, start_time);

-- Die related-Kanten. Gebraucht beim Loeschen einer Quell-Entitaet (dort werden die
-- uebrig bleibenden Bloecke vorher weggeraeumt, sonst kippt die Foreign-Key-Constraint).
CREATE INDEX IF NOT EXISTS idx_calendar_events_related_task
    ON calendar_events (related_task_id);
CREATE INDEX IF NOT EXISTS idx_calendar_events_related_habit
    ON calendar_events (related_habit_id);
CREATE INDEX IF NOT EXISTS idx_calendar_events_related_workout
    ON calendar_events (related_workout_id);
CREATE INDEX IF NOT EXISTS idx_calendar_events_related_project
    ON calendar_events (related_project_id);

COMMIT;
