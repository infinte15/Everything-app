#!/usr/bin/env bash
# ===========================================================================
# Backend-Update auf dem Server.
#
#   ssh homeserver 'bash -s' < scripts/deploy.sh
#   oder direkt auf dem Server: /srv/everything-app/scripts/deploy.sh
#
# Postgres, Caddy und cloudflared laufen durch; nur das Backend wird neu gebaut
# und getauscht. Kurze Downtime, waehrend Spring hochfaehrt.
# ===========================================================================
set -euo pipefail

STACK_DIR="${STACK_DIR:-/srv/everything-app}"
cd "$STACK_DIR"

echo "==> Backup vor dem Deploy"
./scripts/ea-backup.sh

echo "==> git pull"
git pull --ff-only

echo "==> Backend neu bauen und tauschen"
docker compose up -d --build backend

echo "==> Logs (Strg-C beendet nur das Mitlesen, nicht den Container)"
docker compose logs -f --tail=50 backend
