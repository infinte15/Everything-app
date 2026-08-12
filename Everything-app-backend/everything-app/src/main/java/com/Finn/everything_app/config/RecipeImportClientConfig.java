package com.Finn.everything_app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Der HTTP-Client fuer den Rezept-Import.
 *
 * <p>Eine eigene Bean, damit die Zeitlimits nicht im Konstruktor des Importers stehen. Dort
 * haben sie schon einmal gestanden - und {@code MockRestServiceServer} haengt seine eigene
 * Request-Factory in den Builder, sodass ein {@code requestFactory(...)} im Konstruktor sie
 * wieder herauswirft. Die Tests liefen daraufhin still gegen das echte chefkoch.de.
 *
 * <p>Daraus folgt die Aufteilung hier: {@code defaultHeader(...)} ueberlebt ein {@code bindTo},
 * {@code requestFactory(...)} nicht. Deshalb sind die Kopfzeilen in Tests pruefbar - und deshalb
 * muss die Weiterleitungsverfolgung <em>eigener Code</em> sein und nicht Sache des Clients:
 * was in der Factory steckt, faellt im Test weg, und ausgerechnet diese Schleife traegt die
 * Sicherheitspruefung (siehe {@code RecipeWebFetcher}).
 *
 * <p>{@link HttpClient.Redirect#NEVER} ist kein Detail. Der frueher benutzte
 * {@code SimpleClientHttpRequestFactory} folgt Weiterleitungen still im
 * {@code HttpURLConnection} - eine {@code 302} auf {@code http://localhost:8080} liefe damit
 * am {@code SafeUrlValidator} vorbei.
 *
 * <p>Die Grenzen sind knapp, weil der Nutzer waehrenddessen vor einem offenen Sheet wartet.
 */
@Configuration
public class RecipeImportClientConfig {

    /**
     * Eine echte Browser-Kennung.
     *
     * <p>Nicht aus Koketterie: ein grosser Teil der Rezeptseiten - alles hinter Cloudflare
     * zuerst - antwortet einem {@code Java-http-client/17} mit 403.
     */
    private static final String BROWSER_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Safari/537.36";

    @Bean("recipeImportRestClient")
    public RestClient recipeImportRestClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        return builder
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, BROWSER_UA)
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "de-DE,de;q=0.9,en;q=0.8")
                .defaultHeader(HttpHeaders.ACCEPT,
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();
    }
}
