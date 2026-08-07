package com.Finn.everything_app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Der HTTP-Client fuer den Rezept-Import.
 *
 * <p>Eine eigene Bean, damit die Zeitlimits nicht im Konstruktor des Importers stehen. Dort
 * haben sie schon einmal gestanden - und {@code MockRestServiceServer} haengt seine eigene
 * Request-Factory in den Builder, sodass ein {@code requestFactory(...)} im Konstruktor sie
 * wieder herauswirft. Die Tests liefen daraufhin still gegen das echte chefkoch.de.
 *
 * <p>Die Grenzen sind knapp, weil der Nutzer waehrenddessen vor einem offenen Sheet wartet.
 */
@Configuration
public class RecipeImportClientConfig {

    @Bean("recipeImportRestClient")
    public RestClient recipeImportRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());

        return builder.requestFactory(requestFactory).build();
    }
}
