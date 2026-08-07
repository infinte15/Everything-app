package com.Finn.everything_app.service.recipe;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Die Einheiten, die eine Zutat haben darf - eine geschlossene Liste.
 *
 * <p>Das ist die wichtigste Entscheidung im Zutaten-Parser. Die naheliegende Regel "das zweite
 * Wort ist die Einheit" scheitert an der ersten echten Zeile von chefkoch:
 * {@code 3 große Ei(er), Größe L} haette dann die Einheit "große". Steht die Einheit dagegen in
 * einer festen Liste, faellt alles Unbekannte automatisch in den Namen - der schlechteste Fall
 * ist eine Zutat ohne Einheit, nicht eine mit falscher.
 *
 * <p>Die Schreibweisen kommen aus echten Rezeptseiten: {@code Prise(n)}, {@code Dose/n},
 * {@code Zehe/n}, {@code Pkt.} neben {@code Pck.}. Sie werden alle auf eine Form normalisiert,
 * sonst summiert die Einkaufsliste "2 Pkt." und "1 Pck." nicht zusammen.
 */
public final class UnitVocabulary {

    private UnitVocabulary() {
    }

    /**
     * Schreibweise (klein) auf die kanonische Form.
     *
     * <p>Die Schluessel sind bereits von Plural-Anhaengseln befreit, siehe
     * {@link #stripPluralSuffix(String)}.
     */
    private static final Map<String, String> CANONICAL = Map.ofEntries(
            // Gewicht und Volumen - die einzigen, die sich umrechnen lassen
            Map.entry("g", "g"),
            Map.entry("gramm", "g"),
            Map.entry("kg", "kg"),
            Map.entry("kilogramm", "kg"),
            Map.entry("mg", "mg"),
            Map.entry("ml", "ml"),
            Map.entry("milliliter", "ml"),
            Map.entry("l", "l"),
            Map.entry("liter", "l"),
            Map.entry("cl", "cl"),

            // Kuechenmasse
            Map.entry("el", "EL"),
            Map.entry("esslöffel", "EL"),
            Map.entry("tl", "TL"),
            Map.entry("teelöffel", "TL"),
            Map.entry("msp.", "Msp."),
            Map.entry("msp", "Msp."),
            Map.entry("messerspitze", "Msp."),
            Map.entry("prise", "Prise"),
            Map.entry("tasse", "Tasse"),
            Map.entry("tropfen", "Tropfen"),
            Map.entry("handvoll", "Handvoll"),

            // Handelsformen
            Map.entry("pck.", "Pck."),
            Map.entry("pck", "Pck."),
            Map.entry("pkt.", "Pck."),
            Map.entry("pkt", "Pck."),
            Map.entry("päckchen", "Pck."),
            Map.entry("packung", "Pck."),
            Map.entry("dose", "Dose"),
            Map.entry("glas", "Glas"),
            Map.entry("flasche", "Flasche"),
            Map.entry("becher", "Becher"),
            Map.entry("tube", "Tube"),
            Map.entry("beutel", "Beutel"),
            Map.entry("tafel", "Tafel"),

            // Stueckiges
            Map.entry("stück", "Stück"),
            Map.entry("stk.", "Stück"),
            Map.entry("stk", "Stück"),
            Map.entry("bund", "Bund"),
            Map.entry("zehe", "Zehe"),
            Map.entry("kugel", "Kugel"),
            Map.entry("blatt", "Blatt"),
            Map.entry("scheibe", "Scheibe"),
            Map.entry("stange", "Stange"),
            Map.entry("zweig", "Zweig"),
            Map.entry("stiel", "Stiel"),
            Map.entry("kopf", "Kopf"),
            Map.entry("knolle", "Knolle"),
            Map.entry("portion", "Portion"),
            Map.entry("cm", "cm")
    );

    /**
     * Kanonische Form einer Einheit, oder leer, wenn das Wort keine ist.
     */
    public static Optional<String> canonical(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String key = stripPluralSuffix(token.trim()).toLowerCase(Locale.GERMAN);
        return Optional.ofNullable(CANONICAL.get(key));
    }

    /**
     * Entfernt deutsche Plural-Anhaengsel: {@code Prise(n)} und {@code Dose/n} werden zu
     * {@code Prise} und {@code Dose}.
     */
    static String stripPluralSuffix(String token) {
        String result = token;
        int paren = result.indexOf('(');
        if (paren > 0) {
            result = result.substring(0, paren);
        }
        int slash = result.indexOf('/');
        if (slash > 0) {
            result = result.substring(0, slash);
        }
        return result.trim();
    }

    // ── Umrechnung ────────────────────────────────────────────────────────────────────────

    /**
     * Basiseinheit fuers Zusammenfassen: kg rechnet in g, l in ml.
     *
     * <p>Bewusst NUR Gewicht und Volumen. Ein EL Mehl sind keine 15 ml Mehl - die Umrechnung
     * haengt an der Dichte, und eine falsche Zahl auf dem Einkaufszettel ist schlimmer als zwei
     * getrennte Zeilen.
     */
    public static String baseUnit(String unit) {
        if (unit == null) return null;
        return switch (unit) {
            case "kg" -> "g";
            case "mg" -> "g";
            case "l" -> "ml";
            case "cl" -> "ml";
            default -> unit;
        };
    }

    /** Faktor in die Basiseinheit. */
    public static BigDecimal toBaseFactor(String unit) {
        if (unit == null) return BigDecimal.ONE;
        return switch (unit) {
            case "kg" -> new BigDecimal("1000");
            case "mg" -> new BigDecimal("0.001");
            case "l" -> new BigDecimal("1000");
            case "cl" -> new BigDecimal("10");
            default -> BigDecimal.ONE;
        };
    }
}
