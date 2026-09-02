# Deployment — Ablauf auf dem Homeserver

Setzt den Plan aus `everything-app-deployment-guide-v2.md` um. Alles, was ohne
laufenden Server vorbereitbar war, ist im Repo erledigt; hier steht nur noch,
was **auf dem Server** bzw. **im Cloudflare-Dashboard** zu tun ist.

Reihenfolge einhalten. Insbesondere: **der Tunnel-Ingress für die App-Subdomain
kommt zuletzt** — solange gebaut wird, soll der Hostname nicht auflösen.

---

## 0. Voraussetzungen auf dem Server

```bash
sudo mkdir -p /srv/everything-app && sudo chown "$USER" /srv/everything-app
git clone <repo-url> /srv/everything-app
cd /srv/everything-app
```

Docker mit Compose-Plugin muss installiert sein.

---

## 1. Secrets anlegen

```bash
cp .env.example .env
chmod 600 .env

openssl rand -base64 48   # → JWT_SECRET
openssl rand -base64 24   # → DB_PASSWORD
```

Beides in die `.env` eintragen, dazu `APP_DOMAIN` und den `CF_TUNNEL_TOKEN`
(Zero Trust → Networks → Tunnels).

> Die alten Dev-Zugangsdaten aus `application-secrets.properties` waren **nie**
> in der Git-Historie — geprüft mit `git grep` über alle Revisionen. Sie müssen
> nicht rotiert werden, gehören aber trotzdem nicht auf den Server.

---

## 2. Erster Start ohne Tunnel

```bash
docker compose up -d db backend caddy
docker compose logs -f backend        # bis "Everything App successfully started"

curl -i http://localhost:8081/api/auth/login
# erwartet: 400 oder 401 — NICHT 404
```

Gegenprobe, dass die Absicherung greift:

```bash
curl -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8081/api/auth/dev-login   # 403
curl -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8081/api/auth/register    # 403
curl -o /dev/null -w '%{http_code}\n'         http://localhost:8081/api/tasks            # 403
```

---

## 3. Eigenen Account anlegen

```bash
sed -i 's/^APP_REGISTRATION_ENABLED=.*/APP_REGISTRATION_ENABLED=true/' .env
docker compose up -d backend

curl -X POST http://localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"finn","email":"...","password":"<langes Zufallspasswort aus Vaultwarden>"}'

sed -i 's/^APP_REGISTRATION_ENABLED=.*/APP_REGISTRATION_ENABLED=false/' .env
docker compose up -d backend
curl -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8081/api/auth/register   # wieder 403
```

---

## 4. Backup einrichten — vor allem Weiteren

```bash
crontab -e
# 0 3 * * * /srv/everything-app/scripts/ea-backup.sh >> /var/log/ea-backup.log 2>&1
```

**Und einen Restore testen.** Ein Backup, das nie zurückgespielt wurde, ist eine
Vermutung:

```bash
./scripts/ea-backup.sh
docker compose exec db createdb -U "$DB_USER" restore_test
gunzip -c /srv/backup/everything-app/ea-*.sql.gz | \
  docker compose exec -T db psql -U "$DB_USER" -d restore_test
docker compose exec db dropdb -U "$DB_USER" restore_test
```

---

## 5. Client im LAN testen — noch ohne Domain

Erst wenn App und Backend nachweislich miteinander können, kommen WARP, Access
und der Domain-Build dazu. Sonst sucht man den Fehler an drei Stellen gleichzeitig.

In `compose.yaml` den Caddy-Port vorübergehend auf `"8081:80"` öffnen (statt
`127.0.0.1:8081:80`), dann auf dem Entwicklungsrechner:

```bash
cd Everything-app-frontend/everything_app
flutter build apk --release --dart-define=API_BASE_URL=http://192.168.x.x:8081/api
```

Danach die Portbindung wieder auf `127.0.0.1` zurücksetzen.

---

## 6. Zero Trust, WARP und Access

Reines Dashboard-Thema, siehe Kapitel 5 des Plans. Kurzfassung:

1. **Device enrollment policy**: Settings → WARP Client → Include: eigene
   Mailadresse. Session duration lang setzen (6 Monate oder unbegrenzt), sonst
   ist auf jedem Gerät regelmäßig eine Neubestätigung fällig.
2. **WARP** auf Handy, Tablet, Windows-PC, Linux-PC — je einmal Mailcode.
3. **Access-Application** auf `app.deine-domain.de`:
   | Feld | Wert |
   |------|------|
   | Action | `Allow` |
   | Include | `Emails` → eigene Adresse |
   | **Require** | **`Gateway`** |

   `Require: Gateway` ist der entscheidende Teil — er macht aus „meine
   Mailadresse reicht" ein „meine Mailadresse **und** ein eingebuchtes Gerät".
