package com.Finn.everything_app.controller;

import com.Finn.everything_app.model.CalendarEvent;
import com.Finn.everything_app.model.EventType;
import com.Finn.everything_app.model.Task;
import com.Finn.everything_app.model.TaskStatus;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.CalendarEventRepository;
import com.Finn.everything_app.repository.TaskRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises the real HTTP routing layer. DELETE /api/tasks/{id} used to be mapped as bare
 * {@code @DeleteMapping} with no path — every DELETE the frontend sent (always to
 * /api/tasks/{id}) 500'd, so a task swiped-to-delete was never actually removed on the
 * backend. Since it stayed in the dataset, the very next full reload (e.g. after creating
 * another task, which notifies listeners and rebuilds the list) made it reappear — a
 * service-level test alone would never catch a route mapping bug like this.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired CalendarEventRepository calendarEventRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private User testUser;
    private String token;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByUsername("task_controller_test_user").orElseGet(() -> {
            User u = new User();
            u.setUsername("task_controller_test_user");
            u.setEmail("task_controller_test_user@test.local");
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
        taskRepository.findByUserId(testUser.getId()).forEach(taskRepository::delete);
    }

    @Test
    void deleteTaskById_actuallyRemovesIt() throws Exception {
        Task task = new Task();
        task.setUser(testUser);
        task.setTitle("Delete me");
        task.setPriority(3);
        task.setEstimatedDurationMinutes(30);
        task.setStatus(TaskStatus.TODO);
        task.setCreatedAt(LocalDateTime.now());
        Task saved = taskRepository.save(task);

        mockMvc.perform(delete("/api/tasks/{id}", saved.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertTrue(taskRepository.findById(saved.getId()).isEmpty(),
                "the task must actually be gone from the database, not just return 204");
    }

    @Test
    void deletingTheSameTaskTwice_secondCallIs404NotACascadingDelete() throws Exception {
        Task taskA = new Task();
        taskA.setUser(testUser);
        taskA.setTitle("A");
        taskA.setPriority(3);
        taskA.setEstimatedDurationMinutes(30);
        taskA.setStatus(TaskStatus.TODO);
        taskA.setCreatedAt(LocalDateTime.now());
        Task savedA = taskRepository.save(taskA);

        Task taskB = new Task();
        taskB.setUser(testUser);
        taskB.setTitle("B");
        taskB.setPriority(3);
        taskB.setEstimatedDurationMinutes(30);
        taskB.setStatus(TaskStatus.TODO);
        taskB.setCreatedAt(LocalDateTime.now());
        Task savedB = taskRepository.save(taskB);

        mockMvc.perform(delete("/api/tasks/{id}", savedA.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Deleting an already-gone id must fail (matches this codebase's existing "not found"
        // convention of a plain RuntimeException -> 500, same as every other *ById lookup —
        // not ideal REST semantics, but consistent, and not this fix's concern) and must
        // never cascade into deleting unrelated tasks.
        mockMvc.perform(delete("/api/tasks/{id}", savedA.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is5xxServerError());

        assertTrue(taskRepository.findById(savedB.getId()).isPresent(),
                "an unrelated task must never be deleted as a side effect");
    }

    @Test
    void deleteTask_withAnAutoScheduledCalendarEvent_stillSucceeds() throws Exception {
        // Guards a second bug found alongside the routing one: a task with a scheduler-placed
        // CalendarEvent (related_task_id FK) used to 500 on delete — "could not execute
        // statement; ... violates foreign key constraint" — because nothing removed the
        // calendar event first. Since the smart scheduler auto-places nearly every task,
        // this hit constantly in real use, not just as an edge case.
        Task task = new Task();
        task.setUser(testUser);
        task.setTitle("Scheduled task");
        task.setPriority(3);
        task.setEstimatedDurationMinutes(30);
        task.setStatus(TaskStatus.TODO);
        task.setCreatedAt(LocalDateTime.now());
        Task saved = taskRepository.save(task);

        CalendarEvent event = new CalendarEvent();
        event.setUser(testUser);
        event.setTitle("Scheduled task");
        event.setStartTime(LocalDateTime.now().plusDays(1));
        event.setEndTime(LocalDateTime.now().plusDays(1).plusMinutes(30));
        event.setEventType(EventType.TASK);
        event.setIsFixed(false);
        event.setRelatedTask(saved);
        CalendarEvent savedEvent = calendarEventRepository.save(event);

        mockMvc.perform(delete("/api/tasks/{id}", saved.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertTrue(taskRepository.findById(saved.getId()).isEmpty());
        assertTrue(calendarEventRepository.findById(savedEvent.getId()).isEmpty(),
                "the orphaned calendar event must be cleaned up, not left dangling");
    }

    @Test
    void createTask_thenListIncludesExactlyThatTask() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "New Task");
        body.put("priority", 4);
        body.put("estimatedDurationMinutes", 45);
        body.put("status", "TODO");
        body.put("category", "Personal");

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", org.hamcrest.Matchers.equalTo("New Task")));

        assertEquals(1, taskRepository.findByUserId(testUser.getId()).size());
    }
}
