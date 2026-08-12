package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;

/**
 * Holt eine Seite - und nur eine Seite, die geholt werden darf.
 *
 * <p>Vom Zerlegen getrennt, weil das Interessante am Import nicht das Herunterladen ist. Hier
 * steht nur, was schiefgehen kann: Weiterleitungen, Groesse, Kodierung, Sperren.
 *
 * <p><b>Weiterleitungen werden von Hand verfolgt.</b> Das ist der Grund, warum der Client auf
 * {@code Redirect.NEVER} steht: nur so laesst sich <em>jeder</em> Sprung erneut durch den
 * {@link SafeUrlValidator} schicken. Eine Seite, die auf {@code http://127.0.0.1:8080} verweist,
 * ist sonst genau die Luecke, die der Validator schliessen soll - nur eine Umleitung weiter.
 * Weil die Schleife eigener Code ist, laesst sie sich ausserdem mit
 * {@code MockRestServiceServer} pruefen; steckte sie im Client, waere sie es nicht.
 */
@Component
@Slf4j
public class RecipeWebFetcher {

    /** Eine geholte Seite samt der Adresse, unter der sie am Ende wirklich lag. */
    public record FetchedPage(URI finalUrl, Document document) {}

    static final int MAX_REDIRECTS = 5;

    /**
     * Obergrenze fuer das, was ueberhaupt gelesen wird.
     *
     * <p>Der Deckel greift <em>waehrend</em> des Lesens, nicht danach: sonst ist die Grenze nur
     * eine Aussage ueber den Parser, waehrend der Speicher laengst vollgelaufen ist.
     */
    static final int MAX_RESPONSE_BYTES = 3 * 1024 * 1024;

    /** Gesamtfrist ueber alle Spruenge. Die App wartet bei 30 s vor einem offenen Sheet. */
    private static final Duration TOTAL_BUDGET = Duration.ofSeconds(20);

    private static final List<MediaType> READABLE = List.of(
            MediaType.TEXT_HTML,
            MediaType.APPLICATION_XHTML_XML,
            MediaType.APPLICATION_JSON,
            MediaType.TEXT_PLAIN);

    private final RestClient restClient;
    private final SafeUrlValidator validator;

    public RecipeWebFetcher(@Qualifier("recipeImportRestClient") RestClient restClient,
                            SafeUrlValidator validator) {
        this.restClient = restClient;
        this.validator = validator;
    }

    public FetchedPage fetch(URI startUri) {
        return fetch(startUri, null);
    }

    /**
     * Wie {@link #fetch(URI)}, nur mit anderer Kennung.
     *
     * <p>Gibt es allein fuer Instagram: siehe {@code InstagramImporter}. Sonst gilt die
     * Browser-Kennung aus {@code RecipeImportClientConfig}.
     */
    public FetchedPage fetch(URI startUri, String userAgentOverride) {
        long deadline = System.nanoTime() + TOTAL_BUDGET.toNanos();
        URI current = startUri;

        for (int hop = 0; ; hop++) {
            if (hop > MAX_REDIRECTS) {
                throw new BadRequestException(
                        "Diese Adresse leitet immer weiter. Bitte den direkten Link zum Rezept nehmen.");
            }
            if (System.nanoTime() > deadline) {
                throw new BadRequestException(
                        hostOf(current) + " antwortet zu langsam. Später noch mal versuchen.");
            }

            // Auf jedem Sprung neu - nicht nur beim ersten.
            validator.assertPublicHost(current.getHost());

            Hop result = request(current, userAgentOverride);
            if (result.redirect() == null) {
                return new FetchedPage(current, result.document());
            }
            current = nextHop(current, result.redirect());
        }
    }

    /** Ergebnis eines einzelnen Sprungs: entweder eine Seite oder ein Verweis weiter. */
    private record Hop(Document document, String redirect) {}

    private Hop request(URI uri, String userAgentOverride) {
        try {
            // uri(URI) und nicht uri(String): ein String liefe durch die Platzhalter-Ersetzung
            // und zerbraeche an jedem "{" in der Adresse.
            RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(uri);
            if (userAgentOverride != null && !userAgentOverride.isBlank()) {
                spec = spec.header(HttpHeaders.USER_AGENT, userAgentOverride);
            }
            return spec.exchange((request, response) -> handle(uri, response));
        } catch (BadRequestException e) {
            throw e;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("Rezept-Import von {} fehlgeschlagen", uri, e);
            throw new BadRequestException(
                    hostOf(uri) + " hat nicht geantwortet. Später noch mal versuchen.");
        }
    }

