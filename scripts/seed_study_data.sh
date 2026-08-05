#!/usr/bin/env bash
#
# Füllt den Study Space mit Testdaten, die jeden Bereich wirklich beanspruchen — nicht nur
# "irgendwas ist da", sondern gezielt die Fälle, an denen man etwas sehen kann:
#
#   Semester      ein abgeschlossenes und ein laufendes (letzteres als "aktuell" markiert)
#   Module        vier Stück mit unterschiedlichen ECTS, Farben und Dozenten
#   Noten         mehrere Teilleistungen je Modul, dazu ein Schein (zählt nicht in den Schnitt)
#   Stundenplan   sechs Veranstaltungen über die Woche, eine davon im ALTEN Semester
#                 (die darf im Kalender nichts mehr blockieren)
#   Flashcards    zwei Decks; Karten in allen Zuständen: neu, fällig, in Lernphase, gereift
#   Notizen       ein dreistufiger Seitenbaum mit Markdown plus zwei freistehende Seiten
#
# Voraussetzung: das Backend läuft auf :8080 (./mvnw spring-boot:run).
# Das Skript LEGT NUR AN. Zweimal laufen lassen heißt: alles doppelt.
#
#   bash scripts/seed_study_data.sh
#
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080/api}"

if ! curl -s -o /dev/null --max-time 3 -X POST "$BASE/auth/dev-login"; then
  echo "Backend nicht erreichbar unter $BASE — läuft ./mvnw spring-boot:run?" >&2
  exit 1
fi

