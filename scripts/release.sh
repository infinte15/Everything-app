#!/usr/bin/env bash
# ===========================================================================
# Release: testen, bauen, ausrollen.
#
#   ./scripts/release.sh v1.2.0
#
# Vorher in pubspec.yaml die Version hochzaehlen - die Zahl hinter dem Plus ist
# der versionCode, und ohne Erhoehung verweigert Android die Installation.
#
# Reihenfolge mit Absicht: Backend zuerst, Clients danach. Andersherum reden neue
# Clients mit einem alten Backend und bekommen 404 auf Endpunkte, die es noch
# nicht gibt.
# ===========================================================================
set -euo pipefail

VERSION="${1:?Version fehlt, z.B. ./scripts/release.sh v1.2.0}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SSH_HOST="${SSH_HOST:-homeserver}"
STACK_DIR="${STACK_DIR:-/srv/everything-app}"

# APP_DOMAIN aus der lokalen .env, damit Server und Clients dieselbe Adresse nutzen.
if [[ -f .env ]]; then
    set -a; # shellcheck disable=SC1091
    source .env; set +a
fi
: "${APP_DOMAIN:?APP_DOMAIN nicht gesetzt - in .env eintragen (siehe .env.example)}"
API="https://$APP_DOMAIN/api"

BACKEND="$ROOT/Everything-app-backend/everything-app"
FRONTEND="$ROOT/Everything-app-frontend/everything_app"

echo "==> Tests"
(cd "$BACKEND"  && ./mvnw -q test)
(cd "$FRONTEND" && flutter analyze && flutter test)

echo "==> Clients bauen gegen $API"
cd "$FRONTEND"
flutter build apk   --release --dart-define=API_BASE_URL="$API"
flutter build linux --release --dart-define=API_BASE_URL="$API"
# Web braucht kein --dart-define: kIsWeb schaltet auf den relativen Pfad /api um.
flutter build web   --release
cd "$ROOT"

echo "==> Backend ausrollen"
ssh "$SSH_HOST" "bash -s" < scripts/deploy.sh

echo "==> Web-Build auf den Server"
rsync -av --delete "$FRONTEND/build/web/" "$SSH_HOST:$STACK_DIR/deploy/web/"

echo "==> GitHub Release mit APK"
gh release create "$VERSION" \
    "$FRONTEND/build/app/outputs/flutter-apk/app-release.apk" \
    --title "$VERSION" --generate-notes

echo
echo "Fertig. Windows-Build laeuft nicht mit - Flutter Desktop kann nicht"
echo "cross-compilen, der muss auf dem Windows-Rechner selbst gebaut werden:"
echo "  flutter build windows --release --dart-define=API_BASE_URL=$API"
