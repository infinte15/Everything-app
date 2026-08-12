package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.dto.RecipeDTO;
import com.Finn.everything_app.dto.RecipeImportPreviewDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die zweite Stufe der Kette: dieselben Felder wie im JSON-LD, nur als {@code itemprop} ueber
 * die Seite verstreut. Aeltere Blogs liefern nur das.
 */
class RecipeMicrodataParserTest {

    private RecipeMicrodataParser parser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        IngredientParser ingredientParser = new IngredientParser();
        parser = new RecipeMicrodataParser(ingredientParser,
                new RecipeJsonLdParser(objectMapper, ingredientParser));
    }

    private RecipeDTO parse(String html) {
        Document document = Jsoup.parse(html, "https://kochblog.example/rezept");
        return parser.tryParse(document, "https://kochblog.example/rezept", "kochblog.example")
                .orElseThrow()
                .getRecipe();
    }

    private static final String SEITE = """
            <html><body>
            <div itemscope itemtype="http://schema.org/Recipe">
              <h1 itemprop="name">Linsensuppe</h1>
              <p itemprop="description">Deftig und schnell.</p>
              <img itemprop="image" src="/bilder/linsen.jpg">
              <meta itemprop="prepTime" content="PT10M">
              <time itemprop="cookTime" datetime="PT40M">40 Minuten</time>
              <span itemprop="recipeYield">4 Portionen</span>
              <span itemprop="recipeCategory">Suppe</span>
              <span itemprop="difficulty">einfach</span>
              <ul>
                <li itemprop="recipeIngredient">250 g rote Linsen</li>
                <li itemprop="recipeIngredient">2 Karotten</li>
                <li itemprop="recipeIngredient"><a href="/zutat/essig">1 EL Essig</a></li>
              </ul>
              <div itemprop="recipeInstructions">
                <ol><li>1. Linsen abspülen.</li><li>Alles 40 Minuten kochen.</li></ol>
              </div>
              <span itemprop="nutrition" itemscope>
                <span itemprop="calories">320 kcal</span>
              </span>
            </div></body></html>
            """;

    @Test
    void liestDieFelderAusDenAttributen() {
        RecipeDTO recipe = parse(SEITE);

        assertEquals("Linsensuppe", recipe.getName());
        assertEquals("Deftig und schnell.", recipe.getDescription());
        assertEquals(10, recipe.getPrepTimeMinutes());
        assertEquals(40, recipe.getCookTimeMinutes());
        assertEquals(4, recipe.getServings());
        assertEquals("Suppe & Eintopf", recipe.getCategory());
        assertEquals("Einfach", recipe.getDifficulty());
        assertEquals(320, recipe.getCalories());
    }

    // content vor datetime vor Text: <meta> hat gar keinen Text, und der Text von <time> ist
    // "40 Minuten" und keine Dauer.
    @Test
    void nimmtDenWertAusDemRichtigenAttribut() {
        RecipeDTO recipe = parse(SEITE);

        assertEquals(10, recipe.getPrepTimeMinutes(), "content von <meta>");
        assertEquals(40, recipe.getCookTimeMinutes(), "datetime von <time>");
    }

    // Ein relatives Bild allein waere im Kochbuch ein leerer Rahmen.
    @Test
    void loestRelativeBildadressenAuf() {
        assertEquals("https://kochblog.example/bilder/linsen.jpg", parse(SEITE).getImageUrl());
    }

    // Eine verlinkte Zutat ist die Zutat und nicht die Verweisadresse.
    @Test
    void liestVerlinkteZutatenAlsText() {
        var ingredients = parse(SEITE).getIngredients();

        assertEquals(3, ingredients.size());
        assertEquals("rote Linsen", ingredients.get(0).getName());
        // Der Text des Verweises, nicht seine Adresse.
        assertEquals("Essig", ingredients.get(2).getName());
        assertEquals("EL", ingredients.get(2).getUnit());
    }

    // Ein einziges recipeInstructions-Element mit einer Liste darin ist nicht ein Schritt.
    @Test
    void klopftEineSchrittlisteAuseinander() {
        var steps = parse(SEITE).getSteps();

        assertEquals(2, steps.size());
        assertEquals("Linsen abspülen.", steps.get(0).getText());
    }

    @Test
    void kommtMitEinemSchrittProElementZurecht() {
        var steps = parse("""
                <div itemscope itemtype="https://schema.org/Recipe">
                  <h1 itemprop="name">Rührei</h1>
                  <span itemprop="recipeIngredient">3 Eier</span>
                  <p itemprop="recipeInstructions">Eier verquirlen.</p>
                  <p itemprop="recipeInstructions">In der Pfanne stocken lassen.</p>
                </div>
                """).getSteps();

        assertEquals(2, steps.size());
        assertEquals("In der Pfanne stocken lassen.", steps.get(1).getText());
    }

    @Test
    void nimmtDieUeberschriftWennDerNameFehlt() {
        RecipeDTO recipe = parse("""
                <html><body><h1>Bratkartoffeln</h1>
                <div itemscope itemtype="https://schema.org/Recipe">
                  <span itemprop="recipeIngredient">500 g Kartoffeln</span>
                  <p itemprop="recipeInstructions">Braten.</p>
                </div></body></html>
                """);

        assertEquals("Bratkartoffeln", recipe.getName());
    }

    @Test
    void eineSeiteOhneMicrodataGibtNichtsZurueck() {
        Optional<RecipeImportPreviewDTO> preview = parser.tryParse(
                Jsoup.parse("<html><body><h1>Übersicht</h1></body></html>"),
                "https://kochblog.example/", "kochblog.example");

        assertTrue(preview.isEmpty());
    }

    // Ein leeres Wurzelelement ist keine halbe Vorschau wert - dann faellt die Kette weiter auf
    // den Seitentext.
    @Test
    void einLeeresRezeptElementFaelltWeiter() {
        Optional<RecipeImportPreviewDTO> preview = parser.tryParse(
                Jsoup.parse("""
                        <div itemscope itemtype="https://schema.org/Recipe">
                          <h1 itemprop="name">Nur eine Überschrift</h1>
                        </div>
                        """),
                "https://kochblog.example/", "kochblog.example");

        assertTrue(preview.isEmpty());
    }
}
