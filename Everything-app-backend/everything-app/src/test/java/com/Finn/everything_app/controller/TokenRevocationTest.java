package com.Finn.everything_app.controller;

import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.UserRepository;
import com.Finn.everything_app.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nagelt den Notausschalter zur Token-Laufzeit fest.
 *
 * <p>{@code jwt.expiration} steht auf 30 Tage. Vertretbar ist das nur, weil ein einzelnes
 * Geraet sich aussperren laesst, ohne {@code jwt.secret} zu tauschen: {@code logout-all}
 * zaehlt {@code tokenVersion} am Nutzer hoch, und der {@code JwtAuthenticationFilter}
 * vergleicht diesen Stand bei jeder Anfrage mit dem {@code tv}-Claim des Tokens.
 *
 * <p>Der zweite Teil des Tests ist der wichtigere: nach dem Widerruf muss ein <em>frisch</em>
 * ausgestelltes Token wieder funktionieren. Ein Widerruf, der den Nutzer dauerhaft aussperrt,
 * waere kein Ausschalter, sondern ein Selbstschuss.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TokenRevocationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtUtil jwtUtil;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.findByUsername("token_revocation_test").orElseGet(() -> {
            User fresh = new User();
            fresh.setUsername("token_revocation_test");
            fresh.setEmail("token_revocation_test@test.local");
            fresh.setPasswordHash("{noop}egal");
            return userRepository.save(fresh);
        });
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(user.getId());
    }

    @Test
    void frischesTokenWirdAkzeptiert() throws Exception {
        String token = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getTokenVersion());

        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void nachLogoutAllIstDasAlteTokenWertlos() throws Exception {
        String alt = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getTokenVersion());

        // Es geht - sonst pruefte der zweite Aufruf unten nichts.
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + alt))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + alt))
                .andExpect(status().isNoContent());

        // Dasselbe Token, das eben noch ging.
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + alt))
                .andExpect(status().isForbidden());

        User nachWiderruf = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(1, nachWiderruf.getTokenVersion(),
                "logout-all muss den Widerrufs-Stand genau um eins erhoehen");

        // Neu anmelden muss wieder funktionieren.
        String frisch = jwtUtil.generateToken(nachWiderruf.getUsername(), nachWiderruf.getId(),
                nachWiderruf.getTokenVersion());
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + frisch))
                .andExpect(status().isOk());
    }
}
