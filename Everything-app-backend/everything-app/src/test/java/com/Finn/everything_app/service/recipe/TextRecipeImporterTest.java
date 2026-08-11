package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.dto.RecipeImportPreviewDTO;
import com.Finn.everything_app.dto.RecipeIngredientDTO;
import com.Finn.everything_app.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die Beispiele sind nach echten Instagram-Bildunterschriften gebaut, nicht nach einer
 * ausgedachten Grammatik: der Importer scheitert an Emoji-Aufzaehlungen, fehlenden
 * Ueberschriften und angehaengten Hashtag-Blocks, nicht an dem, was man sich als Format
 * ueberlegt.
 */
class TextRecipeImporterTest {

    private final TextRecipeImporter importer = new TextRecipeImporter(new IngredientParser());

    private RecipeImportPreviewDTO parse(String text) {
        return importer.importFrom(text, null, null);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        if (expected == null) {
            assertNull(actual);
            return;
        }
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "erwartet " + expected + ", war " + actual);
    }

    // ── Normalfall ────────────────────────────────────────────────────────────────────────

    @Test
    void zutatenUndZubereitungMitUeberschriften() {
        var preview = parse("""
                Cremige Zitronen-Pasta
                Das schnellste Abendessen der Woche.

                Zutaten:
                - 250 g Spaghetti
                - 200 ml Sahne
                - 1 Zitrone
                - 2 Zehen Knoblauch
                - Salz, Pfeffer

                Zubereitung:
                1. Nudeln in Salzwasser kochen.
                2. Knoblauch in Öl anbraten.
                3. Sahne und Zitronensaft zugeben.
                4. Alles vermengen und abschmecken.
                """);

        var recipe = preview.getRecipe();
        assertEquals("Cremige Zitronen-Pasta", recipe.getName());
        assertEquals("Das schnellste Abendessen der Woche.", recipe.getDescription());
        assertEquals(5, recipe.getIngredients().size());
        assertEquals(4, recipe.getSteps().size());
        assertEquals("Sonstiges", recipe.getCategory());
        assertEquals("Instagram", recipe.getSourceName());
    }

    @Test
    void ueberschriftMitEmojiUndOhneDoppelpunkt() {
        var recipe = parse("""
                Ofengemüse
                🛒 Zutaten
                - 2 Paprika
                - 1 Zucchini
                👩‍🍳 Zubereitung
                Alles in den Ofen schieben und 40 Minuten backen.
                """).getRecipe();

        assertEquals(2, recipe.getIngredients().size());
        assertEquals(1, recipe.getSteps().size());
        assertEquals("Alles in den Ofen schieben und 40 Minuten backen.",
                recipe.getSteps().get(0).getText());
    }

    @Test
    void ueberschriftMitPortionsangabeIstEineUeberschrift() {
        var recipe = parse("""
                Pfannkuchen
                Zutaten für 2 Personen:
                - 200 g Mehl
                - 3 Eier
                Zubereitung:
                Teig anrühren und ausbacken.
                """).getRecipe();

        assertEquals(2, recipe.getIngredients().size());
        assertEquals(2, recipe.getServings());
        // Die Ueberschrift darf nicht als Zutat durchgehen.
        assertTrue(recipe.getIngredients().stream()
                .noneMatch(i -> i.getName().toLowerCase().contains("zutaten")));
    }

    // ── Hashtags ──────────────────────────────────────────────────────────────────────────

    @Test
    void hashtagsWerdenZuTagsUndVerschwindenAusDenSchritten() {
        var recipe = parse("""
                Dal
                Zutaten:
                - 200 g rote Linsen
                Zubereitung:
                Linsen weich kochen. #vegan
                #foodporn #schnellerezepte
                """).getRecipe();

        assertEquals("vegan,foodporn,schnellerezepte", recipe.getTags());
        assertEquals(1, recipe.getSteps().size());
        assertFalse(recipe.getSteps().get(0).getText().contains("#"));
        assertEquals("Linsen weich kochen.", recipe.getSteps().get(0).getText());
    }

    // ── Rueckfallebene ohne Ueberschriften ────────────────────────────────────────────────

    @Test
    void ohneUeberschriftenTrenntDerAufzaehlungslauf() {
        var preview = parse("""
                Shakshuka
                Mein Lieblingsfrühstück am Wochenende.
                • 1 Dose Tomaten
                • 4 Eier
                • 1 Zwiebel
                Zwiebel anbraten, Tomaten zugeben und die Eier hineinschlagen.
                Zugedeckt stocken lassen.
                """);

        var recipe = preview.getRecipe();
        assertEquals("Shakshuka", recipe.getName());
        assertEquals("Mein Lieblingsfrühstück am Wochenende.", recipe.getDescription());
        assertEquals(3, recipe.getIngredients().size());
        assertEquals(2, recipe.getSteps().size());
    }

    @Test
    void ohneErkennbareZutatenWirdAllesZubereitungPlusWarnung() {
        var preview = parse("""
                Nudeln mit Pesto
                Nudeln kochen, Pesto unterrühren, fertig.
                Dazu passt ein Salat.
                """);

        assertTrue(preview.getRecipe().getIngredients().isEmpty());
        assertEquals(2, preview.getRecipe().getSteps().size());
        assertTrue(preview.getWarnings().contains(
                "Zutaten und Zubereitung waren nicht zu trennen - alles steht unter Zubereitung."));
    }

    // ── Schritte ──────────────────────────────────────────────────────────────────────────

    @Test
    void nummerierteSchritteVerlierenIhreNummer() {
        var recipe = parse("""
                Risotto
                Zutaten:
                - 300 g Risottoreis
                Zubereitung:
                1. Reis anschwitzen.
                2. Brühe nach und nach zugeben.
                """).getRecipe();

        assertEquals("Reis anschwitzen.", recipe.getSteps().get(0).getText());
        assertEquals("Brühe nach und nach zugeben.", recipe.getSteps().get(1).getText());
    }

    @Test
    void einLangerAbsatzBleibtEinSchritt() {
        String absatz = "Zuerst die Zwiebeln fein würfeln und in etwas Öl glasig anschwitzen, "
                + "dann den Knoblauch dazugeben und kurz mitbraten, anschließend das Hackfleisch "
                + "zugeben und krümelig anbraten, mit Tomatenmark tomatisieren, mit Rotwein "
                + "ablöschen und alles bei kleiner Hitze mindestens zwei Stunden schmoren lassen, "
                + "dabei gelegentlich umrühren und bei Bedarf Brühe nachgießen.";

        var preview = parse("""
                Bolognese
                Zutaten:
                - 500 g Hackfleisch
                Zubereitung:
                """ + absatz);

        assertEquals(1, preview.getRecipe().getSteps().size());
        assertTrue(preview.getWarnings().contains(
                "Die Zubereitung stand in einem Stück - sie ist ein einziger Schritt."));
    }

    // ── Portionen und Zeit ────────────────────────────────────────────────────────────────

    @Test
    void portionenUndZeitWerdenGelesen() {
        var recipe = parse("""
                Curry
                Für 3 Personen | 25 Minuten
                Zutaten:
                - 400 ml Kokosmilch
                Zubereitung:
                Alles zusammen köcheln lassen.
                """).getRecipe();

        assertEquals(3, recipe.getServings());
        assertEquals(25, recipe.getPrepTimeMinutes());
        assertEquals(0, recipe.getCookTimeMinutes());
    }

    @Test
    void stundenUndMinutenWerdenAddiert() {
        var recipe = parse("""
                Schmorbraten
                Dauert 2 Stunden 30 Minuten.
                Zutaten:
                - 1 kg Rindfleisch
                Zubereitung:
                Schmoren lassen.
                """).getRecipe();

        assertEquals(150, recipe.getPrepTimeMinutes());
    }

    @Test
    void ohnePortionsangabeVierPlusWarnung() {
        var preview = parse("""
                Suppe
                Zutaten:
                - 1 Kürbis
                Zubereitung:
                Pürieren.
                """);

        assertEquals(4, preview.getRecipe().getServings());
        assertTrue(preview.getWarnings().contains(
                "Keine Portionsangabe gefunden - 4 Portionen angenommen."));
        assertTrue(preview.getWarnings().contains(
                "Keine Zeitangabe gefunden - bitte selbst eintragen."));
    }

    // ── Zutatenzeilen ─────────────────────────────────────────────────────────────────────

    @Test
    void zutatenzeilenLaufenDurchDenIngredientParser() {
        var recipe = parse("""
                Pasta
                Zutaten:
                - 250 g Spaghetti
                Zubereitung:
                Kochen.
                """).getRecipe();

        RecipeIngredientDTO first = recipe.getIngredients().get(0);
        assertAmount("250", first.getAmount());
        assertEquals("g", first.getUnit());
        assertEquals("Spaghetti", first.getName());
    }

    @Test
    void emojiAufzaehlungszeichenWerdenEntfernt() {
        var recipe = parse("""
                Bowl
                Zutaten:
                🥕 2 Möhren
                🥑 1 Avocado
                Zubereitung:
                Alles klein schneiden.
                """).getRecipe();

        assertEquals(2, recipe.getIngredients().size());
        assertEquals("Möhren", recipe.getIngredients().get(0).getName());
        assertAmount("2", recipe.getIngredients().get(0).getAmount());
        assertEquals("Avocado", recipe.getIngredients().get(1).getName());
    }

    @Test
    void zierlinienWerdenVerworfen() {
        var recipe = parse("""
                Brot
                ••••••••••
                Zutaten:
                - 500 g Mehl
                ——————
                Zubereitung:
                Backen.
                """).getRecipe();

        assertEquals("Brot", recipe.getName());
        assertEquals(1, recipe.getIngredients().size());
        assertEquals(1, recipe.getSteps().size());
    }

    // ── Notiz und Herkunft ────────────────────────────────────────────────────────────────

    @Test
    void tippLandetInDenNotizen() {
        var recipe = parse("""
                Tiramisu
                Zutaten:
                - 500 g Mascarpone
                Zubereitung:
                Schichten.
                Tipp:
                Am Vortag zubereiten, dann zieht es besser durch.
                """).getRecipe();

        assertEquals("Am Vortag zubereiten, dann zieht es besser durch.", recipe.getNotes());
        assertEquals(1, recipe.getSteps().size());
    }

    @Test
    void herkunftWirdUebernommenUndAdresseGeprueft() {
        var recipe = importer.importFrom("""
                Pancakes
                Zutaten:
                - 2 Eier
                Zubereitung:
                Backen.
                """, "@kochbuch", "https://www.instagram.com/p/abc/").getRecipe();

        assertEquals("@kochbuch", recipe.getSourceName());
        assertEquals("https://www.instagram.com/p/abc/", recipe.getSourceUrl());
    }

    @Test
    void keineAdresseBeiUnsinnigemSchema() {
        var recipe = importer.importFrom("""
                Pancakes
                Zutaten:
                - 2 Eier
                Zubereitung:
                Backen.
                """, null, "javascript:alert(1)").getRecipe();

        assertNull(recipe.getSourceUrl());
    }

    // ── Grenzfaelle ───────────────────────────────────────────────────────────────────────

    @Test
    void leererTextWirftBadRequest() {
        assertThrows(BadRequestException.class, () -> parse("   \n\n😀\n•••"));
        assertThrows(BadRequestException.class, () -> parse(null));
    }

    @Test
    void nurHashtagsWerfenEbenfalls() {
        assertThrows(BadRequestException.class, () -> parse("#foodporn #rezept"));
    }

    @Test
    void warnungWennKeineMengeErkennbarIst() {
        var preview = parse("""
                Salat
                Zutaten:
                - Tomaten
                - Gurke
                - Feta
                Zubereitung:
                Mischen.
                """);

        assertTrue(preview.getWarnings().contains(
                "Bei den Zutaten war keine Menge erkennbar - bitte nachsehen."));
    }

    // ── Bausteine ─────────────────────────────────────────────────────────────────────────

    @Test
    void looksLikeIngredientTrenntZeileVonSatz() {
        assertTrue(TextRecipeImporter.looksLikeIngredient("- 250 g Mehl"));
        assertTrue(TextRecipeImporter.looksLikeIngredient("2 Eier"));
        assertTrue(TextRecipeImporter.looksLikeIngredient("• Salz"));
        // Faengt mit einer Ziffer an, ist aber ein nummerierter Schritt.
        assertFalse(TextRecipeImporter.looksLikeIngredient("2. Nudeln kochen."));
        // Kurz, aber ein Satz.
        assertFalse(TextRecipeImporter.looksLikeIngredient("Alles gut vermengen."));
        // Ohne Aufzaehlungszeichen und ohne Menge.
        assertFalse(TextRecipeImporter.looksLikeIngredient("Guten Appetit"));
    }

    @Test
    void sectionOfErkenntDieUeberschriften() {
        assertEquals(TextRecipeImporter.Section.INGREDIENTS,
                TextRecipeImporter.sectionOf("Zutaten:"));
        assertEquals(TextRecipeImporter.Section.INGREDIENTS,
                TextRecipeImporter.sectionOf("🛒 DU BRAUCHST"));
        assertEquals(TextRecipeImporter.Section.STEPS,
                TextRecipeImporter.sectionOf("So geht's –"));
        assertEquals(TextRecipeImporter.Section.NOTES,
                TextRecipeImporter.sectionOf("Tipp"));
        assertNull(TextRecipeImporter.sectionOf("- 250 g Mehl"));
        assertNull(TextRecipeImporter.sectionOf("Die Zutaten sollten Zimmertemperatur haben."));
    }

    @Test
    void cleanLinesDeckeltDieZeilenzahl() {
        String viele = "Zeile 1\n".repeat(TextRecipeImporter.MAX_LINES + 50);
        List<String> lines = TextRecipeImporter.cleanLines(viele);
        assertEquals(TextRecipeImporter.MAX_LINES, lines.size());
    }
}
