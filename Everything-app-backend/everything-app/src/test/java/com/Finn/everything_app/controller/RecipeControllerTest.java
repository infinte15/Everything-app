package com.Finn.everything_app.controller;

import com.Finn.everything_app.model.Recipe;
import com.Finn.everything_app.model.RecipeIngredient;
import com.Finn.everything_app.model.RecipeStep;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.RecipeRepository;
import com.Finn.everything_app.repository.UserRepository;
import com.Finn.everything_app.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Die echte HTTP-Schicht - Routen, Statuscodes, Eigentum.
 *
 * <p>Zwei Dinge lassen sich unterhalb davon nicht pruefen: dass ein kaputtes Datum in einem
 * Query-Parameter als 400 herauskommt (das entscheidet der {@code GlobalExceptionHandler}, und
 * der vorhandene Handler fuer {@code HttpMessageNotReadableException} deckt nur den
 * Request-Body ab), und dass ein fremdes Rezept ueber die echte Kette 404 gibt.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecipeControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RecipeRepository recipeRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private User owner;
    private User stranger;
    private String ownerToken;
    private String strangerToken;

    private User ensureUser(String username) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User u = new User();
            u.setUsername(username);
            u.setEmail(username + "@test.local");
            u.setPasswordHash(passwordEncoder.encode("irrelevant"));
            u.setCreatedAt(LocalDateTime.now());
            return userRepository.save(u);
        });
    }

    @BeforeEach
    void setUp() {
        owner = ensureUser("recipe_controller_owner");
        stranger = ensureUser("recipe_controller_stranger");
        ownerToken = jwtUtil.generateToken(owner.getUsername(), owner.getId());
        strangerToken = jwtUtil.generateToken(stranger.getUsername(), stranger.getId());
    }

    @AfterEach
    void tearDown() {
        recipeRepository.findByUserId(owner.getId()).forEach(recipeRepository::delete);
        recipeRepository.findByUserId(stranger.getId()).forEach(recipeRepository::delete);
    }

    private Recipe storedRecipe() {
        Recipe recipe = new Recipe();
        recipe.setUser(owner);
        recipe.setName("Pfannkuchen");
        recipe.setPrepTimeMinutes(5);
        recipe.setCookTimeMinutes(10);
        recipe.setServings(4);
        recipe.setCategory("Backen");
        recipe.setCookCount(0);

        RecipeIngredient mehl = new RecipeIngredient();
        mehl.setName("Mehl");
        mehl.setAmount(new BigDecimal("400"));
        mehl.setUnit("g");
        recipe.replaceIngredients(new ArrayList<>(List.of(mehl)));

        RecipeStep step = new RecipeStep();
        step.setText("Alles verrühren.");
        recipe.replaceSteps(new ArrayList<>(List.of(step)));

        return recipeRepository.save(recipe);
    }

    // Vorher wurde das Datum mit LocalDate.parse von Hand zerlegt, die
    // DateTimeParseException fiel in den 500er-Handler, und ein Tippfehler im Client sah aus
    // wie ein Serverabsturz.
    @Test
    void einKaputtesDatumImParameterIst400Und500() throws Exception {
        mockMvc.perform(get("/api/recipes/meal-plan")
                        .param("startDate", "unsinn")
                        .param("endDate", "2026-08-10")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void einKaputtesDatumInDerPfadvariableEbenso() throws Exception {
        mockMvc.perform(get("/api/recipes/meal-plan/date/{date}", "2026-13-45")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    // Was einem nicht gehoert, soll nicht einmal als existent erkennbar sein - deshalb 404
    // und nicht 403.
    @Test
    void einFremdesRezeptIstNichtGefunden() throws Exception {
        Recipe recipe = storedRecipe();

        mockMvc.perform(get("/api/recipes/{id}", recipe.getId())
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/recipes/{id}", recipe.getId())
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());

        assertTrue(recipeRepository.findById(recipe.getId()).isPresent(),
                "das Rezept muss trotz DELETE noch da sein");
    }

    @Test
    void dasEigeneRezeptKommtMitZutatenUndSchrittenZurueck() throws Exception {
        Recipe recipe = storedRecipe();

        mockMvc.perform(get("/api/recipes/{id}", recipe.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pfannkuchen"))
                .andExpect(jsonPath("$.ingredients[0].name").value("Mehl"))
                .andExpect(jsonPath("$.ingredients[0].unit").value("g"))
                .andExpect(jsonPath("$.steps[0].text").value("Alles verrühren."))
                // Der Klartext dient dem Hinsehen im Terminal.
                .andExpect(jsonPath("$.ingredientsText").value("400 g Mehl"));
    }

    // Die Whitelist muss auch ueber die echte Kette 400 geben, nicht 500.
    @Test
    void einImportVonEinemFremdenHostIst400() throws Exception {
        mockMvc.perform(post("/api/recipes/import/preview")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/rezept\"}"))
                .andExpect(status().isBadRequest());
    }

    // Der Parse-Endpunkt schreibt nichts und laeuft durch denselben Parser wie der Import.
    @Test
    void eingefuegteZeilenWerdenZerlegtOhneEtwasZuSpeichern() throws Exception {
        mockMvc.perform(post("/api/recipes/ingredients/parse")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"text\":\"400 g Mehl\\n1 Prise(n) Salz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Mehl"))
                .andExpect(jsonPath("$[0].unit").value("g"))
                .andExpect(jsonPath("$[1].unit").value("Prise"));

        assertEquals(0, recipeRepository.findByUserId(owner.getId()).size());
    }

    // Ein Rezept ohne Zutaten ist kein Rezept - das gehoert in die Validierung, nicht in eine
    // spaetere Ueberraschung beim Kochen.
    @Test
    void einRezeptOhneZutatenWirdAbgelehnt() throws Exception {
        String body = """
                {"name":"Leer","prepTimeMinutes":5,"cookTimeMinutes":5,"servings":2,
                 "category":"Sonstiges","ingredients":[],"steps":[]}
                """;

        mockMvc.perform(post("/api/recipes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
