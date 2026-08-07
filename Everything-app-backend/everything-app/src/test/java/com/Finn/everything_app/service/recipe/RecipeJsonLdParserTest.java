package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.dto.RecipeDTO;
import com.Finn.everything_app.dto.RecipeImportPreviewDTO;
import com.Finn.everything_app.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gegen eine echte, gespeicherte chefkoch-Antwort
 * ({@code src/test/resources/chefkoch/pfannkuchen.json}) - kein Netz, keine Datenbank.
 *
 * <p>Jede Erwartung hier bildet einen Fallstrick ab, den die Seite tatsaechlich enthaelt. Gegen
 * eine ausgedachte schema.org-Datei getestet, waere der Parser gruen und trotzdem unbrauchbar.
 */
class RecipeJsonLdParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RecipeJsonLdParser parser;
    private JsonNode fixture;

    @BeforeEach
    void setUp() throws IOException {
        parser = new RecipeJsonLdParser(objectMapper, new IngredientParser());
        try (InputStream in = new ClassPathResource("chefkoch/pfannkuchen.json").getInputStream()) {
            fixture = objectMapper.readTree(in);
        }
    }

    private RecipeDTO parsed() {
        return parser.parse(fixture, "https://www.chefkoch.de/rezepte/1/x.html", "chefkoch.de").getRecipe();
    }

    // Das Rezept liegt in @graph zwischen VideoObject, WebPage, Organization und zwei Person -
    // gesucht wird ueber @type, nicht ueber die Position.
    @Test
    void findetDasRezeptImGraph() {
        assertNotNull(parsed());
        assertTrue(parsed().getName().startsWith("Der perfekte Pfannkuchen"));
    }

    // Der Titel traegt " von zwergenmuomi" - das ist der Nutzername, nicht Teil des Rezepts.
    @Test
    void schneidetDenAutorVomTitelAb() {
        assertEquals("Der perfekte Pfannkuchen - gelingt einfach immer", parsed().getName());
    }

    // image ist {"@id": "…#primaryimage"} - eine Referenz auf einen anderen Knoten. Wer sie
    // als Adresse nimmt, speichert die Rezeptseite als Bild und zeigt spaeter nichts an.
    @Test
    void loestDieBildreferenzGegenDenGraphenAuf() {
        String imageUrl = parsed().getImageUrl();

        assertNotNull(imageUrl);
        assertTrue(imageUrl.startsWith("https://img.chefkoch-cdn.de/"), imageUrl);
        assertTrue(imageUrl.endsWith(".jpg"), imageUrl);
        assertFalse(imageUrl.contains("#primaryimage"), imageUrl);
    }

    // recipeYield ist ein Array: ["4", "4 Portionen"].
    @Test
    void liestDiePortionenAusDemArray() {
        assertEquals(4, parsed().getServings());
    }

    @Test
    void liestDieZeitenAlsIsoDauer() {
        assertEquals(5, parsed().getPrepTimeMinutes());
        assertEquals(10, parsed().getCookTimeMinutes());
    }

    // recipeInstructions ist [HowToSection{itemListElement:[HowToStep]}] - die Schritte liegen
    // eine Ebene tiefer, und wer die Sektion fuer einen Schritt haelt, bekommt genau einen.
    @Test
    void klopftDieSchritteEineEbeneFlach() {
        assertTrue(parsed().getSteps().size() >= 2,
                "nur " + parsed().getSteps().size() + " Schritt(e) gefunden");
        assertTrue(parsed().getSteps().get(0).getText().contains("Mehl"));
    }

    @Test
    void entferntFuehrendeNummernAusDenSchritten() {
        parsed().getSteps().forEach(step ->
                assertFalse(step.getText().matches("^\\d+[.)].*"),
                        "Schritt beginnt mit einer Nummer: " + step.getText()));
    }

    // description ist bei chefkoch Suchmaschinentext ("Über 2834 Bewertungen und für
    // vorzüglich befunden. Mit ► Portionsrechner …"). Der echte Text steht in abstract.
    @Test
    void bevorzugtAbstractVorDerSeoBeschreibung() {
        String description = parsed().getDescription();

        assertNotNull(description);
        assertFalse(description.contains("Bewertungen und für"), description);
        assertFalse(description.contains("Portionsrechner"), description);
    }

    @Test
    void zerlegtDieZutatenMitMengeUndEinheit() {
        var ingredients = parsed().getIngredients();

        assertEquals(6, ingredients.size());
        assertEquals("Mehl", ingredients.get(0).getName());
        assertEquals("g", ingredients.get(0).getUnit());
        assertEquals(0, new java.math.BigDecimal("400").compareTo(ingredients.get(0).getAmount()));
        // Die letzte Zeile ist "Butter (zum Backen)" - ohne Menge.
        assertEquals("Butter", ingredients.get(5).getName());
        assertNull(ingredients.get(5).getAmount());
    }

    // aggregateRating steht in der Datei (4,72 aus 2834 Stimmen), ist aber die Meinung von
    // Fremden. Der Space zeigt die eigene Bewertung.
    @Test
    void uebernimmtDieFremdbewertungNicht() {
        assertNull(parsed().getRating());
    }

    // Die Schwierigkeit steht nicht im JSON-LD, sondern nur im HTML hinter einem Vue-Hash,
    // der sich mit jedem Deploy aendert. Eine ehrliche Vorgabe statt geraten.
    @Test
    void setztEineVorgabeFuerDieSchwierigkeit() {
        assertEquals("Normal", parsed().getDifficulty());
    }

    @Test
    void merktSichDieHerkunft() {
        assertEquals("https://www.chefkoch.de/rezepte/1/x.html", parsed().getSourceUrl());
        assertEquals("chefkoch.de", parsed().getSourceName());
    }

    @Test
    void bildetChefkochsRubrikAufDenEigenenKatalogAb() {
        // "Mehlspeisen" laut Datei
        assertEquals("Backen", parsed().getCategory());
        // Was nicht in der Zuordnung steht, wird ehrlich zu "Sonstiges" statt geraten.
        assertEquals("Sonstiges", parser.mapCategory("Silvester"));
        assertEquals("Sonstiges", parser.mapCategory(null));
        assertEquals("Pasta & Reis", parser.mapCategory("pasta & nudel"));
    }

    @Test
    void eineSeiteOhneRezeptKnotenIstEinKlarerFehler() {
        JsonNode ohneRezept = objectMapper.createObjectNode()
                .put("@context", "https://schema.org");

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> parser.parse(ohneRezept, "https://www.chefkoch.de/x", "chefkoch.de"));
        assertTrue(thrown.getMessage().contains("kein Rezept"), thrown.getMessage());
    }

    // Ein Rezept ohne Zutaten wird trotzdem uebernommen - mit Hinweis. Teilerfolg schlaegt
    // Totalverweigerung, den Rest tippt man in zwei Minuten.
    @Test
    void meldetFehlendeZutatenStattDenImportZuVerweigern() throws IOException {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode)
                fixture.get("@graph").get(0);
        node.remove("recipeIngredient");

        RecipeImportPreviewDTO preview =
                parser.parse(fixture, "https://www.chefkoch.de/x", "chefkoch.de");

        assertNotNull(preview.getRecipe());
        assertTrue(preview.getWarnings().stream().anyMatch(w -> w.contains("Zutaten")),
                preview.getWarnings().toString());
    }
}
