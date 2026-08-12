package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.dto.RecipeDTO;
import com.Finn.everything_app.dto.RecipeImportPreviewDTO;
import com.Finn.everything_app.dto.RecipeIngredientDTO;
import com.Finn.everything_app.dto.RecipeStepDTO;
import com.Finn.everything_app.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest ein Rezept aus eingefuegtem Text - typisch eine Instagram-Bildunterschrift.
 *
 * <p><b>Warum eingefuegt und nicht nur abgerufen.</b> Der {@link InstagramImporter} versucht
 * durchaus, sich die Bildunterschrift selbst zu holen - aber Instagram zeigt einem nicht
 * angemeldeten Abrufer meist eine Anmeldeseite statt des Texts, und die oEmbed-Schnittstelle
 * liefert seit Jahren nur Einbettungs-HTML ohne Text und verlangt dafuer ein Facebook-App-Token.
 * Der Abruf ist deshalb die Abkuerzung und dieser Weg hier der tragende: eingefuegter Text
 * kostet einen Handgriff und geht immer.
 *
 * <p>Dieselbe Klasse ist ausserdem die letzte Stufe des Adress-Imports - fuer Seiten, die ihr
 * Rezept weder als {@code ld+json} noch als Microdata herausgeben, liest der
 * {@link RecipeUrlImporter} den sichtbaren Seitentext und schickt ihn hier durch.
 *
 * <p>Aufgebaut wie {@link RecipeJsonLdParser}: keine Netzzugriffe, keine Datenbank, ein
 * {@link RecipeImportPreviewDTO} mit Warnungen als Ergebnis. Was nicht sicher zu erkennen ist,
 * wird nicht geraten, sondern als Warnung benannt - der Nutzer korrigiert es in der Vorschau.
 *
 * <p>Bildunterschriften sind kein Format. Erkannt werden die zwei Bauformen, die tatsaechlich
 * vorkommen: mit Abschnittsueberschriften ("Zutaten:" / "Zubereitung:") und ohne, wo eine
 * Aufzaehlung mitten im Text die Zutaten traegt.
 */
@Component
@RequiredArgsConstructor
public class TextRecipeImporter {

    /** Mehr Zeilen hat keine Bildunterschrift. Deckel gegen absichtlich grosse Eingaben. */
    static final int MAX_LINES = 500;
    static final int MAX_INGREDIENTS = 100;
    static final int MAX_STEPS = 100;

    /** Ab hier ist ein einzelner Schritt so lang, dass er offensichtlich ein Absatz war. */
    private static final int LONG_STEP_CHARS = 300;

    /** Eine Zutatenzeile ist kurz. Alles Laengere ist ein Satz. */
    private static final int INGREDIENT_LINE_MAX = 60;

    private static final Pattern HASHTAG = Pattern.compile("#(\\p{L}[\\p{L}\\d_]*)");

    /**
     * Fuehrende Aufzaehlungszeichen, auch Emoji - Bildunterschriften nutzen 🥕 wie einen Strich.
     *
     * <p>Alles vor dem ersten Buchstaben oder der ersten Zahl faellt weg, statt eine Liste von
     * Zeichen aufzuzaehlen. Der Grund ist {@code 👩‍🍳}: ein Emoji ist nicht ein Zeichen, sondern
     * eine Folge aus Symbolen, Verbindern (U+200D) und Variantenselektoren. Eine Zeichenklasse
     * aus {@code \p{So}} trifft davon nur das erste Glied und laesst den Rest stehen - aus
     * "👩‍🍳 Zubereitung" wird dann "‍🍳 Zubereitung", und die Ueberschrift wird nicht erkannt.
     *
     * <p>{@code \p{N}} statt {@code \p{Nd}}: Bruchzeichen wie ½ sind Zahlen der Kategorie No und
     * muessen am Zeilenanfang stehen bleiben, sonst verliert "½ TL Salz" seine Menge.
     */
    private static final Pattern LEADING_BULLET = Pattern.compile("^[^\\p{L}\\p{N}]+");

