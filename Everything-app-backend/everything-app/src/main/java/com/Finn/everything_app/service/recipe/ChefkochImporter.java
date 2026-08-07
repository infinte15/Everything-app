package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.dto.RecipeImportPreviewDTO;
import com.Finn.everything_app.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holt eine chefkoch.de-Rezeptseite und gibt ihren Inhalt als Rezept-Vorschau zurueck.
 *
 * <p>{@code RestClient} wie im {@code EnableBankingClient} - er kommt mit
 * {@code spring-boot-starter-web} und kostet keine Abhaengigkeit. Auch kein jsoup: gesucht wird
 * genau ein {@code <script type="application/ld+json">}, und dafuer eine HTML-Bibliothek
 * einzubinden waere unverhaeltnismaessig. Alles Weitere macht {@link RecipeJsonLdParser} auf
 * dem geparsten JSON, nicht auf dem HTML.
 *
 * <p><b>Die Beschraenkung auf chefkoch.de ist eine Sicherheitsanforderung, keine
 * Hoeflichkeit.</b> Ein angemeldeter Nutzer, der dem Server eine beliebige Adresse zum Abrufen
 * gibt, laesst ihn in jedes Netz greifen, das der Server erreicht - {@code localhost:8080}, die
 * Datenbank, alles im selben Subnetz. Deshalb: fester Host, kein Folgen auf fremde Hosts,
 * knappe Zeitlimits und ein Deckel auf der Antwortgroesse.
 */
@Component
@Slf4j
public class ChefkochImporter {

    /**
     * Obergrenze fuer das, was geparst wird. Die echte Seite hat rund 320 KB.
     *
     * <p>Der Deckel greift nach dem Lesen und verhindert den Download also nicht - das tut das
     * Lese-Zeitlimit. Er verhindert, dass die Regex-Suche und Jackson auf etwas losgelassen
     * werden, das kein Rezept mehr sein kann.
     */
    static final int MAX_RESPONSE_CHARS = 2 * 1024 * 1024;

    private static final List<String> ALLOWED_HOSTS = List.of("chefkoch.de", "www.chefkoch.de");

    private static final Pattern LD_JSON = Pattern.compile(
            "<script[^>]+type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RecipeJsonLdParser jsonLdParser;

    public ChefkochImporter(@Qualifier("recipeImportRestClient") RestClient restClient,
                            ObjectMapper objectMapper,
                            RecipeJsonLdParser jsonLdParser) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.jsonLdParser = jsonLdParser;
    }

    public RecipeImportPreviewDTO importFrom(String url) {
        URI uri = validate(url);
        String html = fetch(uri);
        JsonNode root = extractJsonLd(html);
        return jsonLdParser.parse(root, uri.toString(), "chefkoch.de");
    }

    /** Adresse pruefen, bevor irgendetwas abgerufen wird. */
    URI validate(String url) {
        if (url == null || url.isBlank()) {
            throw new BadRequestException("Das ist keine Web-Adresse.");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new BadRequestException("Das ist keine Web-Adresse.");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BadRequestException("Das ist keine Web-Adresse.");
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!ALLOWED_HOSTS.contains(host)) {
            throw new BadRequestException("Bisher werden nur Rezepte von chefkoch.de erkannt.");
        }
        return uri;
    }

    private String fetch(URI uri) {
        String body;
        try {
            body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("Rezept-Import von {} fehlgeschlagen", uri, e);
            throw new BadRequestException(
                    "chefkoch.de hat nicht geantwortet. Später noch mal versuchen.");
        }

        if (body == null || body.isBlank()) {
            throw new BadRequestException(
                    "chefkoch.de hat nichts zurückgegeben. Später noch mal versuchen.");
        }
        if (body.length() > MAX_RESPONSE_CHARS) {
            throw new BadRequestException("Die Seite ist zu groß - das ist wohl kein Rezept.");
        }
        return body;
    }

    /**
     * Sucht den Rezept-Block im HTML.
     *
     * <p>Eine Seite kann mehrere {@code ld+json}-Bloecke tragen (Organisation, Brotkrumen).
     * Genommen wird der erste, der sich als JSON lesen laesst und irgendwo ein
     * {@code "Recipe"} enthaelt; ueber die genaue Struktur entscheidet danach der Parser.
     */
    JsonNode extractJsonLd(String html) {
        Matcher matcher = LD_JSON.matcher(html);
        JsonNode firstReadable = null;

        while (matcher.find()) {
            String json = matcher.group(1).trim();
            try {
                JsonNode node = objectMapper.readTree(json);
                if (firstReadable == null) {
                    firstReadable = node;
                }
                if (json.contains("\"Recipe\"")) {
                    return node;
                }
            } catch (Exception e) {
                // Ein unlesbarer Block ist kein Grund aufzuhoeren - der naechste kann der
                // richtige sein.
                log.debug("ld+json-Block nicht lesbar, weiter mit dem naechsten", e);
            }
        }

        if (firstReadable != null) {
            return firstReadable;
        }
        throw new BadRequestException(
                "Auf dieser Seite steckt kein Rezept. Ist das die Rezeptseite oder eine Übersicht?");
    }
}
