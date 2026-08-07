package com.Finn.everything_app.service.recipe;

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
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Nur die Netzseite des Imports - mit {@link MockRestServiceServer} wie im
 * {@code EnableBankingClientTest}. Was aus dem JSON wird, prueft
 * {@link RecipeJsonLdParserTest}.
 */
class ChefkochImporterTest {

    private static final String URL =
            "https://www.chefkoch.de/rezepte/1208161226570428/Der-perfekte-Pfannkuchen.html";

    private ChefkochImporter importer;
    private MockRestServiceServer server;
    private String fixtureJson;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        // bindTo haengt seine Request-Factory in den Builder - deshalb erst danach bauen,
        // und deshalb konfiguriert der Importer seine Zeitlimits nicht mehr selbst.
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        importer = new ChefkochImporter(builder.build(), objectMapper,
                new RecipeJsonLdParser(objectMapper, new IngredientParser()));

        fixtureJson = new String(
                new ClassPathResource("chefkoch/pfannkuchen.json").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private String pageWith(String json) {
        return "<html><head><script type=\"application/ld+json\">" + json
                + "</script></head><body>egal</body></html>";
    }

    @Test
    void holtDieSeiteUndGibtDasRezeptZurueck() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(pageWith(fixtureJson), MediaType.TEXT_HTML));

        RecipeImportPreviewDTO preview = importer.importFrom(URL);

        assertNotNull(preview.getRecipe());
        assertEquals("Der perfekte Pfannkuchen - gelingt einfach immer", preview.getRecipe().getName());
        server.verify();
    }

    // Die Whitelist ist eine Sicherheitsanforderung: ein angemeldeter Nutzer koennte den
    // Server sonst beliebige Adressen abrufen lassen - auch interne.
    @Test
    void fremdeHostsWerdenAbgelehntOhneSieAbzurufen() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> importer.importFrom("https://example.com/rezept"));

        assertTrue(thrown.getMessage().contains("chefkoch.de"), thrown.getMessage());
        // Keine einzige Anfrage - die Pruefung greift vor dem Abruf.
        server.verify();
    }

    @Test
    void interneAdressenKommenGarNichtErstDurch() {
        for (String url : java.util.List.of(
                "http://localhost:8080/actuator",
                "http://127.0.0.1:5432",
                "file:///etc/passwd",
                "https://chefkoch.de.angreifer.example/x")) {
            assertThrows(BadRequestException.class, () -> importer.importFrom(url), url);
        }
        server.verify();
    }

    @Test
    void keineAdresseIstEinKlarerFehler() {
        assertThrows(BadRequestException.class, () -> importer.importFrom(null));
        assertThrows(BadRequestException.class, () -> importer.importFrom("  "));
        assertThrows(BadRequestException.class, () -> importer.importFrom("kein url"));
    }

    // Ein Ausfall bei chefkoch ist kein Serverfehler bei uns - der Nutzer bekommt einen
    // deutschen Satz und darf es spaeter erneut versuchen.
    @Test
    void einAusfallWirdZumLesbarenHinweis() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        BadRequestException thrown =
                assertThrows(BadRequestException.class, () -> importer.importFrom(URL));

        assertTrue(thrown.getMessage().contains("chefkoch.de hat nicht geantwortet"),
                thrown.getMessage());
    }

    @Test
    void eineSeiteOhneRezeptBlockWirdBenannt() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("<html><body>Übersicht</body></html>", MediaType.TEXT_HTML));

        BadRequestException thrown =
                assertThrows(BadRequestException.class, () -> importer.importFrom(URL));

        assertTrue(thrown.getMessage().contains("kein Rezept"), thrown.getMessage());
    }

    // Eine Seite traegt mehrere ld+json-Bloecke (Organisation, Brotkrumen). Genommen wird
    // der, in dem ein Rezept steckt - nicht einfach der erste.
    @Test
    void findetDenRezeptBlockZwischenAnderen() {
        String page = "<html><head>"
                + "<script type=\"application/ld+json\">{\"@type\":\"Organization\",\"name\":\"Chefkoch\"}</script>"
                + "<script type=\"application/ld+json\">" + fixtureJson + "</script>"
                + "</head></html>";
        server.expect(requestTo(URL)).andRespond(withSuccess(page, MediaType.TEXT_HTML));

        RecipeImportPreviewDTO preview = importer.importFrom(URL);

        assertTrue(preview.getRecipe().getName().startsWith("Der perfekte Pfannkuchen"));
    }

    @Test
    void eineLeereAntwortIstKeinRezept() {
        server.expect(requestTo(URL)).andRespond(withSuccess("", MediaType.TEXT_HTML));

        assertThrows(BadRequestException.class, () -> importer.importFrom(URL));
    }
}