TOKEN=$(curl -s -X POST "$BASE/auth/dev-login" | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
AUTH="Authorization: Bearer $TOKEN"
JSON="Content-Type: application/json"

# POST und die vergebene ID zurückgeben; bricht ab, wenn der Server nein sagt.
post_id() {
  local path="$1" body="$2"
  local response
  response=$(curl -s -X POST "$BASE$path" -H "$AUTH" -H "$JSON" -d "$body")
  python3 - "$response" "$path" <<'PY'
import sys, json
response, path = sys.argv[1], sys.argv[2]
try:
    data = json.loads(response)
except json.JSONDecodeError:
    sys.exit(f"Antwort von {path} war kein JSON: {response[:200]}")
if "id" not in data:
    sys.exit(f"{path} hat nichts angelegt: {data.get('message', response)[:200]}")
print(data["id"])
PY
}

post() { curl -s -o /dev/null -X POST "$BASE$1" -H "$AUTH" -H "$JSON" -d "$2"; }
put()  { curl -s -o /dev/null -X PUT  "$BASE$1" -H "$AUTH" -H "$JSON" -d "${2:-\{\}}"; }

TODAY=$(date +%Y-%m-%d)
LAST_TERM_START=$(date -d "$TODAY -12 months" +%Y-%m-%d)
LAST_TERM_END=$(date -d "$TODAY -6 months" +%Y-%m-%d)
TERM_START=$(date -d "$TODAY -2 months" +%Y-%m-%d)
TERM_END=$(date -d "$TODAY +4 months" +%Y-%m-%d)

echo "== Semester =="
OLD_TERM=$(post_id /study/semesters \
  "{\"label\":\"SS 2025\",\"startDate\":\"$LAST_TERM_START\",\"endDate\":\"$LAST_TERM_END\"}")
TERM=$(post_id /study/semesters \
  "{\"label\":\"WS 2025/26\",\"startDate\":\"$TERM_START\",\"endDate\":\"$TERM_END\"}")
put "/study/semesters/$TERM/current"
echo "   SS 2025 (abgeschlossen) = $OLD_TERM, WS 2025/26 (aktuell) = $TERM"

echo "== Module =="
ANA=$(post_id /study/courses "{\"name\":\"Analysis I\",\"ectsCredits\":9,\"instructor\":\"Prof. Meier\",\"color\":\"#FF9F0A\",\"semesterId\":$TERM}")
LA=$(post_id  /study/courses "{\"name\":\"Lineare Algebra\",\"ectsCredits\":6,\"instructor\":\"Prof. Schmidt\",\"color\":\"#64D2FF\",\"semesterId\":$TERM}")
PROG=$(post_id /study/courses "{\"name\":\"Programmierung 2\",\"ectsCredits\":5,\"instructor\":\"Dr. Wagner\",\"color\":\"#30D158\",\"semesterId\":$TERM}")
STAT=$(post_id /study/courses "{\"name\":\"Statistik\",\"ectsCredits\":4,\"instructor\":\"Prof. Klein\",\"color\":\"#C2C1FF\",\"semesterId\":$OLD_TERM}")
echo "   Analysis I=$ANA, Lineare Algebra=$LA, Programmierung 2=$PROG, Statistik=$STAT (altes Semester)"

echo "== Noten =="
# Analysis: zwei gewichtete Teilleistungen -> Modul-Ø 1,85
post /study/grades "{\"examName\":\"Klausur\",\"courseId\":$ANA,\"grade\":2.0,\"weight\":70}"
post /study/grades "{\"examName\":\"Übungsblätter\",\"courseId\":$ANA,\"grade\":1.5,\"weight\":30}"
# Lineare Algebra: eine Note plus ein Schein, der den Schnitt NICHT bewegen darf
post /study/grades "{\"examName\":\"Klausur\",\"courseId\":$LA,\"grade\":1.3,\"weight\":100}"
post /study/grades "{\"examName\":\"Testat\",\"courseId\":$LA,\"grade\":4.0,\"weight\":100,\"countsTowardGrade\":false}"
post /study/grades "{\"examName\":\"Projektabgabe\",\"courseId\":$PROG,\"grade\":2.7,\"weight\":100}"
post /study/grades "{\"examName\":\"Klausur\",\"courseId\":$STAT,\"grade\":3.3,\"weight\":100}"
echo "   6 Teilleistungen, davon 1 Schein (countsTowardGrade=false)"

echo "== Stundenplan =="
post "/study/courses/$ANA/schedules"  '{"dayOfWeek":"MONDAY","startTime":"08:00:00","endTime":"09:30:00","location":"HS 1"}'
post "/study/courses/$ANA/schedules"  '{"dayOfWeek":"WEDNESDAY","startTime":"10:00:00","endTime":"11:30:00","location":"HS 1"}'
post "/study/courses/$LA/schedules"   '{"dayOfWeek":"TUESDAY","startTime":"14:00:00","endTime":"16:00:00","location":"SR 12"}'
post "/study/courses/$LA/schedules"   '{"dayOfWeek":"THURSDAY","startTime":"08:00:00","endTime":"09:30:00","location":"SR 12"}'
post "/study/courses/$PROG/schedules" '{"dayOfWeek":"FRIDAY","startTime":"12:00:00","endTime":"14:00:00","location":"CIP-Pool"}'
# Im abgeschlossenen Semester: taucht im Stundenplan auf, blockiert den Kalender aber nicht mehr.
post "/study/courses/$STAT/schedules" '{"dayOfWeek":"MONDAY","startTime":"16:00:00","endTime":"18:00:00","location":"HS 3"}'
echo "   6 Veranstaltungen, eine davon im abgeschlossenen Semester"

echo "== Flashcards =="
DECK_A=$(post_id /study/decks "{\"name\":\"Analysis Grundlagen\",\"description\":\"Folgen, Reihen, Stetigkeit\",\"courseId\":$ANA}")
DECK_L=$(post_id /study/decks "{\"name\":\"LA Definitionen\",\"description\":\"Vektorräume und Abbildungen\",\"courseId\":$LA}")

card() { post_id /study/flashcards "{\"question\":$(python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$1"),\"answer\":$(python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$2"),\"deckId\":$3}"; }
review() { post "/study/flashcards/$1/review" "{\"rating\":\"$2\"}"; }

C1=$(card "Was ist eine Ableitung?" "Die Steigung der Tangente an einem Punkt" "$DECK_A")
C2=$(card "Was besagt der Mittelwertsatz?" "Es gibt eine Stelle mit der mittleren Steigung" "$DECK_A")
C3=$(card "Wann ist eine Folge konvergent?" "Wenn sie einen Grenzwert besitzt" "$DECK_A")
C4=$(card "Was ist eine Cauchy-Folge?" "Ab einem Index liegen alle Glieder beliebig nah beieinander" "$DECK_A")
C5=$(card "Definition Vektorraum" "Menge mit Addition und Skalarmultiplikation über einem Körper" "$DECK_L")
C6=$(card "Was ist der Kern einer Abbildung?" "Alle Vektoren, die auf den Nullvektor abgebildet werden" "$DECK_L")
C7=$(card "Wann ist eine Matrix invertierbar?" "Wenn ihre Determinante ungleich null ist" "$DECK_L")

