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

# Ohne key.properties faellt build.gradle.kts auf den DEBUG-Schluessel zurueck und warnt
# nur - eine Zeile, die in der Build-Ausgabe untergeht. Eine so signierte v1.0.0 laesst
# sich spaeter nicht durch ein echt signiertes Update ersetzen: Android verweigert den
# Signaturwechsel, es hilft nur Deinstallieren auf jedem Geraet.
if [[ ! -f "$FRONTEND/android/key.properties" ]]; then
    echo "FEHLER: android/key.properties fehlt - Release wuerde mit Debug-Key signiert" >&2
    exit 1
fi

# Gleicher versionCode = Android verweigert die Installation. Lieber hier abbrechen als
# nach dem vollstaendigen Build auf dem Geraet.
PUB_VERSION=$(grep '^version:' "$FRONTEND/pubspec.yaml" | awk '{print $2}')
if [[ "v${PUB_VERSION%%+*}" != "$VERSION" ]]; then
    echo "FEHLER: pubspec.yaml hat $PUB_VERSION, Release ist $VERSION - erst hochzaehlen" >&2
    exit 1
fi

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

# Der Linux-Build wird verwendet: das Bundle wandert nach /opt/everything-app auf den
# Linux-PC. Als Release-Asset muss man es nicht von Hand kopieren.
echo "==> Linux-Bundle packen"
tar -czf "$FRONTEND/build/everything-app-linux-$VERSION.tar.gz" \
    -C "$FRONTEND/build/linux/x64/release" bundle

echo "==> GitHub Release mit APK und Linux-Bundle"
gh release create "$VERSION" \
    "$FRONTEND/build/app/outputs/flutter-apk/app-release.apk" \
    "$FRONTEND/build/everything-app-linux-$VERSION.tar.gz" \
    --title "$VERSION" --generate-notes

echo
echo "Fertig. Windows-Build laeuft nicht mit - Flutter Desktop kann nicht"
echo "cross-compilen, der muss auf dem Windows-Rechner selbst gebaut werden:"
echo "  flutter build windows --release --dart-define=API_BASE_URL=$API"
