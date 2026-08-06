-- Einmalige Handmigration: stabiler Kontoschluessel fuer bank_accounts.
--
-- Nachtrag zu 2026-08-06-finance-bank-import.sql. Die Enable-Banking-Spezifikation stellt klar,
-- dass die account_uid nur gilt, solange die Sitzung autorisiert ist: nach einer erneuten
-- Zustimmung traegt dasselbe Konto einen anderen UID. Eine Eindeutigkeit auf (user_id,
-- account_uid) haette also bei jeder Neu-Autorisierung ein zweites Konto samt kompletter
-- Buchungshistorie entstehen lassen. Stabil ist allein der identification_hash.
--
-- Zwei Aenderungen:
--   1. identification_hash als neue Spalte und neuer Eindeutigkeitsschluessel,
--   2. account_uid verliert NOT NULL (die Spezifikation kennt Konten ohne UID, etwa gesperrte).
--
-- Die Tabelle ist zum Zeitpunkt dieser Migration leer - es gibt noch keinen Bankimport -,
-- deshalb ist kein Backfill noetig und die NOT-NULL-Spalte laesst sich direkt anlegen.
-- Auf einer Datenbank mit Bestandszeilen waere stattdessen erst die Spalte nullable anzulegen,
-- zu befuellen und dann zu verschaerfen.
--
-- REIHENFOLGE: Die Anwendung muss einmal gestartet worden sein (ddl-auto=update legt die neue
-- Spalte an), BEVOR dieses Skript laeuft.
--
-- Ausfuehren mit:
--   psql -h localhost -U postgres -d everything_app -f src/main/resources/db/manual/2026-08-06-bank-account-identification-hash.sql

BEGIN;

-- 1. Alter Eindeutigkeitsschluessel weg. Hibernate hat ihn beim Erstellen der Tabelle angelegt;
--    ddl-auto=update entfernt ihn nie von selbst.
ALTER TABLE bank_accounts DROP CONSTRAINT IF EXISTS uk_bank_accounts_user_uid;
DROP INDEX IF EXISTS uk_bank_accounts_user_uid;

-- 2. account_uid ist nur noch ein Arbeitswert, kein Identitaetsmerkmal.
ALTER TABLE bank_accounts ALTER COLUMN account_uid DROP NOT NULL;

-- 3. Neuer Schluessel. IF NOT EXISTS, weil Hibernate ihn auf einer frischen Datenbank schon
--    aus der @UniqueConstraint-Deklaration erzeugt hat.
CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_accounts_user_hash
  ON bank_accounts (user_id, identification_hash);

COMMIT;