4. **Bypass-Policy** für `/api/finance/bank/callback` (Action `Bypass`,
   Include `Everyone`). Der Browser kommt aus dem Bank-Login ohne JWT zurück.
5. **Rate Limiting Rule** auf `/api/auth/login`: Block bei >10 Requests/Minute
   pro IP. Der gleichnamige Filter im Backend greift zusätzlich, siehe
   `LoginRateLimitFilter`.
6. WAF Managed Rules, Bot Fight Mode, optional Geo-Blocking auf DE/EU.

---

## 7. Tunnel scharfschalten — jetzt erst

```yaml
ingress:
  - hostname: app.deine-domain.de
    service: http://caddy:80
  - service: http_status:404
```

```bash
docker compose up -d cloudflared
```

Der Tunnel zeigt auf **Caddy**, nicht direkt auf Spring. Caddy entscheidet, was
`/api` ist und was Web-App.

---

## 8. Clients final bauen

```bash
./scripts/release.sh v1.0.0
```

Das Skript testet, baut APK/Linux/Web gegen `https://$APP_DOMAIN/api`, rollt das
Backend per SSH aus, rsynct den Web-Build und legt ein GitHub-Release mit der APK
an. Windows läuft nicht mit — Flutter Desktop kann nicht cross-compilen:

```bash
flutter build windows --release --dart-define=API_BASE_URL=https://app.deine-domain.de/api
```

Vor jedem Release die Version in `pubspec.yaml` hochzählen. Die Zahl hinter dem
Plus ist der `versionCode`; bleibt sie gleich, verweigert Android die Installation.

Auf Handy und Tablet **Obtainium** auf das Repo zeigen lassen (bei privatem Repo
mit Personal Access Token), dann kommen Updates als Benachrichtigung.

---

## 9. Was ins Backup gehört, außer der Datenbank

Diese vier ändern sich fast nie und werden deshalb gern vergessen — einmal als
Anhang in Vaultwarden:

- `/srv/everything-app/.env`
- `~/.everything-app/upload-keystore.jks` und `android/key.properties`
- `~/.everything-app/enablebanking.pem`
- die Cloudflare-Tunnel-Credentials

---

## 10. Offene Punkte

| Punkt | Stand |
|-------|-------|
| Objekt-Autorisierung (Kap. 10.2) | **23 Endpunkte prüfen den Besitzer nicht** — siehe unten |
| `tokenVersion` für Token-Widerruf | offen, im Plan als „Später" eingeordnet |
| Flyway statt `ddl-auto=update` | offen; bis dahin schützt das Backup vor jedem Deploy |
| Biometrie (`local_auth`) | offen |
| Settings-Screen für die API-URL zur Laufzeit | offen; bislang ein Build pro Umgebung |
| `state`-Parameter im Bank-Callback (Kap. 10.1) | **geprüft, in Ordnung** |

### Zu 10.1 — Bank-Callback

`BankSyncService.completeAuthorization` setzt `authState` nach erfolgreichem
Callback auf `null` (einmalig verwendbar) und lehnt Aufrufe ab, deren
`BankConnection.createdAt` älter als `STATE_VALIDITY_MINUTES` ist. Beides ist
vorhanden, der Endpunkt ist also so abgesichert wie beabsichtigt.

### Zu 10.2 — Objekt-Autorisierung

Die Services haben durchgängig ein `getXById(Long id)` **ohne** `userId`, und die
zugehörigen Controller-Methoden nehmen kein `@CurrentUser` entgegen. Betroffen
sind 23 Endpunkte, darunter schreibende und löschende auf Finanzdaten:

- `FinanceController` — `getTransactionById`, `updateTransaction`,
  `deleteTransaction`, `getBudgetById`, `updateBudget`, `deleteBudget`
- `TaskController` — `getTaskById`, `updateTask`, `completeTask`, `deleteTask`
- `HabitController` — `updateHabit`, `completeHabit`, `uncompleteHabit`,
  `getHabitProgress`
- `SportsController` — `updatePlan`, `deletePlan`, `getSessionsByPlan`,
  `updateSession`, `completeSession`, `deleteSession`, `getExerciseById`,
  `updateSet`, `deleteSet`

`ProjectService` zeigt das richtige Muster bereits vor: es gibt dort
`getProjectById(id, userId)` mit `findByIdAndUserId`.

**Aktuell nicht ausnutzbar**, weil es genau einen Account gibt und
`app.registration.enabled=false` steht. Es wird in dem Moment ausnutzbar, in dem
ein zweiter Account existiert. Vor jeder Öffnung der Registrierung also zwingend
nachziehen.
