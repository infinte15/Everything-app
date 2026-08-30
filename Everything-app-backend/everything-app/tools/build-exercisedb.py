#!/usr/bin/env python3
"""Erzeugt src/main/resources/data/exercisedb.json aus dem Upstream-Datensatz.

Quelle: https://github.com/hasaneyldrm/exercises-dataset (data/exercises.json, ~17 MB).
Die Metadaten dort stehen unter der MIT-Lizenz und duerfen mitgeliefert werden.

Die Medien (images/*.jpg, videos/*.gif) sind (c) Gym visual und werden bewusst
NICHT heruntergeladen: der Seeder setzt nur URLs auf den jsDelivr-Spiegel, die
App laedt sie zur Laufzeit. Siehe NOTICE.md des Upstream-Repos.

Aufruf:  python3 tools/build-exercisedb.py
"""
import json
import sys
import urllib.request
from pathlib import Path

SOURCE_URL = (
    "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/data/exercises.json"
)
# Nur Englisch: die App zeigt englische Uebungsnamen und Anleitungen, die
# uebrigen neun Sprachen des Datensatzes waeren totes Gewicht im Jar.
LANG = "en"

# Felder, die der Seeder braucht. Alles andere faellt weg.
#
# "muscle_group" ist bewusst nicht dabei: der Datensatz beschreibt es als
# "primary synergist muscle group", der Wert steht aber in allen 1324
# Datensaetzen ohnehin schon in "secondary_muscles". Doppelt gefuehrte
# Wahrheit waere nur eine weitere Stelle, die auseinanderlaufen kann.
KEEP = ("id", "name", "body_part", "equipment", "target",
        "secondary_muscles", "media_id", "image", "gif_url")

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/exercisedb.json"

# Vier Namen im Upstream-Datensatz haben kaputtes Encoding: das Gradzeichen wurde einmal als
# cp1251 fehlgedeutet ("°" -> "В°") und danach kleingeschrieben. Zwei weitere Namen im selben
# Datensatz tragen das Zeichen korrekt, es ist also ein Fehler dort, keiner dieser Pipeline.
# Bewusst nur diese eine bekannte Sequenz - generisches Reparieren von Mojibake raet.
MOJIBAKE = {"\u0432\u00b0": "\u00b0"}


def fix_encoding(text: str) -> str:
    for broken, fixed in MOJIBAKE.items():
        text = text.replace(broken, fixed)
    return text


def main() -> int:
    print(f"Lade {SOURCE_URL} …", file=sys.stderr)
    with urllib.request.urlopen(SOURCE_URL) as response:
        entries = json.load(response)
    print(f"  {len(entries)} Datensaetze gelesen", file=sys.stderr)

    trimmed = []
    for entry in entries:
        out = {key: entry[key] for key in KEEP if key in entry}
        out["name"] = fix_encoding(out["name"])
        steps = (entry.get("instruction_steps") or {}).get(LANG) or []
        out["instructions"] = [fix_encoding(step) for step in steps]
        trimmed.append(out)

    # Stabile Reihenfolge, damit ein Refresh einen lesbaren Diff erzeugt.
    trimmed.sort(key=lambda e: e["id"])

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps(trimmed, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    size_kb = OUT.stat().st_size / 1024
    print(f"✓ {OUT.relative_to(OUT.parents[4])}: {len(trimmed)} Uebungen, {size_kb:.0f} KB",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