    /** Fuehrende Nummerierung eines Schritts - die Nummer setzt die Oberflaeche selbst. */
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*\\d+[.)]\\s*");

    private static final Pattern SERVINGS = Pattern.compile(
            "(?:f(?:ü|ue)r\\s+)?(\\d{1,2})\\s*(?:personen|portionen|port\\.|stück|stueck)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MINUTES = Pattern.compile(
            "(\\d{1,3})\\s*(?:min\\b|minuten)", Pattern.CASE_INSENSITIVE);

    private static final Pattern HOURS = Pattern.compile(
            "(\\d{1,2})\\s*(?:std\\b|stunden|stunde|h\\b)", Pattern.CASE_INSENSITIVE);

    /** Beginnt mit einer Ziffer oder einem Bruchzeichen - dann ist es wohl eine Menge. */
    private static final Pattern LEADING_QUANTITY = Pattern.compile("^[\\d½⅓⅔¼¾⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞]");

    private static final Set<String> INGREDIENT_HEADS = Set.of(
            "zutaten", "du brauchst", "das brauchst du", "was du brauchst", "ihr braucht",
            "das braucht ihr", "einkaufsliste", "ingredients");

    private static final Set<String> STEP_HEADS = Set.of(
            "zubereitung", "anleitung", "so geht's", "so gehts", "so wird's gemacht",
            "schritt für schritt", "los geht's", "und so geht's", "instructions", "method",
            "zubereitungsschritte");

    private static final Set<String> NOTE_HEADS = Set.of(
            "tipp", "tipps", "hinweis", "hinweise", "notiz", "notizen", "anmerkung");

    private final IngredientParser ingredientParser;

    /** In welchem Abschnitt eine Zeile steht. */
    enum Section { HEAD, INGREDIENTS, STEPS, NOTES }

    public RecipeImportPreviewDTO importFrom(String text, String sourceName, String sourceUrl) {
        List<String> lines = cleanLines(text);
        if (lines.isEmpty()) {
            throw new BadRequestException(
                    "Aus diesem Text ließ sich kein Rezept lesen. Steht dort wirklich ein Rezept?");
        }

        List<String> warnings = new ArrayList<>();
        RecipeDTO dto = new RecipeDTO();

        String tags = extractHashtags(lines);
        // Hashtags koennen ganze Zeilen gewesen sein.
        lines.removeIf(String::isBlank);
        if (lines.isEmpty()) {
            throw new BadRequestException(
                    "Aus diesem Text ließ sich kein Rezept lesen. Steht dort wirklich ein Rezept?");
        }
        dto.setTags(tags);

        Split split = split(lines, warnings);

        // ── Titel und Beschreibung ────────────────────────────────────────────────────────
        String origin = sourceName == null || sourceName.isBlank() ? "Instagram" : sourceName.trim();
        String title = split.head.isEmpty() ? null : stripLeadingSymbols(split.head.get(0));
        if (title == null || title.isBlank()) {
            dto.setName("Rezept von " + origin);
            warnings.add("Kein Titel gefunden.");
        } else {
            dto.setName(trimTo(title, 200));
        }
        if (split.head.size() > 1) {
            dto.setDescription(trimTo(String.join("\n", split.head.subList(1, split.head.size())), 2000));
        }

        // ── Portionen und Zeit ────────────────────────────────────────────────────────────
        String whole = String.join("\n", lines);
        Integer servings = readServings(whole);
        if (servings == null) {
            // Vier wie in RecipeJsonLdParser.readYield - zwei Importer, eine Vorgabe.
            dto.setServings(4);
            warnings.add("Keine Portionsangabe gefunden - 4 Portionen angenommen.");
        } else {
            dto.setServings(servings);
        }

        int minutes = readMinutes(whole);
        // Alles in die Zubereitungszeit: eine Bildunterschrift nennt eine Gesamtzeit, und sie
        // auf Vorbereiten und Kochen aufzuteilen waere geraten.
        dto.setPrepTimeMinutes(minutes);
        dto.setCookTimeMinutes(0);
        if (minutes == 0) {
            warnings.add("Keine Zeitangabe gefunden - bitte selbst eintragen.");
        } else {
            warnings.add("Nur eine Gesamtzeit gefunden - sie steht unter Zubereitungszeit.");
        }

        // ── Kategorie und Schwierigkeit ───────────────────────────────────────────────────
        // Nicht aus den Hashtags geraten: "#pasta" steht unter genug Desserts, und eine falsch
        // einsortierte Kategorie merkt niemand, bis der Filter Luecken hat.
        dto.setCategory("Sonstiges");
        warnings.add("Kategorie nicht erkennbar - bitte auswählen.");
        dto.setDifficulty("Mittel");

        // ── Zutaten und Schritte ──────────────────────────────────────────────────────────
        dto.setIngredients(readIngredients(split.ingredients));
        if (dto.getIngredients().isEmpty()) {
            warnings.add("Keine Zutaten gefunden - bitte selbst ergänzen.");
        }

        dto.setSteps(readSteps(split.steps));
        if (dto.getSteps().isEmpty()) {
            warnings.add("Keine Zubereitung gefunden - bitte selbst ergänzen.");
        } else if (dto.getSteps().size() == 1
                && dto.getSteps().get(0).getText().length() > LONG_STEP_CHARS) {
            warnings.add("Die Zubereitung stand in einem Stück - sie ist ein einziger Schritt.");
        }

        if (!split.notes.isEmpty()) {
            dto.setNotes(String.join("\n", split.notes));
        }

        long unparsed = dto.getIngredients().stream()
                .filter(i -> i.getAmount() == null && i.getUnit() == null)
                .count();
        if (unparsed == dto.getIngredients().size() && unparsed > 2) {
            warnings.add("Bei den Zutaten war keine Menge erkennbar - bitte nachsehen.");
        }

        dto.setSourceName(trimTo(origin, 100));
        dto.setSourceUrl(validUrl(sourceUrl));

        return new RecipeImportPreviewDTO(dto, warnings);
    }

    // ── Zerlegen ──────────────────────────────────────────────────────────────────────────

    /** Die vier Bloecke eines Rezepttextes. */
    private record Split(List<String> head, List<String> ingredients, List<String> steps,
                         List<String> notes) {
    }

    /**
     * Teilt die Zeilen in Kopf, Zutaten, Schritte und Notiz.
     *
     * <p>Erst ueber die Ueberschriften. Fehlen sie, uebernimmt {@link #splitByRun} - dort steckt
     * die eigentliche Annahme ueber Bildunterschriften.
     */
    private Split split(List<String> lines, List<String> warnings) {
        List<String> head = new ArrayList<>();
        List<String> ingredients = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        Section current = Section.HEAD;
        boolean sawHeading = false;

        for (String line : lines) {
            Section heading = sectionOf(line);
            if (heading != null) {
                current = heading;
                sawHeading = true;
                continue;
            }
            switch (current) {
                case INGREDIENTS -> ingredients.add(line);
                case STEPS -> steps.add(line);
                case NOTES -> notes.add(line);
                case HEAD -> head.add(line);
            }
        }

        if (sawHeading && !(ingredients.isEmpty() && steps.isEmpty())) {
            return new Split(head, ingredients, steps, notes);
        }
        return splitByRun(lines, notes, warnings);
    }

    /**
     * Rueckfallebene ohne Ueberschriften.
     *
     * <p>Annahme: die erste Zeile ist der Titel, und irgendwo darunter steht ein
     * zusammenhaengender Lauf kurzer Aufzaehlungszeilen - das sind die Zutaten. Alles danach ist
     * Zubereitung. Findet sich kein solcher Lauf, wird nichts erfunden: dann ist alles
     * Zubereitung, und die Warnung sagt es.
     */
    private Split splitByRun(List<String> lines, List<String> notes, List<String> warnings) {
        List<String> head = new ArrayList<>(List.of(lines.get(0)));
        List<String> body = lines.subList(1, lines.size());

        int start = -1;
        int end = -1;
        for (int i = 0; i < body.size(); i++) {
            if (looksLikeIngredient(body.get(i))) {
                if (start < 0) {
                    start = i;
                }
                end = i;
            } else if (start >= 0) {
                break;
            }
        }

        if (start < 0) {
            warnings.add("Zutaten und Zubereitung waren nicht zu trennen - alles steht unter Zubereitung.");
            return new Split(head, List.of(), new ArrayList<>(body), notes);
        }

        head.addAll(body.subList(0, start));
        List<String> ingredients = new ArrayList<>(body.subList(start, end + 1));
        List<String> steps = new ArrayList<>(body.subList(end + 1, body.size()));
        return new Split(head, ingredients, steps, notes);
    }

    /**
     * Zeilen saeubern: trimmen, Zierlinien wegwerfen, deckeln.
     *
     * <p>Weg muss alles ohne einen einzigen Buchstaben oder eine Ziffer - {@code •••},
     * {@code ———}, eine Reihe Emoji. Solche Zeilen sind Absatzschmuck und wuerden sonst als
     * Titel oder als Zutat durchgehen.
     */
    static List<String> cleanLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.codePoints().anyMatch(Character::isLetterOrDigit)) {
                continue;
            }
            lines.add(trimmed);
            if (lines.size() >= MAX_LINES) {
                break;
            }
        }
        return lines;
    }

    /**
     * Erkennt eine Abschnittsueberschrift.
     *
     * <p>Vor dem Vergleich fallen fuehrende Emoji ({@code 🛒 Zutaten}) und ein abschliessender
     * Doppelpunkt oder Strich weg, ebenso ein Klammerzusatz wie {@code Zutaten (für 2)}.
     * Zurueck kommt {@code null}, wenn die Zeile keine Ueberschrift ist.
     */
    static Section sectionOf(String line) {
        String candidate = stripLeadingSymbols(line)
                .replaceAll("\\s*[:\\-–—]\\s*$", "")
                .trim()
                .toLowerCase(Locale.GERMAN);
        // "Zutaten für 2 Personen" ist dieselbe Ueberschrift wie "Zutaten".
        String withoutSuffix = candidate.replaceAll("\\s*[(\\[].*$", "")
                .replaceAll("\\s+f(?:ü|ue)r\\s+.*$", "")
                .trim();

        if (candidate.length() > 40) {
            return null;
        }
        if (INGREDIENT_HEADS.contains(candidate) || INGREDIENT_HEADS.contains(withoutSuffix)) {
            return Section.INGREDIENTS;
        }
        if (STEP_HEADS.contains(candidate) || STEP_HEADS.contains(withoutSuffix)) {
            return Section.STEPS;
        }
        if (NOTE_HEADS.contains(candidate) || NOTE_HEADS.contains(withoutSuffix)) {
            return Section.NOTES;
        }
        return null;
    }

    /**
     * Sammelt Hashtags ein und entfernt sie aus den Zeilen.
     *
     * <p>Sie stehen meistens als Block am Ende, manchmal aber auch mitten im letzten Schritt.
     * Bleiben sie stehen, endet die Zubereitung mit {@code #foodporn}.
     */
    static String extractHashtags(List<String> lines) {
        Set<String> tags = new LinkedHashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = HASHTAG.matcher(lines.get(i));
            StringBuilder cleaned = new StringBuilder();
            boolean found = false;
            while (matcher.find()) {
                found = true;
                tags.add(matcher.group(1).toLowerCase(Locale.GERMAN));
                matcher.appendReplacement(cleaned, "");
            }
            if (found) {
                matcher.appendTail(cleaned);
                lines.set(i, cleaned.toString().replaceAll("\\s{2,}", " ").trim());
            }
        }
        if (tags.isEmpty()) {
            return null;
        }
        return trimTo(String.join(",", tags), 200);
    }

    /** Portionen aus dem ganzen Text. {@code null}, wenn nichts Brauchbares dasteht. */
    static Integer readServings(String text) {
        Matcher matcher = SERVINGS.matcher(text);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value > 0 && value <= 100) {
                return value;
            }
        }
        return null;
    }

    /** Summe aller gefundenen Zeitangaben in Minuten. 0, wenn keine dasteht. */
    static int readMinutes(String text) {
        int total = 0;
        Matcher hours = HOURS.matcher(text);
        if (hours.find()) {
            total += Integer.parseInt(hours.group(1)) * 60;
        }
        Matcher minutes = MINUTES.matcher(text);
        if (minutes.find()) {
            total += Integer.parseInt(minutes.group(1));
        }
        return Math.min(total, 24 * 60);
    }

    /**
     * Sieht die Zeile wie eine Zutat aus?
     *
     * <p>Drei Merkmale zusammen: ein Aufzaehlungszeichen oder eine fuehrende Menge, kurz, und
     * kein Satzzeichen am Ende. Jedes fuer sich waere zu grob - "2. Nudeln kochen." faengt mit
     * einer Ziffer an und ist trotzdem ein Schritt.
     */
    static boolean looksLikeIngredient(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.length() > INGREDIENT_LINE_MAX) {
            return false;
        }
        if (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")) {
            return false;
        }
        if (LEADING_NUMBER.matcher(trimmed).find()) {
            return false;
        }
        String stripped = stripBullet(trimmed);
        return !stripped.equals(trimmed) || LEADING_QUANTITY.matcher(stripped).find();
    }

    /** Entfernt ein fuehrendes Aufzaehlungszeichen, auch ein Emoji. */
    static String stripBullet(String line) {
        return LEADING_BULLET.matcher(line).replaceFirst("").trim();
    }

    private List<RecipeIngredientDTO> readIngredients(List<String> lines) {
        List<RecipeIngredientDTO> result = new ArrayList<>();
        for (String line : lines) {
            String cleaned = stripBullet(line);
            if (cleaned.isBlank()) {
                continue;
            }
            ParsedIngredient parsed = ingredientParser.parse(cleaned);
            if (parsed.name() == null || parsed.name().isBlank()) {
                continue;
            }
            result.add(new RecipeIngredientDTO(
                    null,
                    parsed.amount(),
                    parsed.unit(),
                    trimTo(parsed.name(), 200),
                    trimTo(parsed.note(), 200),
                    trimTo(parsed.rawText(), 300),
                    null));
            if (result.size() >= MAX_INGREDIENTS) {
                break;
            }
        }
        return result;
    }

    private List<RecipeStepDTO> readSteps(List<String> lines) {
        List<RecipeStepDTO> result = new ArrayList<>();
        for (String line : lines) {
            String cleaned = LEADING_NUMBER.matcher(stripBullet(line)).replaceFirst("").trim();
            if (cleaned.length() < 3) {
                continue;
            }
            result.add(new RecipeStepDTO(null, cleaned));
            if (result.size() >= MAX_STEPS) {
                break;
            }
        }
        return result;
    }

    // ── Kleinkram ─────────────────────────────────────────────────────────────────────────

    /** Fuehrende Emoji, Aufzaehlungszeichen und Leerraum weg. */
    private static String stripLeadingSymbols(String line) {
        return stripBullet(line);
    }

    /** Uebernimmt die Adresse nur, wenn sie eine ist - gespeichert wird sie ungeprueft weiter. */
    private String validUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }
            return trimTo(url, 500);
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
