package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.dto.RecipeImportPreviewDTO;
import com.Finn.everything_app.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Der Instagram-Abruf - eine Abkuerzung mit eingebautem Fehlschlag.
 *
 * <p>Die Haelfte dieser Tests prueft nicht, dass es klappt, sondern dass es <em>sauber</em> nicht
 * klappt: Instagram zeigt einem Server meist eine Anmeldeseite, und aus einer Sperre eine
 * ueberzeugend aussehende, halbleere Vorschau zu bauen waere schlimmer als die ehrliche Meldung.
 */
class InstagramImporterTest {

    private static final String POST = "https://www.instagram.com/p/Cx1y2z3AbCd/";

    private InstagramImporter importer;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        IngredientParser ingredientParser = new IngredientParser();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        importer = new InstagramImporter(
                new RecipeWebFetcher(builder.build(), TestHosts.allPublic()),
                new RecipeJsonLdParser(objectMapper, ingredientParser),
                new TextRecipeImporter(ingredientParser),
                false);
    }

    // ── Adressen ──────────────────────────────────────────────────────────────────────────

    @Test
    void erkenntInstagramAdressen() {
        assertTrue(InstagramImporter.handles(URI.create("https://www.instagram.com/p/Abc/")));
        assertTrue(InstagramImporter.handles(URI.create("https://instagram.com/reel/Abc/")));
        assertFalse(InstagramImporter.handles(URI.create("https://www.chefkoch.de/x")));
        // Kein Blankoscheck fuer alles, was "instagram.com" im Namen hat.
        assertFalse(InstagramImporter.handles(URI.create("https://instagram.com.angreifer.example/")));
    }

    @Test
    void bringtAlleBeitragsformenAufDieselbeAdresse() {
        String erwartet = "https://www.instagram.com/p/Cx1y2z3AbCd/";
        for (String form : java.util.List.of(
                "https://www.instagram.com/p/Cx1y2z3AbCd/",
                "https://www.instagram.com/reel/Cx1y2z3AbCd/?igsh=abc123",
                "https://www.instagram.com/reels/Cx1y2z3AbCd/",
                "https://www.instagram.com/tv/Cx1y2z3AbCd/",
                "https://www.instagram.com/einkoch/p/Cx1y2z3AbCd/")) {
            assertEquals(erwartet, InstagramImporter.normalize(URI.create(form)).toString(), form);
        }
    }

    // ── Der gute Fall ─────────────────────────────────────────────────────────────────────

    @Test
    void liestDieBildunterschriftAusDenEingebettetenDaten() {
        server.expect(requestTo(POST)).andRespond(withSuccess(pageWithEmbeddedCaption(),
                MediaType.TEXT_HTML));

        RecipeImportPreviewDTO preview = importer.importFrom(URI.create(POST));

        assertEquals("Ofengemüse", preview.getRecipe().getName());
        assertEquals(4, preview.getRecipe().getIngredients().size());
        assertFalse(preview.getRecipe().getSteps().isEmpty());
        assertEquals("Instagram", preview.getRecipe().getSourceName());
        assertEquals(POST, preview.getRecipe().getSourceUrl());
    }

    @Test
    void warntDassInstagramNurEinenAusschnittHerausgibt() {
        server.expect(requestTo(POST)).andRespond(withSuccess(pageWithEmbeddedCaption(),
                MediaType.TEXT_HTML));

        RecipeImportPreviewDTO preview = importer.importFrom(URI.create(POST));

        assertTrue(preview.getWarnings().stream().anyMatch(w -> w.contains("Ausschnitt")),
                preview.getWarnings().toString());
    }

    // Das Bild ist der eine Teil, den Instagram fast immer herausgibt - aber die Adressen sind
    // signiert und laufen ab. Ohne den Hinweis steht im Kochbuch spaeter ein leerer Rahmen.
    @Test
    void nimmtDasBildMitUndSagtDassEsAblaeuft() {
        server.expect(requestTo(POST)).andRespond(withSuccess(pageWithEmbeddedCaption(),
                MediaType.TEXT_HTML));

        RecipeImportPreviewDTO preview = importer.importFrom(URI.create(POST));

        assertEquals("https://cdn.example/bild.jpg", preview.getRecipe().getImageUrl());
        assertTrue(preview.getWarnings().stream().anyMatch(w -> w.contains("kurze Zeit")),
                preview.getWarnings().toString());
    }

    // ── Die Fehlschlaege ──────────────────────────────────────────────────────────────────

    @Test
    void eineAnmeldeseiteSchicktInDenTextWeg() {
        server.expect(requestTo(POST)).andRespond(withSuccess("""
                <html><head><title>Login • Instagram</title></head>
                <body><form><input name="username"></form></body></html>
                """, MediaType.TEXT_HTML));

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> importer.importFrom(URI.create(POST)));

        assertEquals(InstagramImporter.PASTE_CAPTION, thrown.getCode());
        assertTrue(thrown.getMessage().contains("Bildunterschrift"), thrown.getMessage());
    }

    @Test
    void auchEineSperreSchicktInDenTextWeg() {
        server.expect(requestTo(POST)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> importer.importFrom(URI.create(POST)));

        assertEquals(InstagramImporter.PASTE_CAPTION, thrown.getCode());
    }

    /**
     * Auf der Anmeldeseite steht in {@code og:description} die Vorschauzeile mit
     * Gefaellt-mir-Zahl und den ersten Zeichen. Sie anzunehmen hiesse, aus einer Sperre eine
     * ueberzeugende, falsche Vorschau zu bauen.
     */
    @Test
    void nimmtDieAbgeschnitteneVorschauZeileNichtAn() {
        server.expect(requestTo(POST)).andRespond(withSuccess("""
                <html><head><meta property="og:description"
                  content="1.234 likes, 56 comments - einkoch on May 3, 2026: &quot;Ofengemüse
                  mit Feta - das beste Rezept...&quot;"></head><body></body></html>
                """, MediaType.TEXT_HTML));

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> importer.importFrom(URI.create(POST)));
        assertEquals(InstagramImporter.PASTE_CAPTION, thrown.getCode());
    }

    // Text ja, Rezept nein - dann ist der Hinweis besser als eine Vorschau mit einer Zutat.
    @Test
    void einBeitragOhneRezeptWirdNichtZumRezeptGeraten() {
        server.expect(requestTo(POST)).andRespond(withSuccess(pageWithCaption(
                "Was für ein Wochenende! Danke an alle, die da waren. Wir sehen uns nächsten "
                        + "Monat wieder, dann mit neuem Programm und hoffentlich besserem Wetter "
                        + "als diesmal. Bis dahin: passt auf euch auf und esst was Anständiges."),
                MediaType.TEXT_HTML));

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> importer.importFrom(URI.create(POST)));
        assertEquals(InstagramImporter.PASTE_CAPTION, thrown.getCode());
    }

    @Test
    void loestJsonFluchtzeichenAuf() {
        assertEquals("Zeile 1\nZeile 2", InstagramImporter.unescapeJson("Zeile 1\\nZeile 2"));
        assertEquals("Ofengemüse", InstagramImporter.unescapeJson("Ofengem\\u00fcse"));
        assertEquals("\"Zitat\"", InstagramImporter.unescapeJson("\\\"Zitat\\\""));
    }

    // ── Hilfen ────────────────────────────────────────────────────────────────────────────

    private static final String CAPTION = """
            Ofengemüse
            Zutaten:
            400 g Kartoffeln
            2 Paprika
            200 g Feta
            3 EL Olivenöl
            Zubereitung:
            Ofen auf 200 Grad vorheizen.
            Gemüse würfeln und mit Öl mischen.
            30 Minuten backen, Feta darüber.
            #rezept #ofengemuese""";

    private String pageWithEmbeddedCaption() {
        return pageWithCaption(CAPTION);
    }

    private String pageWithCaption(String caption) {
        String escaped = caption.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "<html><head>"
                + "<meta property=\"og:image\" content=\"https://cdn.example/bild.jpg\">"
                + "</head><body><script type=\"text/javascript\">"
                + "window.__d=({\"edge_media_to_caption\":{\"edges\":[{\"node\":{\"text\":\""
                + escaped + "\"}}]}});"
                + "</script></body></html>";
    }
}
