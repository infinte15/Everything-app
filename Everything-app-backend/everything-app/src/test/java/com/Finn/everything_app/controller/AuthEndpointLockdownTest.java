package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Nagelt die Produktionsstellung der Auth-Schalter fest: {@code app.dev-login.enabled} und
 * {@code app.registration.enabled} stehen hier - wie im Prod-Profil - auf {@code false}.
 *
 * <p>Der Testkontext braucht dafuer nichts zu setzen: beide Properties fehlen in
 * {@code src/test/resources/application.properties}, und die {@code @Value}-Vorgaben in
 * {@link com.Finn.everything_app.security.SecurityConfig} und {@link AuthController} sind
 * {@code false}. Genau diese Vorgabe ist das Sicherheitsnetz - wuerde sie jemand auf
 * {@code true} drehen, faellt es hier auf und nicht erst auf dem Server.
 *
 * <p>Die Gegenrichtung - beide Schalter an, beide Pfade funktionieren - steht in
 * {@link AuthEndpointsEnabledTest}. Nur beide Tests zusammen zeigen, dass die Pfade
 * tatsaechlich am Schalter haengen und nicht aus einem anderen Grund zu sind.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthEndpointLockdownTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApplicationContext context;

    /**
     * Zwei Aussagen in einer: der Pfad ist zu, und der Controller existiert gar nicht erst als
     * Bean. Das zweite ist der eigentliche Schutz - ohne Bean gibt es keinen Handler, den eine
     * spaetere Aenderung an der Filterkette versehentlich wieder erreichbar machen koennte.
     */
    @Test
    void devLoginIstOhneSchalterZu() throws Exception {
        mockMvc.perform(post("/api/auth/dev-login"))
                .andExpect(status().isForbidden());

        assertTrue(context.getBeansOfType(DevAuthController.class).isEmpty(),
                "DevAuthController darf ohne app.dev-login.enabled=true nicht im Kontext liegen");
    }

    /**
     * Die Registrierung hat zwei Sperren: SecurityConfig nimmt den Pfad nicht in die
     * permitAll-Liste auf, und der Controller antwortet zusaetzlich mit 403. Hier greift die
     * erste - die Anfrage kommt am Controller nie an.
     */
    @Test
    void registrierungIstOhneSchalterVerboten() throws Exception {
        String body = """
                {"username":"eindringling","email":"eindringling@test.local","password":"geheim123"}
                """;

        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    /**
     * Der Gegencheck zu allem anderen: /api/auth/login bleibt offen, sonst kaeme niemand mehr
     * an ein Token. Falsche Zugangsdaten muessen 401 liefern - 403 waere das Zeichen, dass der
     * Pfad aus der permitAll-Liste gefallen ist und die Antwort von der Filterkette kommt.
     */
    @Test
    void loginBleibtErreichbar() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("gibt_es_nicht");
        request.setPassword("auch_nicht");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /** anyRequest().authenticated() gilt weiterhin fuer die fachlichen Endpunkte. */
    @Test
    void fachlicheEndpunkteBrauchenWeiterhinEinToken() throws Exception {
        mockMvc.perform(get("/api/tasks")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/calendar/events")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/finance/transactions")).andExpect(status().isForbidden());
    }

    /**
     * Von aussen darf der abgeschaltete Dev-Login nicht von einem Pfad zu unterscheiden sein,
     * den es nie gab. Waere die Antwort unterschiedlich, verriete allein der Status, dass es
     * diesen Endpunkt in anderen Umgebungen gibt.
     */
    @Test
    void unbekanntePfadeAntwortenGenausoWieDevLogin() throws Exception {
        int devLogin = mockMvc.perform(post("/api/auth/dev-login"))
                .andReturn().getResponse().getStatus();
        int erfunden = mockMvc.perform(post("/api/auth/gibt-es-nicht"))
                .andReturn().getResponse().getStatus();

        assertEquals(erfunden, devLogin,
                "Abgeschalteter Dev-Login muss sich wie ein unbekannter Pfad verhalten");
    }
}
