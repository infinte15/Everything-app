package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.HabitDTO;
import com.Finn.everything_app.dto.HabitCompletionDTO;
import com.Finn.everything_app.mapper.HabitMapper;
import com.Finn.everything_app.model.Habit;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.HabitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/habits")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class HabitController {

    private final HabitService habitService;
    private final HabitMapper habitMapper;

    @GetMapping
    public ResponseEntity<List<HabitDTO>> getAllHabits(@CurrentUser Long userId) {
        List<Habit> habits = habitService.getUserHabits(userId);
        return ResponseEntity.ok(
                habits.stream().map(habitMapper::toDTO).collect(Collectors.toList())
        );
    }

    @PostMapping
    public ResponseEntity<HabitDTO> createHabit(
            @CurrentUser Long userId,
            @Valid @RequestBody HabitDTO habitDTO) {

        Habit habit = habitMapper.toEntity(habitDTO);
        Habit created = habitService.createHabit(userId, habit);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                habitMapper.toDTO(created)
        );
    }


    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> completeHabit(
            @PathVariable Long id,
            @RequestParam(required = false) String date) {

        LocalDate completionDate = date != null ?
                LocalDate.parse(date) : LocalDate.now();

        habitService.markHabitComplete(id, completionDate);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/{id}/progress")
    public ResponseEntity<List<HabitCompletionDTO>> getHabitProgress(
            @PathVariable Long id,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        List<HabitCompletionDTO> progress = habitService.getHabitProgress(
                id,
                LocalDate.parse(startDate),
                LocalDate.parse(endDate)
        );

        return ResponseEntity.ok(progress);
    }
}