package com.Finn.everything_app.controller;

import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.UserRepository;
import com.Finn.everything_app.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nagelt fest, was Nero darf und was nicht.
 *
 * <p>Nero laeuft unter demselben Nutzer wie die App - jede Entity haengt per Fremdschluessel
 * an genau einem User, ein eigener technischer Nutzer haette also einen leeren Kalender.
 * Unterscheidbar sind die beiden allein ueber den {@code client}-Claim im Token, aus dem
 * {@code JwtAuthenticationFilter} die Rolle ableitet.
 *
 * <p>Beide Richtungen stehen hier: dass Nero an den gesperrten Pfaden auflaeuft, und dass das
 * App-Token an denselben Pfaden weiterhin durchkommt. Nur zusammen zeigen sie, dass die Sperre
 * am Claim haengt und nicht daran, dass der Pfad ohnehin zu waere.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NeroTokenScopeTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwtUtil;
    @Autowired UserRepository userRepository;

    private String appToken;
    private String neroToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.findByUsername("nero_scope_test").orElseGet(() -> {
            User fresh = new User();
            fresh.setUsername("nero_scope_test");
            fresh.setEmail("nero_scope_test@example.invalid");
            fresh.setPasswordHash("{noop}egal");
            return userRepository.save(fresh);
        });

        appToken = jwtUtil.generateToken(user.getUsername(), user.getId());
        neroToken = jwtUtil.generateToken(
                user.getUsername(), user.getId(), JwtUtil.CLIENT_NERO, Duration.ofDays(365).toMillis());
    }

    /** Bestandstoken tragen keinen client-Claim - sie muessen weiter als App gelten. */
    @Test
    void tokenOhneClientClaimGiltAlsApp() {
        assertEquals(JwtUtil.CLIENT_APP, jwtUtil.extractClient(appToken));
        assertEquals(JwtUtil.CLIENT_NERO, jwtUtil.extractClient(neroToken));
    }

    @Test
    void neroDarfLesen() throws Exception {
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + neroToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + neroToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/study/goals").header("Authorization", "Bearer " + neroToken))
                .andExpect(status().isOk());
    }

    @Test
    void neroDarfAufgabenAnlegenUndAbhaken() throws Exception {
        String created = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + neroToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"Von Nero angelegt\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created).get("id").asLong();

        mockMvc.perform(put("/api/tasks/" + id + "/complete")
                        .header("Authorization", "Bearer " + neroToken))
                .andExpect(status().isOk());
    }

    /**
     * Der wichtigste Test: das Token laesst sich nicht widerrufen, nur durch Rotation von
     * jwt.secret entwerten. Deshalb darf Nero gar nicht erst loeschen koennen.
     */
    @Test
    void neroDarfNichtLoeschen() throws Exception {
        mockMvc.perform(delete("/api/tasks/1").header("Authorization", "Bearer " + neroToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/habits/1").header("Authorization", "Bearer " + neroToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void neroKommtNichtAnGeldUndKonto() throws Exception {
        mockMvc.perform(get("/api/finance/transactions")
                        .header("Authorization", "Bearer " + neroToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/user/preferences")
                        .header("Authorization", "Bearer " + neroToken))
                .andExpect(status().isForbidden());
    }

    /** Gegenprobe: dieselben Pfade bleiben fuer die App offen. */
    @Test
    void appTokenBleibtUneingeschraenkt() throws Exception {
        mockMvc.perform(get("/api/finance/transactions")
                        .header("Authorization", "Bearer " + appToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/user/preferences")
                        .header("Authorization", "Bearer " + appToken))
                .andExpect(status().isOk());
        // Eine nicht existierende Aufgabe: 403 waere die Sperre, alles andere nicht.
        mockMvc.perform(delete("/api/tasks/999999").header("Authorization", "Bearer " + appToken))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }
}
