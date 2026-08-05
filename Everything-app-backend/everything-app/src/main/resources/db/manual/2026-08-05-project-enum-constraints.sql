-- Einmalige Handmigration fuer bestehende Entwicklungs-Datenbanken.
--
-- Hibernate legt fuer @Enumerated(STRING)-Spalten eine CHECK-Bedingung mit genau den
-- Enum-Werten an, die beim ERSTELLEN der Tabelle existierten. Mit
-- spring.jpa.hibernate.ddl-auto=update wird diese Bedingung spaeter nie wieder angefasst -
-- neue Enum-Werte fehlen also dauerhaft und jedes Insert/Update damit scheitert mit
-- "violates check constraint".
--
-- Zwei Bedingungen sind betroffen:
--
--  1. projects_status_check kennt ACTIVE und CANCELLED nicht. Erst jetzt faellt das auf:
--     ProjectService.updateProjectStatistics setzt den Status automatisch auf ACTIVE, sobald
--     die erste Aufgabe erledigt ist - vorher war die Methode toter Code.
--
--  2. calendar_events_event_type_check kennt PROJECT nicht. Ohne diese Migration kann der
--     Scheduler keinen einzigen Projektblock speichern.
--
-- Frisch angelegte Datenbanken sind nicht betroffen; dort erzeugt Hibernate die Bedingungen
-- direkt mit allen aktuellen Werten.
--
-- Ausfuehren mit:
--   psql -h localhost -U postgres -d everything_app -f src/main/resources/db/manual/2026-08-05-project-enum-constraints.sql

BEGIN;

ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_status_check;
ALTER TABLE projects ADD CONSTRAINT projects_status_check
  CHECK (status IN ('PLANNING', 'IN_PROGRESS', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED'));

ALTER TABLE calendar_events DROP CONSTRAINT IF EXISTS calendar_events_event_type_check;
ALTER TABLE calendar_events ADD CONSTRAINT calendar_events_event_type_check
  CHECK (event_type IN ('TASK', 'HABIT', 'WORKOUT', 'PROJECT', 'CLASS', 'FIXED', 'STUDY', 'OTHER'));

COMMIT;