# C1 bleibt neu. Die anderen in verschiedene Zustände bringen:
review "$C2" GOOD                                    # 1 Tag  -> morgen fällig
review "$C3" GOOD; review "$C3" GOOD                 # ~3 Tage
for _ in 1 2 3 4 5; do review "$C4" GOOD; done       # >21 Tage -> gilt als gereift
review "$C5" EASY                                    # 4 Tage
review "$C6" GOOD; review "$C6" AGAIN                # vergessen -> in einer Minute wieder fällig
# C7 bleibt neu.
echo "   2 Decks, 7 Karten: neu / fällig / in Lernphase / gereift, dazu 11 Einträge im Review-Protokoll"

echo "== Notizen =="
SKRIPT=$(post_id /study/notes "{\"title\":\"Analysis Skript\",\"icon\":\"📘\",\"courseId\":$ANA,\"content\":\"# Kapitelübersicht\\n\\nDas Skript zur Vorlesung, kapitelweise mitgeschrieben.\\n\\n- Folgen und Grenzwerte\\n- Reihen\\n- Stetigkeit\\n\\n> Klausur am 15.03. — Altklausuren liegen im CIP-Pool.\\n\\n---\\n\\n**Wichtig:** Der Mittelwertsatz kommt jedes Jahr dran.\"}")
KAP1=$(post_id /study/notes "{\"title\":\"Kapitel 1: Folgen\",\"parentId\":$SKRIPT,\"content\":\"## Grenzwerte\\n\\nEine Folge konvergiert, wenn sie einen Grenzwert besitzt.\\n\\n- [x] Definition gelernt\\n- [ ] Übungsblatt 1 rechnen\\n- [ ] Beweise wiederholen\"}")
post_id /study/notes "{\"title\":\"Abschnitt 1.1: Konvergenz\",\"parentId\":$KAP1,\"content\":\"### Epsilon-Kriterium\\n\\nZu jedem eps > 0 gibt es ein N, ab dem alle Glieder naeher als eps am Grenzwert liegen.\"}" > /dev/null
post_id /study/notes "{\"title\":\"Abschnitt 1.2: Cauchy-Folgen\",\"parentId\":$KAP1,\"content\":\"\"}" > /dev/null
post_id /study/notes "{\"title\":\"Kapitel 2: Reihen\",\"parentId\":$SKRIPT,\"content\":\"## Konvergenzkriterien\\n\\n- Majorantenkriterium\\n- Quotientenkriterium\\n- Wurzelkriterium\"}" > /dev/null

LA_NOTE=$(post_id /study/notes "{\"title\":\"LA Zusammenfassung\",\"icon\":\"📐\",\"courseId\":$LA,\"content\":\"# Lineare Algebra\\n\\n> Zwei Wochen vor der Klausur mit den Beweisen anfangen.\"}")
post_id /study/notes "{\"title\":\"Basiswechsel\",\"parentId\":$LA_NOTE,\"content\":\"Transformationsmatrix aufstellen und invertieren.\"}" > /dev/null

post_id /study/notes "{\"title\":\"Semesterplanung\",\"icon\":\"🗓\",\"content\":\"# Was dieses Semester ansteht\\n\\n- [x] Module eintragen\\n- [ ] Lerngruppe finden\\n- [ ] Praktikum bewerben\"}" > /dev/null
post_id /study/notes "{\"title\":\"Ideen\",\"icon\":\"💡\",\"content\":\"Freistehende Seite ohne Modul — landet auf der obersten Ebene.\"}" > /dev/null
echo "   Seitenbaum drei Ebenen tief plus zwei freistehende Seiten"

echo
echo "Fertig. Study Space im Ueberblick:"
for endpoint in semesters courses grades schedules decks flashcards notes; do
  count=$(curl -s "$BASE/study/$endpoint" -H "$AUTH" | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))')
  printf "   %-12s %s\n" "$endpoint" "$count"
done
