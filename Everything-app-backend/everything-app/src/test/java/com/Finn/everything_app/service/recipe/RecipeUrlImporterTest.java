package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.dto.RecipeDTO;
import com.Finn.everything_app.dto.RecipeImportPreviewDTO;
import com.Finn.everything_app.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Die Kette: JSON-LD, dann Microdata, dann der Seitentext.
 *
 * <p>Geprueft wird vor allem, dass sie <em>weiterfaellt</em>. Der frueher benutzte Importer gab
 * den ersten lesbaren {@code ld+json}-Block zurueck, egal was darin stand - solange nur chefkoch
 * gelesen wurde, fiel das nicht auf. Bei beliebigen Seiten haette es jede Seite mit einem
 * {@code Organization}-Block um ihre zweite und dritte Chance gebracht.
 */
class RecipeUrlImporterTest {

    private static final String URL = "https://kochblog.example/rezepte/pfannkuchen";

    private RecipeUrlImporter importer;
    private MockRestServiceServer server;
    private String chefkochJson;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        IngredientParser ingredientParser = new IngredientParser();
        RecipeJsonLdParser jsonLdParser = new RecipeJsonLdParser(objectMapper, ingredientParser);
        TextRecipeImporter textImporter = new TextRecipeImporter(ingredientParser);
        SafeUrlValidator validator = TestHosts.allPublic();

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RecipeWebFetcher fetcher = new RecipeWebFetcher(builder.build(), validator);

        importer = new RecipeUrlImporter(
                validator,
                fetcher,
                jsonLdParser,
                new RecipeMicrodataParser(ingredientParser, jsonLdParser),
                textImporter,
                new InstagramImporter(fetcher, jsonLdParser, textImporter, false));

