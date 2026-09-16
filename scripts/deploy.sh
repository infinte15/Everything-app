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
# --wait kehrt zurueck, sobald der Healthcheck gruen ist, und endet mit Fehler, wenn er
# es nach 180 s nicht ist. Kein "logs -f": dieses Skript laeuft auch per
#   ssh homeserver 'bash -s' < scripts/deploy.sh
# aus release.sh, und ein Folgen der Logs wuerde dort unbegrenzt warten - rsync und
# gh release kaemen nie dran. 180 s decken start_period (90 s) plus zwei Intervalle ab.
docker compose up -d --build --wait --wait-timeout 180 backend

echo "==> Letzte Logzeilen"
docker compose logs --tail=50 backend

echo
echo "Live mitlesen:  docker compose -f $STACK_DIR/compose.yaml logs -f backend"
