package com.Finn.everything_app.controller;

import com.Finn.everything_app.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Die Gegenrichtung zu {@link AuthEndpointLockdownTest}: mit beiden Schaltern auf {@code true}
 * funktionieren Dev-Login und Registrierung wie im Entwicklungsbetrieb.
 *
 * <p>Ohne diesen Test koennte der Lockdown-Test auch dann gruen sein, wenn die Pfade aus einem
 * ganz anderen Grund kaputt waeren - er wuerde nur nicht mehr belegen, dass der Schalter die
 * Ursache ist. Zusammen zeigen beide: der Zustand haengt genau an der Property.
 *
 * <p>Eigener Kontext wegen {@code properties} - Spring cacht ihn getrennt vom Standardkontext
 * der uebrigen Tests.
 */
@SpringBootTest(properties = {
        "app.dev-login.enabled=true",
        "app.registration.enabled=true"
})
@AutoConfigureMockMvc
class AuthEndpointsEnabledTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtil jwtUtil;

    @Test
    void devLoginLiefertMitSchalterEinToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/dev-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("dev_tester"))
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();

        // Nicht nur "irgendein String": das Token muss auch die Filterkette passieren.
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void registrierungLegtMitSchalterAn() throws Exception {
        String body = """
                {"username":"neuling_test","email":"neuling@test.local","password":"geheim123"}
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("neuling_test"))
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
        assertEquals("neuling_test", jwtUtil.extractUsername(token));
    }
}
