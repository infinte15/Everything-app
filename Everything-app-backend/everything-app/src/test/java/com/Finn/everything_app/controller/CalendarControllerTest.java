package com.Finn.everything_app.controller;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.CalendarEventRepository;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises the real HTTP routing layer (not just the service), the way a pure Mockito
 * service test cannot. This suite exists specifically because PUT /events/{id} was called
 * by the frontend for every drag-and-drop reschedule while no @PutMapping for it existed on
 * the controller at all — every such request 404/500'd and the optimistic UI update quietly
 * reverted. A service-level test alone would never catch a missing route.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CalendarControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CalendarEventRepository calendarEventRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private User testUser;
    private String token;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByUsername("calendar_controller_test_user").orElseGet(() -> {
            User u = new User();
            u.setUsername("calendar_controller_test_user");
            u.setEmail("calendar_controller_test_user@test.local");
            u.setPasswordHash(passwordEncoder.encode("irrelevant"));
            u.setCreatedAt(LocalDateTime.now());
            return userRepository.save(u);
        });
        token = jwtUtil.generateToken(testUser.getUsername(), testUser.getId());
    }

    @AfterEach
    void tearDown() {
        calendarEventRepository.findByUserIdAndStartTimeBetween(
                testUser.getId(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(365)
        ).forEach(calendarEventRepository::delete);
    }

    @Test
    void putEventsById_reschedulesAnExistingEvent() throws Exception {
        CalendarEvent event = new CalendarEvent();
        event.setUser(testUser);
        event.setTitle("Original");
        event.setStartTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));
        event.setEndTime(event.getStartTime().plusHours(1));
        event.setEventType(EventType.TASK);
        event.setIsFixed(false);
        CalendarEvent saved = calendarEventRepository.save(event);

        LocalDateTime newStart = saved.getStartTime().plusHours(4);
        LocalDateTime newEnd = saved.getEndTime().plusHours(4);

        Map<String, Object> body = new HashMap<>();
        body.put("id", saved.getId());
        body.put("title", "Original");
        body.put("startTime", newStart.toString());
        body.put("endTime", newEnd.toString());
        body.put("eventType", "TASK");
        body.put("isFixed", false);

        mockMvc.perform(put("/api/calendar/events/{id}", saved.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(saved.getId().intValue())));

        CalendarEvent reloaded = calendarEventRepository.findById(saved.getId()).orElseThrow();
        assertEquals(newStart, reloaded.getStartTime());
        assertEquals(newEnd, reloaded.getEndTime());
        // Manually rescheduling a TASK-type event pins it so the scheduler won't move it again.
        assertTrue(reloaded.getIsFixed());
    }

    @Test
    void postEvents_rejectsAnInvalidEventTypeInsteadOf500ing() throws Exception {
        // Guards the paired bug: the frontend's generic "New Event" sheet used to send
        // eventType values ("Personal"/"Studium") that don't exist in the backend's
        // EventType enum, which Jackson can't deserialize — the create silently 500'd.
        // A malformed enum value should come back as a client error (400), not a 500.
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Bad Type Event");
        body.put("startTime", LocalDateTime.now().plusDays(1).toString());
        body.put("endTime", LocalDateTime.now().plusDays(1).plusHours(1).toString());
        body.put("eventType", "NotARealEventType");
        body.put("isFixed", false);

        mockMvc.perform(post("/api/calendar/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is4xxClientError());
    }
}
