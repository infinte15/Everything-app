package com.Finn.everything_app.service.recipe;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zerlegt eine Zutatenzeile in Menge, Einheit und Name.
 *
 * <p>Grammatik: {@code [Menge] [Einheit] Name [, Zusatz] [(Anmerkung)]}, alles ausser dem Namen
 * ist optional. Die Einheit muss im geschlossenen Vokabular stehen (siehe
 * {@link UnitVocabulary}) - sonst gehoert das Wort zum Namen.
 *
 * <p>Die Methode wirft nie. Was sie nicht zerlegen kann, gibt sie als Zutat ohne Menge und ohne
 * Einheit zurueck, mit der ganzen Zeile als Namen. Das ist genau das Verhalten von vorher, als
 * die Zutaten noch ein Textblock waren - schlechter kann ein Rezept durch den Parser also nicht
 * werden.
 *
 * <p>Er liegt im Backend, weil der chefkoch-Import genau diese Zeilen erzeugt und die
 * Einkaufsliste dieselbe Einheiten-Normalisierung braucht. Eine zweite Implementierung im
 * Frontend wuerde davon abdriften, und das Zusammenfassen wuerde still aufhoeren zu
 * funktionieren.
 */
@Component
public class IngredientParser {

    /** Bruchzeichen, wie sie in Rezepten vorkommen. */
    private static final Map<Character, String> VULGAR_FRACTIONS = Map.ofEntries(
            Map.entry('½', "0.5"),
            Map.entry('⅓', "0.333"),
            Map.entry('⅔', "0.667"),
            Map.entry('¼', "0.25"),
            Map.entry('¾', "0.75"),
            Map.entry('⅕', "0.2"),
            Map.entry('⅖', "0.4"),
            Map.entry('⅗', "0.6"),
            Map.entry('⅘', "0.8"),
            Map.entry('⅙', "0.167"),
            Map.entry('⅚', "0.833"),
            Map.entry('⅛', "0.125"),
            Map.entry('⅜', "0.375"),
            Map.entry('⅝', "0.625"),
            Map.entry('⅞', "0.875")
    );

    /**
     * Woerter, die "Menge unbestimmt" bedeuten.
     *
     * <p>Sie wandern in die Anmerkung statt in den Namen: "etwas Mehl" soll als "Mehl" auf der
     * Einkaufsliste stehen und sich mit den 400 g aus dem anderen Rezept zusammenlegen lassen.
     */
    private static final List<String> VAGUE_AMOUNTS = List.of(
            "etwas", "evtl.", "eventuell", "n. B.", "nach Belieben", "nach Bedarf", "ein wenig"
    );

    /**
     * Zahl am Zeilenanfang: "400", "0.5", "0,5", "1/2", "2-3", "½", "1 ½".
     *
     * <p>Die Bruchzeichen-Alternative steht vorn, und das ist kein Zufall: Java probiert
     * Alternativen der Reihe nach. Stuende die Ziffern-Alternative vorn, haette "1 ½ EL" die
     * Menge 1 - sie passt auf die "1" und hoert dort auf.
     */
    private static final Pattern LEADING_AMOUNT = Pattern.compile(
            "^\\s*(\\d*\\s*[" + fractionChars() + "]|\\d+(?:[.,]\\d+)?(?:\\s*[/]\\s*\\d+)?)"
                    + "\\s*(?:[-–—]\\s*(\\d+(?:[.,]\\d+)?))?"
    );

    private static String fractionChars() {
        StringBuilder chars = new StringBuilder();
        VULGAR_FRACTIONS.keySet().forEach(chars::append);
        return chars.toString();
    }

    /**
     * Zerlegt mehrere Zeilen. Leere Zeilen fallen weg.
     */
    public List<ParsedIngredient> parseAll(String block) {
        List<ParsedIngredient> result = new ArrayList<>();
        if (block == null) {
            return result;
        }
        for (String line : block.split("\\R")) {
            if (!line.isBlank()) {
                result.add(parse(line));
            }
        }
        return result;
    }