    private Hop handle(URI uri, org.springframework.http.client.ClientHttpResponse response)
            throws IOException {

        HttpStatusCode status = response.getStatusCode();
        HttpHeaders headers = response.getHeaders();

        if (status.is3xxRedirection()) {
            String location = headers.getFirst(HttpHeaders.LOCATION);
            if (location == null || location.isBlank()) {
                throw new BadRequestException(
                        hostOf(uri) + " hat nicht geantwortet. Später noch mal versuchen.");
            }
            return new Hop(null, location);
        }

        if (status.isError()) {
            throw new BadRequestException(messageFor(uri, status));
        }

        MediaType contentType = headers.getContentType();
        if (contentType != null && READABLE.stream().noneMatch(t -> t.includes(contentType))) {
            throw new BadRequestException(
                    "Unter dieser Adresse steckt kein Rezept, sondern " + contentType.getType()
                            + "/" + contentType.getSubtype() + ".");
        }

        long declared = headers.getContentLength();
        if (declared > MAX_RESPONSE_BYTES) {
            throw new BadRequestException("Die Seite ist zu groß - das ist wohl kein Rezept.");
        }

        byte[] body = readCapped(response.getBody());
        if (body.length == 0) {
            throw new BadRequestException(
                    hostOf(uri) + " hat nichts zurückgegeben. Später noch mal versuchen.");
        }

        // Kodierung: was die Kopfzeile sagt, gilt. Sagt sie nichts, schnueffelt jsoup selbst im
        // <meta charset> - und das ist der haeufigere Fall. Deutsche Blogs auf altem WordPress
        // liefern bis heute ISO-8859-1 ohne Kopfzeile, und aus "Löffel" wird sonst Buchstabensalat,
        // an dem die Zutatenzerlegung scheitert.
        Charset declaredCharset = contentType == null ? null : contentType.getCharset();
        String charsetName = declaredCharset == null ? null : declaredCharset.name();

        Document document = Jsoup.parse(
                new ByteArrayInputStream(body), charsetName, uri.toString());
        return new Hop(document, null);
    }

    private String messageFor(URI uri, HttpStatusCode status) {
        int code = status.value();
        if (code == 404 || code == 410) {
            return "Unter dieser Adresse liegt keine Seite.";
        }
        if (code == 401 || code == 403 || code == 429) {
            // Der haeufigste echte Fehlschlag: Cloudflare oder eine Bezahlschranke. Die Meldung
            // zeigt deshalb auf den Weg, der trotzdem funktioniert.
            return "Die Seite lässt sich nicht lesen - sie sperrt automatische Zugriffe aus. "
                    + "Kopier den Text und füg ihn unter „Text einfügen\" ein.";
        }
        return hostOf(uri) + " hat nicht geantwortet. Später noch mal versuchen.";
    }

    /** Liest hoechstens {@link #MAX_RESPONSE_BYTES} und bricht ab, sobald es mehr wird. */
    private byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) {
                throw new BadRequestException("Die Seite ist zu groß - das ist wohl kein Rezept.");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * Loest den {@code Location}-Wert gegen die aktuelle Adresse auf.
     *
     * <p>Relative Verweise ({@code /rezept/123}) sind erlaubt und haeufig; {@code URI#resolve}
     * macht daraus die vollstaendige Adresse. Danach geht sie durch dieselbe Formpruefung wie
     * die urspruengliche Eingabe - ein {@code Location: file:///etc/passwd} ist damit erledigt,
     * bevor irgendetwas aufgeloest wird.
     */
    private URI nextHop(URI current, String location) {
        URI target;
        try {
            target = current.resolve(location.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Diese Adresse leitet auf etwas Unlesbares weiter.");
        }
        return validator.parse(target.toString());
    }

    private String hostOf(URI uri) {
        String host = uri.getHost();
        return host == null ? "Die Seite" : host;
    }
}
