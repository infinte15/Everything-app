package com.Finn.everything_app.controller;

import com.Finn.everything_app.model.Habit;
import com.Finn.everything_app.model.HabitCompletion;
import com.Finn.everything_app.model.HabitFrequency;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.CalendarEventRepository;
import com.Finn.everything_app.repository.HabitCompletionRepository;
import com.Finn.everything_app.repository.HabitRepository;
import com.Finn.everything_app.repository.UserRepository;
import com.Finn.everything_app.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Haelt {@code Habit.completions} ueber die Session-Grenze hinweg fest.
 *
 * <p>{@code GET /api/habits} und {@code PUT /api/habits/{id}} antworteten mit 500, sobald eine
 * Gewohnheit auch nur eine Erledigung hatte: die Sammlung ist traege, {@code open-in-view=false}
 * schliesst die Session am Ende der Service-Methode, und der {@code HabitMapper} liest sie erst
 * danach im Controller - {@code LazyInitializationException} statt Antwort. Beide Pfade sind in
 * der App taeglich in Gebrauch, ein reiner Service-Test haette den Fehler nie gesehen: dort ist
 * die Session noch offen.
 *
 * <p>Deshalb geht jeder Test hier durch die echte HTTP-Schicht und prueft die Sammlung im JSON,
 * nicht am Entity.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HabitControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired HabitRepository habitRepository;
    @Autowired HabitCompletionRepository completionRepository;
    @Autowired CalendarEventRepository calendarEventRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private User testUser;
    private String token;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByUsername("habit_controller_test_user").orElseGet(() -> {
            User u = new User();
            u.setUsername("habit_controller_test_user");
            u.setEmail("habit_controller_test_user@test.local");
            u.setPasswordHash(passwordEncoder.encode("irrelevant"));
            u.setCreatedAt(LocalDateTime.now());
            return userRepository.save(u);
        });
        token = jwtUtil.generateToken(testUser.getUsername(), testUser.getId());
    }

    @AfterEach
    void tearDown() {
        calendarEventRepository.findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
                testUser.getId(),
                LocalDateTime.now().minusDays(365),
                LocalDateTime.now().plusDays(365)
        ).forEach(calendarEventRepository::delete);
        habitRepository.findByUserId(testUser.getId()).forEach(habitRepository::delete);
    }

    /** Legt eine Gewohnheit mit {@code completionCount} aufeinanderfolgenden Erledigungen an. */
    private Habit habitMitErledigungen(String name, int completionCount) {
        Habit habit = new Habit();
        habit.setUser(testUser);
        habit.setName(name);
        habit.setFrequency(HabitFrequency.DAILY);
        habit.setDurationMinutes(30);
        habit.setStartDate(LocalDate.now().minusDays(30));
        habit.setCurrentStreak(0);
        habit.setLongestStreak(0);
        Habit saved = habitRepository.save(habit);

        for (int i = 0; i < completionCount; i++) {
            HabitCompletion completion = new HabitCompletion();
            completion.setHabit(saved);
            completion.setCompletionDate(LocalDate.now().minusDays(i));
            completion.setWasSuccessful(true);
            completionRepository.save(completion);
        }
        return saved;
    }

    /** Der eigentliche Fehler: eine einzige Erledigung genuegte fuer den 500er. */
    @Test
    void gewohnheitenListeLiefertErledigungenStattFuenfhundert() throws Exception {
        habitMitErledigungen("Lesen", 3);

        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Lesen"))
                .andExpect(jsonPath("$[0].completedDates.length()").value(3))
                .andExpect(jsonPath("$[0].completedDates[0]")
                        .value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}")));
    }

    /**
     * Der Fetch-Join darf die Gewohnheit nicht vervielfachen - drei Erledigungen sind drei
     * Zeilen im Join, aber eine Gewohnheit in der Antwort.
     */
    @Test
    void fetchJoinVervielfachtDieGewohnheitNicht() throws Exception {
        habitMitErledigungen("Joggen", 5);
        habitMitErledigungen("Meditieren", 2);

        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        assertEquals(2, habitRepository.findByUserId(testUser.getId()).size(),
                "findByUserId muss trotz Fetch-Join eine Zeile pro Gewohnheit liefern");
    }

    /** Ohne Erledigungen war der Pfad schon immer heil - das muss so bleiben. */
    @Test
    void gewohnheitOhneErledigungen() throws Exception {
        habitMitErledigungen("Frisch", 0);

        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Frisch"));
    }

    /**
     * Der zweite betroffene Pfad. Hier ist die Service-Methode {@code @Transactional} - die
     * Session ist also erst recht zu, wenn der Controller danach mappt.
     */
    @Test
    void aktualisierenLiefertErledigungenStattFuenfhundert() throws Exception {
        Habit habit = habitMitErledigungen("Alt", 2);

        mockMvc.perform(put("/api/habits/{id}", habit.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Neu\",\"frequency\":\"DAILY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Neu"))
                .andExpect(jsonPath("$.completedDates.length()").value(2));
    }

    /** Abhaken und danach lesen - der Ablauf, den Nero fuer "hab Lesen gemacht" nutzt. */
    @Test
    void abhakenTauchtInDerListeAuf() throws Exception {
        Habit habit = habitMitErledigungen("Lesen", 0);
        String heute = LocalDate.now().toString();

        mockMvc.perform(post("/api/habits/{id}/complete", habit.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].completedDates[0]").value(heute))
                .andExpect(jsonPath("$[0].currentStreak").value(1));
    }
}
