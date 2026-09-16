#!/usr/bin/env bash
# ===========================================================================
# Datenbank-Backup. Laeuft naechtlich per cron und zusaetzlich vor jedem Deploy.
#
#   crontab -e
#   0 3 * * * /srv/everything-app/scripts/ea-backup.sh >> /var/log/ea-backup.log 2>&1
#
# Zwei Voraussetzungen, ohne die der Cron-Lauf stumm scheitert:
#   sudo usermod -aG docker "$USER"        # docker compose braucht den Socket
#   sudo touch /var/log/ea-backup.log && sudo chown "$USER": /var/log/ea-backup.log
# Bei der Logdatei scheitert sonst die Shell an der Umleitung, BEVOR sie das Skript
# startet - es gibt dann weder Meldung noch Backup.
#
# Solange spring.jpa.hibernate.ddl-auto=update aktiv ist, ist das Backup vor dem
# Deploy nicht optional: Hibernate legt Spalten an, loescht nie welche und
# verhaelt sich bei Typaenderungen unvorhersehbar.
# ===========================================================================
set -euo pipefail

STACK_DIR="${STACK_DIR:-/srv/everything-app}"

# DB_USER kommt aus derselben .env, die auch compose liest.
set -a
# shellcheck disable=SC1091
source "$STACK_DIR/.env"
set +a

# Erst NACH dem source: BACKUP_MOUNT und BACKUP_KEEP_DAYS duerfen aus der .env kommen.
BACKUP_MOUNT="${BACKUP_MOUNT:-/mnt/backup}"
DEST="${BACKUP_DIR:-$BACKUP_MOUNT/everything-app}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-30}"

# Das Backup gehoert auf die externe Platte, nicht auf die Systemplatte - dort liegt
# schon das pgdata-Volume, und ein Plattenausfall naehme sonst Datenbank UND Backup mit.
# Ist die Platte nicht eingehaengt, ist der Mountpoint ein leeres Verzeichnis auf der
# Systemplatte, und mkdir -p wuerde den Pfad dort still anlegen.
if ! mountpoint -q "$BACKUP_MOUNT"; then
    echo "FEHLER: $BACKUP_MOUNT ist nicht gemountet - Backup abgebrochen" >&2
    exit 1
fi

mkdir -p "$DEST"
if [[ ! -w "$DEST" ]]; then
    echo "FEHLER: $DEST nicht beschreibbar" >&2
    exit 1
fi

FILE="$DEST/ea-$(date +%F-%H%M).sql.gz"

# In eine temporaere Datei und erst danach umbenennen: ein abgebrochener pg_dump
# hinterlaesst sonst ein halbes Archiv, das wie ein gueltiges Backup aussieht.
docker compose -f "$STACK_DIR/compose.yaml" exec -T db \
    pg_dump -U "$DB_USER" everything_app | gzip > "$FILE.part"
mv "$FILE.part" "$FILE"

find "$DEST" -name 'ea-*.sql.gz' -mtime "+$KEEP_DAYS" -delete

echo "Backup: $FILE ($(du -h "$FILE" | cut -f1))"