    public ParsedIngredient parse(String rawLine) {
        String raw = rawLine == null ? "" : rawLine.trim();
        if (raw.isEmpty()) {
            return new ParsedIngredient(null, null, "", null, rawLine);
        }

        List<String> notes = new ArrayList<>();

        // 1. Klammer am Zeilenende. Von hinten aufgeloest, damit "Tomate(n) ((Pizzatomaten))"
        //    die aeussere Klammer erwischt und nicht das (n) mitten im Namen.
        String rest = extractTrailingParenthesis(raw, notes);

        // 2. Menge
        BigDecimal amount = null;
        Matcher amountMatch = LEADING_AMOUNT.matcher(rest);
        if (amountMatch.find()) {
            amount = toDecimal(amountMatch.group(1));
            if (amountMatch.group(2) != null) {
                // "2-3 Zwiebeln": die Untergrenze steht in der Menge, der Bereich in der
                // Anmerkung - wer drei kauft, hat nichts falsch gemacht.
                notes.add(amountMatch.group().trim());
            }
            if (amount != null) {
                rest = rest.substring(amountMatch.end()).trim();
            }
        }
        if (amount == null) {
            rest = stripVagueAmount(rest, notes);
        }

        // 3. Einheit - nur aus dem festen Vokabular
        String unit = null;
        String[] tokens = rest.split("\\s+", 2);
        if (tokens.length > 0) {
            // "2 TL, gehäuft Backpulver": das Komma klebt an der Einheit und haette den
            // Vokabeltreffer verhindert. Es wird abgetrennt und in Schritt 4 wieder
            // vorangestellt, damit dort der Zusatz erkannt wird.
            String candidate = tokens[0];
            boolean commaFollows = candidate.endsWith(",");
            if (commaFollows) {
                candidate = candidate.substring(0, candidate.length() - 1);
            }
            Optional<String> canonical = UnitVocabulary.canonical(candidate);
            if (canonical.isPresent()) {
                unit = canonical.get();
                rest = tokens.length > 1 ? tokens[1].trim() : "";
                if (commaFollows) {
                    rest = "," + rest;
                }
            }
        }

        // 4. Zusatz direkt hinter der Einheit: "2 TL, gehäuft Backpulver"
        if (rest.startsWith(",")) {
            String afterComma = rest.substring(1).trim();
            String[] parts = afterComma.split("\\s+", 2);
            if (parts.length == 2) {
                notes.add(parts[0].replaceAll(",$", ""));
                rest = parts[1].trim();
            } else {
                rest = afterComma;
            }
        }

        // 5. Nachgestellter Zusatz: "Ei(er), Größe L" -> Name "Ei(er)", Anmerkung "Größe L"
        int comma = rest.indexOf(", ");
        if (comma > 0) {
            notes.add(rest.substring(comma + 1).trim());
            rest = rest.substring(0, comma).trim();
        }

        String name = rest.trim();
        if (name.isEmpty()) {
            // Nur eine Menge und sonst nichts - dann ist die ganze Zeile der Name, sonst
            // stuende auf der Einkaufsliste "400 g" ohne zu sagen, wovon.
            return new ParsedIngredient(null, null, raw, null, raw);
        }

        String note = notes.isEmpty() ? null : String.join(", ", notes);
        return new ParsedIngredient(amount, unit, name, note, raw);
    }

    /**
     * Loest eine abschliessende Klammer heraus und legt ihren Inhalt in die Anmerkungen.
     *
     * <p>Zaehlt Klammern rueckwaerts, statt auf die erste offene Klammer zu greifen: Namen wie
     * {@code Tomate(n)} tragen selbst Klammern, und {@code 1 Dose Tomate(n) ((Pizzatomaten) à
     * 400 g)} soll die aeussere Gruppe verlieren, nicht das {@code (n)}.
     */
    private String extractTrailingParenthesis(String line, List<String> notes) {
        if (!line.endsWith(")")) {
            return line;
        }
        int depth = 0;
        for (int i = line.length() - 1; i >= 0; i--) {
            char c = line.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                depth--;
                if (depth == 0) {
                    String inside = line.substring(i + 1, line.length() - 1).trim();
                    String before = line.substring(0, i).trim();
                    // "Ei(er)" ohne weiteren Text davor ist kein Zusatz, sondern der Name.
                    if (before.isEmpty() || inside.isEmpty()) {
                        return line;
                    }
                    notes.add(inside);
                    return before;
                }
            }
        }
        return line;
    }

    private String stripVagueAmount(String rest, List<String> notes) {
        String lower = rest.toLowerCase(Locale.GERMAN);
        for (String vague : VAGUE_AMOUNTS) {
            String candidate = vague.toLowerCase(Locale.GERMAN);
            if (lower.startsWith(candidate + " ")) {
                notes.add(rest.substring(0, vague.length()).trim());
                return rest.substring(vague.length()).trim();
            }
        }
        return rest;
    }

    /**
     * Wandelt die erkannte Mengen-Zeichenfolge in eine Zahl.
     *
     * <p>Beide Dezimaltrenner sind zugelassen: chefkoch liefert {@code 0.5}, von Hand tippt man
     * {@code 0,5}.
     */
    private BigDecimal toDecimal(String token) {
        String text = token.trim();
        if (text.isEmpty()) {
            return null;
        }

        // Bruchzeichen, auch gemischt ("1 ½")
        for (Map.Entry<Character, String> fraction : VULGAR_FRACTIONS.entrySet()) {
            int index = text.indexOf(fraction.getKey());
            if (index >= 0) {
                String whole = text.substring(0, index).trim();
                BigDecimal value = new BigDecimal(fraction.getValue());
                if (!whole.isEmpty()) {
                    value = value.add(new BigDecimal(whole.replace(',', '.')));
                }
                return value;
            }
        }

        // ASCII-Bruch "1/2"
        if (text.contains("/")) {
            String[] parts = text.split("/");
            try {
                BigDecimal numerator = new BigDecimal(parts[0].trim().replace(',', '.'));
                BigDecimal denominator = new BigDecimal(parts[1].trim().replace(',', '.'));
                if (denominator.signum() == 0) {
                    return null;
                }
                return numerator.divide(denominator, 3, java.math.RoundingMode.HALF_UP);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                return null;
            }
        }

        try {
            return new BigDecimal(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
