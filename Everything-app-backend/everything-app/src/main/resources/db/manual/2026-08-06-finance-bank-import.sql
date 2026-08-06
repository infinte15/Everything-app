-- Einmalige Handmigration fuer bestehende Entwicklungs-Datenbanken (Bankimport, Phase 1).
--
-- Die vier neuen Tabellen (bank_connections, bank_accounts, contracts, category_rules) legt
-- Hibernate mit spring.jpa.hibernate.ddl-auto=update korrekt an - inklusive CHECK-Bedingungen
-- und Indizes, weil sie neu sind. Betroffen ist ausschliesslich die bereits bestehende Tabelle
-- finance_transactions: dort ergaenzt update zwar die neuen Spalten und Fremdschluessel, aber
-- niemals
--
--   * die CHECK-Bedingung fuer die neue @Enumerated(STRING)-Spalte "source" - Hibernate friert
--     solche Bedingungen beim ERSTELLEN der Tabelle ein und fasst sie danach nie wieder an
--     (vgl. 2026-08-05-project-enum-constraints.sql),
--   * die UNIQUE-Bedingung auf (user_id, external_id), die den Dedup des Bankimports absichert,
--   * die in @Table(indexes = ...) deklarierten Indizes.
--
-- Ausserdem koennen die neuen Spalten nicht als NOT NULL angelegt werden, solange die Tabelle
-- Zeilen enthaelt: ddl-auto=update setzt ein nacktes ALTER TABLE ... ADD COLUMN ... NOT NULL ohne
-- Default ab, das daran scheitert - und reisst dabei den restlichen Schema-Update mit. Die
-- Spalten sind im Entity deshalb nullable und werden hier einmalig nachgezogen.
--
-- REIHENFOLGE: Die Anwendung muss einmal gestartet worden sein (ddl-auto=update legt Spalten und
-- neue Tabellen an), BEVOR dieses Skript laeuft. Sonst scheitert Punkt 2 an einer Spalte, die es
-- noch nicht gibt.
--
-- Frisch angelegte Datenbanken sind nicht betroffen; dort erzeugt Hibernate alles direkt mit.
--
-- Ausfuehren mit:
--   psql -h localhost -U postgres -d everything_app -f src/main/resources/db/manual/2026-08-06-finance-bank-import.sql

BEGIN;

-- 1. Bestandszeilen sind per Definition manuelle Buchungen mit frei gewaehlter Kategorie.
UPDATE finance_transactions SET source = 'MANUAL'       WHERE source IS NULL;
UPDATE finance_transactions SET category_locked = FALSE WHERE category_locked IS NULL;

-- 2. CHECK-Bedingung fuer die neue Enum-Spalte, damit die Alt-Datenbank denselben Schutz hat
--    wie eine frisch erzeugte.
ALTER TABLE finance_transactions DROP CONSTRAINT IF EXISTS finance_transactions_source_check;
ALTER TABLE finance_transactions ADD CONSTRAINT finance_transactions_source_check
  CHECK (source IN ('MANUAL', 'BANK'));

-- 3. Dedup-Schluessel des Bankimports. Partiell, weil manuelle Buchungen external_id = NULL
--    tragen und davon beliebig viele existieren duerfen.
CREATE UNIQUE INDEX IF NOT EXISTS uk_finance_tx_user_external
  ON finance_transactions (user_id, external_id)
  WHERE external_id IS NOT NULL;

-- 4. Indizes, die Hibernate auf einer bereits bestehenden Tabelle nicht mehr nachtraegt.
CREATE INDEX IF NOT EXISTS idx_finance_tx_user_date
  ON finance_transactions (user_id, transaction_date);
CREATE INDEX IF NOT EXISTS idx_finance_tx_bank_account
  ON finance_transactions (bank_account_id);
CREATE INDEX IF NOT EXISTS idx_finance_tx_contract
  ON finance_transactions (contract_id);

-- 5. Eindeutigkeit der ausgelieferten Standardregeln. Ueber JPA nicht ausdrueckbar: ein
--    gewoehnliches UNIQUE(user_id, pattern, ...) greift hier nicht, weil PostgreSQL NULL-Werte
--    in UNIQUE-Bedingungen als verschieden behandelt - jede globale Regel hat aber
--    user_id IS NULL. Deshalb ein partieller, funktionaler Index.
CREATE UNIQUE INDEX IF NOT EXISTS uk_category_rules_global_pattern
  ON category_rules (lower(pattern), match_field, match_type)
  WHERE user_id IS NULL;

COMMIT;
