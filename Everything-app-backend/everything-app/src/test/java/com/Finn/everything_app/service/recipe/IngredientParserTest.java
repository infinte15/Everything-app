package com.Finn.everything_app.service.recipe;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die Beispiele stammen bis auf die letzten drei woertlich von chefkoch.de-Rezeptseiten.
 * Ausgedachte Zutatenzeilen sind hier wertlos - der Parser scheitert an dem, was echte Seiten
 * schreiben, nicht an dem, was man sich als Grammatik ueberlegt.
 */
class IngredientParserTest {

    private final IngredientParser parser = new IngredientParser();

    private void assertParsed(String line, String amount, String unit, String name) {
        ParsedIngredient result = parser.parse(line);
        assertAmount(amount, result.amount(), line);
        assertEquals(unit, result.unit(), "Einheit von " + line);
        assertEquals(name, result.name(), "Name von " + line);
        assertEquals(line, result.rawText(), "Rohtext von " + line);
    }

    /**
     * Vergleicht ueber compareTo, nicht ueber equals: bei {@link BigDecimal} zaehlt fuer
     * equals auch die Nachkommastellen-Zahl, und "400" ist damit ungleich "400.0".
     */
    private void assertAmount(String expected, BigDecimal actual, String line) {
        if (expected == null) {
            assertNull(actual, "Menge von " + line);
            return;
        }
        assertNotNull(actual, "Menge von " + line);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "Menge von " + line + ": erwartet " + expected + ", war " + actual);
    }

    // Der Grund fuer das geschlossene Einheiten-Vokabular. Mit der naheliegenden Regel
    // "das zweite Wort ist die Einheit" waere die Einheit hier "große".
    @Test
    void einAdjektivWirdNichtZurEinheit() {
        assertParsed("3 große Ei(er), Größe L", "3", null, "große Ei(er)");
        assertEquals("Größe L", parser.parse("3 große Ei(er), Größe L").note());
    }

    @Test
    void mengeEinheitName() {
        assertParsed("400 g Mehl (wer mag, kann Vollkornmehl verwenden)", "400", "g", "Mehl");
        assertParsed("750 ml Milch (fettarme)", "750", "ml", "Milch");
        assertParsed("1 EL Oregano", "1", "EL", "Oregano");
        assertParsed("600 g Hackfleisch (gemischtes)", "600", "g", "Hackfleisch");
    }

    @Test
    void deutschePluralklammernWerdenNormalisiert() {
        assertParsed("1 Prise(n) Salz", "1", "Prise", "Salz");
        assertParsed("2 Zehe/n Knoblauch", "2", "Zehe", "Knoblauch");
        assertParsed("0.5 Tube/n Tomatenmark (à 200 g)", "0.5", "Tube", "Tomatenmark");
    }

    // chefkoch schreibt mal Pkt., mal Pck. Ohne Normalisierung legt die Einkaufsliste sie
    // als zwei Zeilen ab.
    @Test
    void pktUndPckSindDieselbeEinheit() {
        assertEquals("Pck.", parser.parse("0.5 Pkt. Tomate(n) (passierte)").unit());
        assertEquals("Pck.", parser.parse("1 Pck. Löffelbiskuits").unit());
    }

    // chefkoch trennt Dezimalstellen mit Punkt, von Hand tippt man ein Komma.
    @Test
    void beideDezimaltrennerWerdenVerstanden() {
        assertParsed("0.5 Pkt. Backpulver", "0.5", "Pck.", "Backpulver");
        assertParsed("0,5 l Milch", "0.5", "l", "Milch");
    }

    @Test
    void bruchzeichenAuchGemischt() {
        assertParsed("½ TL Zimt", "0.5", "TL", "Zimt");
        assertParsed("1 ½ EL Zucker", "1.5", "EL", "Zucker");
        assertParsed("1/2 Bund Petersilie", "0.5", "Bund", "Petersilie");
    }

    // Wer drei kauft, hat nichts falsch gemacht - also die Untergrenze, und der Bereich
    // bleibt als Anmerkung sichtbar.
    @Test
    void einBereichWirdZurUntergrenzeMitAnmerkung() {
        ParsedIngredient result = parser.parse("2-3 Zwiebeln");

        assertEquals(0, new BigDecimal("2").compareTo(result.amount()));
        assertEquals("Zwiebeln", result.name());
        assertTrue(result.note().contains("2-3"), "Bereich fehlt in der Anmerkung: " + result.note());
    }

    @Test
    void zusatzHinterDerEinheit() {
        assertParsed("2 TL, gehäuft Backpulver", "2", "TL", "Backpulver");
        assertEquals("gehäuft", parser.parse("2 TL, gehäuft Backpulver").note());
    }

    // Die Klammer wird von hinten aufgeloest: "Tomate(n)" traegt selbst Klammern, und die
    // aeussere Gruppe ist die Anmerkung.
    @Test
    void geschachtelteKlammernAmZeilenende() {
        ParsedIngredient result = parser.parse("1 Dose Tomate(n) ((Pizzatomaten) à 400 g)");

        assertEquals(0, new BigDecimal("1").compareTo(result.amount()));
        assertEquals("Dose", result.unit());
        assertEquals("Tomate(n)", result.name());
        assertEquals("(Pizzatomaten) à 400 g", result.note());
    }

    @Test
    void eineKlammerDieZumNamenGehoertBleibtDort() {
        assertParsed("3 Zwiebel(n) (klein gehackte)", "3", null, "Zwiebel(n)");
        assertEquals("klein gehackte", parser.parse("3 Zwiebel(n) (klein gehackte)").note());
    }

    // Eine erfundene 0 waere schlimmer als keine Zahl - sie wuerde beim Umrechnen
    // mitwandern und als "0 g Salz" auf dem Einkaufszettel stehen.
    @Test
    void zutatOhneMengeBehaeltKeineMenge() {
        assertParsed("Salz", null, null, "Salz");
        assertParsed("Butter (zum Backen)", null, null, "Butter");
    }

    // "etwas Mehl" soll als "Mehl" einkaufbar sein und sich mit den 400 g aus dem anderen
    // Rezept zusammenlegen lassen.
    @Test
    void unbestimmteMengeWandertInDieAnmerkung() {
        ParsedIngredient result = parser.parse("etwas Mehl (zum Ausrollen)");

        assertNull(result.amount());
        assertEquals("Mehl", result.name());
        assertTrue(result.note().contains("etwas"), "Anmerkung: " + result.note());
        assertTrue(result.note().contains("zum Ausrollen"), "Anmerkung: " + result.note());
    }

    @Test
    void mehrereWoerterBleibenZusammenImNamen() {
        assertParsed("30 ml Mineralwasser mit Kohlensäure", "30", "ml", "Mineralwasser mit Kohlensäure");
    }

    @Test
    void kommaImKlammerinhaltTrenntDenNamenNicht() {
        assertParsed("100 g Speck (geräuchert, durchwachsen)", "100", "g", "Speck");
    }

    // Der Parser darf nie werfen: eine unlesbare Zeile wird zur Zutat ohne Menge, mit der
    // ganzen Zeile als Namen. Das ist genau das Verhalten von vorher.
    @Test
    void wirftNiemals() {
        for (String line : List.of("", "   ", "???", "(", ")", "1/0 Liter Wasser", "400 g",
                "-", "0", "12345678901234567890 g Mehl")) {
            assertDoesNotThrow(() -> parser.parse(line), "warf bei: " + line);
        }
    }

    @Test
    void eineZeileOhneNamenBleibtVollstaendigErhalten() {
        // "400 g" allein sagt nicht, wovon - dann lieber die ganze Zeile als Name.
        assertParsed("400 g", null, null, "400 g");
    }

    @Test
    void mehrereZeilenAufEinmalOhneLeerzeilen() {
        List<ParsedIngredient> result = parser.parseAll("400 g Mehl\n\n1 Prise(n) Salz\n   \nSalz");

        assertEquals(3, result.size());
        assertEquals("Mehl", result.get(0).name());
        assertEquals("Prise", result.get(1).unit());
        assertEquals("Salz", result.get(2).name());
    }

    @Test
    void nullUndLeerBleibenHarmlos() {
        assertTrue(parser.parseAll(null).isEmpty());
        assertEquals("", parser.parse("").name());
    }
}
