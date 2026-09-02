#!/usr/bin/env bash
# ===========================================================================
# Datenbank-Backup. Laeuft naechtlich per cron und zusaetzlich vor jedem Deploy.
#
#   crontab -e
#   0 3 * * * /srv/everything-app/scripts/ea-backup.sh >> /var/log/ea-backup.log 2>&1
#
# Solange spring.jpa.hibernate.ddl-auto=update aktiv ist, ist das Backup vor dem
# Deploy nicht optional: Hibernate legt Spalten an, loescht nie welche und
# verhaelt sich bei Typaenderungen unvorhersehbar.
# ===========================================================================
set -euo pipefail

STACK_DIR="${STACK_DIR:-/srv/everything-app}"
DEST="${BACKUP_DIR:-/srv/backup/everything-app}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-30}"

# DB_USER kommt aus derselben .env, die auch compose liest.
set -a
# shellcheck disable=SC1091
source "$STACK_DIR/.env"
set +a

mkdir -p "$DEST"
FILE="$DEST/ea-$(date +%F-%H%M).sql.gz"

# In eine temporaere Datei und erst danach umbenennen: ein abgebrochener pg_dump
# hinterlaesst sonst ein halbes Archiv, das wie ein gueltiges Backup aussieht.
docker compose -f "$STACK_DIR/compose.yaml" exec -T db \
    pg_dump -U "$DB_USER" everything_app | gzip > "$FILE.part"
mv "$FILE.part" "$FILE"

find "$DEST" -name 'ea-*.sql.gz' -mtime "+$KEEP_DAYS" -delete

echo "Backup: $FILE ($(du -h "$FILE" | cut -f1))"
