package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Die Netzseite des Imports - mit {@link MockRestServiceServer}, also ohne echtes Netz.
 *
 * <p>Der wichtigste Test hier ist {@link #weiterleitungNachInnenWirdAbgefangen()}: die
 * Weiterleitungsverfolgung ist eigener Code, damit genau das pruefbar ist. Steckte sie im
 * HTTP-Client, wuerde {@code bindTo} sie herauswerfen und niemand wuesste es.
 */
class RecipeWebFetcherTest {

    private static final String URL = "https://www.chefkoch.de/rezepte/1/x.html";

    private RecipeWebFetcher fetcher;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        // bindTo haengt seine Request-Factory in den Builder - deshalb erst danach bauen.
        RestClient.Builder builder = RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Test)")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "de-DE,de;q=0.9,en;q=0.8");
        server = MockRestServiceServer.bindTo(builder).build();
        fetcher = new RecipeWebFetcher(builder.build(), TestHosts.allPublic());
    }

    private URI uri(String value) {
        return URI.create(value);
    }

    @Test
    void holtDieSeiteUndGibtSieGeparstZurueck() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("<html><body><h1>Pfannkuchen</h1></body></html>",
                        MediaType.TEXT_HTML));

        RecipeWebFetcher.FetchedPage page = fetcher.fetch(uri(URL));

        assertEquals("Pfannkuchen", page.document().selectFirst("h1").text());
        assertEquals(URL, page.finalUrl().toString());
        server.verify();
    }

    // Die Kopfzeilen sitzen auf dem Builder und nicht auf der Request-Factory - nur deshalb
    // ueberleben sie bindTo und sind hier ueberhaupt pruefbar.
    @Test
    void schicktEineBrowserKennungMit() {
        server.expect(requestTo(URL))
                .andExpect(header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Test)"))
                .andExpect(header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE,de;q=0.9,en;q=0.8"))
                .andRespond(withSuccess("<html><body>x</body></html>", MediaType.TEXT_HTML));

        fetcher.fetch(uri(URL));
        server.verify();
    }

    @Test
    void folgtEinerWeiterleitungNachAussen() {
        String zwei = "https://www.chefkoch.de/rezepte/2/neu.html";
        server.expect(requestTo(URL)).andRespond(
                withStatus(HttpStatus.MOVED_PERMANENTLY).header(HttpHeaders.LOCATION, zwei));
        server.expect(requestTo(zwei)).andRespond(
                withSuccess("<html><body><h1>Neu</h1></body></html>", MediaType.TEXT_HTML));

        RecipeWebFetcher.FetchedPage page = fetcher.fetch(uri(URL));

        // Die Herkunft muss die Adresse sein, unter der die Seite wirklich lag.
        assertEquals(zwei, page.finalUrl().toString());
        server.verify();
    }

    @Test
    void loestRelativeWeiterleitungenAuf() {
        server.expect(requestTo(URL)).andRespond(
                withStatus(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/rezepte/3/x.html"));
        server.expect(requestTo("https://www.chefkoch.de/rezepte/3/x.html")).andRespond(
                withSuccess("<html><body>x</body></html>", MediaType.TEXT_HTML));

        assertEquals("https://www.chefkoch.de/rezepte/3/x.html",
                fetcher.fetch(uri(URL)).finalUrl().toString());
        server.verify();
    }

    /**
     * Der eigentliche Grund fuer {@code Redirect.NEVER} und die Schleife von Hand.
     *
     * <p>Ein Client, der still folgt, traegt den Server auf dem Silbertablett nach innen: die
     * erste Adresse ist harmlos, die zweite nicht - und geprueft wurde nur die erste.
     */
    @Test
    void weiterleitungNachInnenWirdAbgefangen() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "http://127.0.0.1:8080/api/recipes"));

        assertThrows(BadRequestException.class, () -> fetcher.fetch(uri(URL)));

        // Genau eine Anfrage: die zweite wurde nie gestellt.
        server.verify();
    }

    @Test
    void weiterleitungAufEinAnderesSchemaWirdAbgefangen() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "file:///etc/passwd"));

        assertThrows(BadRequestException.class, () -> fetcher.fetch(uri(URL)));
        server.verify();
    }

    @Test
    void eineSchleifeAusWeiterleitungenBrichtAb() {
        for (int i = 0; i <= RecipeWebFetcher.MAX_REDIRECTS; i++) {
            server.expect(requestTo(URL)).andRespond(
                    withStatus(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URL));
        }

        BadRequestException thrown =
                assertThrows(BadRequestException.class, () -> fetcher.fetch(uri(URL)));
        assertTrue(thrown.getMessage().contains("leitet immer weiter"), thrown.getMessage());
    }

    @Test
    void zuGrosseAntwortenWerdenNichtGelesen() {
        String riesig = "<html><body>" + "x".repeat(RecipeWebFetcher.MAX_RESPONSE_BYTES + 1024)
                + "</body></html>";
        server.expect(requestTo(URL)).andRespond(withSuccess(riesig, MediaType.TEXT_HTML));

        BadRequestException thrown =
                assertThrows(BadRequestException.class, () -> fetcher.fetch(uri(URL)));
        assertTrue(thrown.getMessage().contains("zu groß"), thrown.getMessage());
    }

    @Test
    void einBildIstKeinRezept() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("nicht wirklich ein Bild", MediaType.IMAGE_JPEG));

        BadRequestException thrown =
                assertThrows(BadRequestException.class, () -> fetcher.fetch(uri(URL)));
        assertTrue(thrown.getMessage().contains("kein Rezept"), thrown.getMessage());
    }

    // Der haeufigste echte Fehlschlag: Cloudflare oder eine Bezahlschranke. Die Meldung muss auf
    // den Weg zeigen, der trotzdem funktioniert - sonst steht der Nutzer davor.
    @Test
    void eineSperreZeigtAufDenTextWeg() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        BadRequestException thrown =
                assertThrows(BadRequestException.class, () -> fetcher.fetch(uri(URL)));
        assertTrue(thrown.getMessage().contains("Text einfügen"), thrown.getMessage());
    }

    @Test
    void einAusfallWirdZumLesbarenHinweisMitHost() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        BadRequestException thrown =
                assertThrows(BadRequestException.class, () -> fetcher.fetch(uri(URL)));
        assertTrue(thrown.getMessage().startsWith("www.chefkoch.de hat nicht geantwortet"),
                thrown.getMessage());
    }

    @Test
    void eineFehlendeSeiteWirdBenannt() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        BadRequestException thrown =
                assertThrows(BadRequestException.class, () -> fetcher.fetch(uri(URL)));
        assertTrue(thrown.getMessage().contains("keine Seite"), thrown.getMessage());
    }

    @Test
    void eineLeereAntwortIstKeineSeite() {
        server.expect(requestTo(URL)).andRespond(withSuccess("", MediaType.TEXT_HTML));

        assertThrows(BadRequestException.class, () -> fetcher.fetch(uri(URL)));
    }

    /**
     * Deutsche Blogs auf altem WordPress liefern bis heute ISO-8859-1 ohne Angabe in der
     * Kopfzeile. Wer dann UTF-8 annimmt, bekommt aus "Löffel" Buchstabensalat - und der
     * Zutatenzerleger findet keine Einheit mehr.
     */
    @Test
    void erkenntDieKodierungAusDemMetaTag() {
        String html = "<html><head><meta charset=\"ISO-8859-1\"></head>"
                + "<body><p>2 Löffel Zucker</p></body></html>";
        server.expect(requestTo(URL)).andRespond(
                withSuccess(html.getBytes(StandardCharsets.ISO_8859_1),
                        new MediaType("text", "html")));

        RecipeWebFetcher.FetchedPage page = fetcher.fetch(uri(URL));

        assertTrue(page.document().text().contains("Löffel"), page.document().text());
    }
}