        chefkochJson = new String(
                new ClassPathResource("chefkoch/pfannkuchen.json").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private void respondWith(String html) {
        server.expect(requestTo(URL)).andRespond(withSuccess(html, MediaType.TEXT_HTML));
    }

    // ── Stufe 1: JSON-LD ──────────────────────────────────────────────────────────────────

    @Test
    void liestEinRezeptAusJsonLd() {
        respondWith("<html><head><script type=\"application/ld+json\">" + chefkochJson
                + "</script></head><body>egal</body></html>");

        RecipeDTO recipe = importer.importFrom(URL).getRecipe();

        assertTrue(recipe.getName().startsWith("Der perfekte Pfannkuchen"), recipe.getName());
        assertEquals(6, recipe.getIngredients().size());
    }

    // Der Name der Quelle kommt jetzt aus der Adresse und steht nicht mehr fest im Code.
    @Test
    void leitetDenQuellnamenAusDerAdresseAb() {
        respondWith("<html><head><script type=\"application/ld+json\">" + chefkochJson
                + "</script></head></html>");

        RecipeDTO recipe = importer.importFrom(URL).getRecipe();

        assertEquals("kochblog.example", recipe.getSourceName());
        assertEquals(URL, recipe.getSourceUrl());
    }

    // Auf einer fremden Seite darf " von Oma" nicht abgeschnitten werden - das ist Teil des
    // Titels und kein Nutzername.
    @Test
    void schneidetAufFremdenSeitenNichtsVomTitelAb() {
        respondWith(pageWithJsonLd("""
                {"@context":"https://schema.org","@type":"Recipe",
                 "name":"Kartoffelsalat von Oma",
                 "recipeIngredient":["500 g Kartoffeln","1 Zwiebel","3 EL Essig"],
                 "recipeInstructions":[{"@type":"HowToStep","text":"Kartoffeln kochen."},
                                       {"@type":"HowToStep","text":"Alles mischen."}]}
                """));

        assertEquals("Kartoffelsalat von Oma", importer.importFrom(URL).getRecipe().getName());
    }

    // Was WordPress-Rezept-Erweiterungen tatsaechlich liefern: HTML im Schritttext, @type als
    // Liste, recipeCategory als Array, "30 mins" statt PT30M.
    @Test
    void kommtMitDemZeugZurechtDasWordPressLiefert() {
        respondWith(pageWithJsonLd("""
                {"@context":"https://schema.org","@type":["Recipe","NewsArticle"],
                 "name":"Ofengem\\u00fcse",
                 "recipeCategory":["Dinner","Main Course"],
                 "prepTime":"15 mins","cookTime":"1 hour 15 minutes",
                 "recipeYield":["6","6 servings"],
                 "recipeIngredient":["<span>400 g</span> Kartoffeln","2 Paprika","3 EL \\u00d6l"],
                 "recipeInstructions":[
                   {"@type":"HowToStep","text":"<p>Den Ofen auf 200\\u00b0C <b>vorheizen</b>.</p>"},
                   {"@type":"HowToStep","name":"Gem\\u00fcse w\\u00fcrfeln."}]}
                """));

        RecipeDTO recipe = importer.importFrom(URL).getRecipe();

        assertEquals("Hauptgericht", recipe.getCategory());
        assertEquals(15, recipe.getPrepTimeMinutes());
        assertEquals(75, recipe.getCookTimeMinutes());
        assertEquals(6, recipe.getServings());
        assertEquals("Den Ofen auf 200°C vorheizen.", recipe.getSteps().get(0).getText());
        // Ein Schritt, der nur einen Namen hat, ist trotzdem ein Schritt.
        assertEquals("Gemüse würfeln.", recipe.getSteps().get(1).getText());
        assertEquals("Kartoffeln", recipe.getIngredients().get(0).getName());
    }

    // ── Weiterfallen ──────────────────────────────────────────────────────────────────────

    @Test
    void faelltUeberEinenOrganisationsBlockHinwegAufMicrodata() {
        respondWith("<html><head>"
                + "<script type=\"application/ld+json\">"
                + "{\"@type\":\"Organization\",\"name\":\"Kochblog\"}</script>"
                + "</head><body>" + microdataBody() + "</body></html>");

        RecipeDTO recipe = importer.importFrom(URL).getRecipe();

        assertEquals("Linsensuppe", recipe.getName());
        assertEquals(3, recipe.getIngredients().size());
    }

    @Test
    void faelltGanzOhneAuszeichnungAufDenSeitentext() {
        respondWith("""
                <html><head><meta property="og:image" content="https://kochblog.example/bild.jpg">
                </head><body>
                <nav>Startseite Rezepte Kontakt</nav>
                <h1>Tomatensuppe</h1>
                <p>Zutaten:</p>
                <ul><li>800 g Tomaten</li><li>1 Zwiebel</li><li>2 EL Olivenöl</li>
                    <li>200 ml Brühe</li></ul>
                <p>Zubereitung:</p>
                <ol><li>Zwiebel würfeln und andünsten.</li>
                    <li>Tomaten zugeben und 20 Minuten köcheln.</li>
                    <li>Pürieren und abschmecken.</li></ol>
                <footer>Impressum</footer></body></html>
                """);

        RecipeImportPreviewDTO preview = importer.importFrom(URL);
        RecipeDTO recipe = preview.getRecipe();

        assertEquals(4, recipe.getIngredients().size(), recipe.getIngredients().toString());
        assertEquals(3, recipe.getSteps().size(), recipe.getSteps().toString());
        assertEquals("https://kochblog.example/bild.jpg", recipe.getImageUrl());
        // Geraten ist geraten - und das muss dranstehen, und zwar zuerst.
        assertTrue(preview.getWarnings().get(0).contains("aus dem Seitentext geraten"),
                preview.getWarnings().toString());
    }

    // Ohne Schwelle findet die Heuristik auf jeder Seite etwas: Menuepunkte sind kurze Zeilen,
    // und kurze Zeilen sehen aus wie Zutaten.
    @Test
    void eineSeiteOhneRezeptWirdNichtZurVorschauGeraten() {
        respondWith("""
                <html><body>
                <h1>Nachrichten</h1>
                <ul><li>Politik</li><li>Wirtschaft</li><li>Sport</li><li>Kultur</li></ul>
                <p>Der Bundestag hat am Donnerstag über den Haushalt beraten.</p>
                </body></html>
                """);

        BadRequestException thrown =
                assertThrows(BadRequestException.class, () -> importer.importFrom(URL));
        assertTrue(thrown.getMessage().contains("kein Rezept"), thrown.getMessage());
    }

    @Test
    void interneAdressenKommenGarNichtErstDurch() {
        for (String url : java.util.List.of(
                "http://localhost:8080/actuator",
                "http://127.0.0.1:5432",
                "file:///etc/passwd")) {
            assertThrows(BadRequestException.class, () -> importer.importFrom(url), url);
        }
        // Keine einzige Anfrage - die Pruefung greift vor dem Abruf.
        server.verify();
    }

    @Test
    void keineAdresseIstEinKlarerFehler() {
        assertThrows(BadRequestException.class, () -> importer.importFrom(null));
        assertThrows(BadRequestException.class, () -> importer.importFrom("  "));
        assertThrows(BadRequestException.class, () -> importer.importFrom("kein url"));
    }

    // ── Hilfen ────────────────────────────────────────────────────────────────────────────

    private String pageWithJsonLd(String json) {
        return "<html><head><script type=\"application/ld+json\">" + json
                + "</script></head><body>egal</body></html>";
    }

    private String microdataBody() {
        return """
                <div itemscope itemtype="https://schema.org/Recipe">
                  <h1 itemprop="name">Linsensuppe</h1>
                  <meta itemprop="prepTime" content="PT10M">
                  <time itemprop="cookTime" datetime="PT40M">40 Minuten</time>
                  <span itemprop="recipeYield">4 Portionen</span>
                  <span itemprop="recipeCategory">Suppe</span>
                  <ul>
                    <li itemprop="recipeIngredient">250 g Linsen</li>
                    <li itemprop="recipeIngredient">2 Karotten</li>
                    <li itemprop="recipeIngredient">1 EL Essig</li>
                  </ul>
                  <div itemprop="recipeInstructions">
                    <ol><li>Linsen einweichen.</li><li>Alles 40 Minuten kochen.</li></ol>
                  </div>
                </div>
                """;
    }
}
