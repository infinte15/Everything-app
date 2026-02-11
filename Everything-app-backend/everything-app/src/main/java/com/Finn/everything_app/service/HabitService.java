package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.HabitCompletionDTO;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HabitService {

    private final HabitRepository habitRepository;
    private final HabitCompletionRepository completionRepository;
    private final UserRepository userRepository;

    @Transactional
    public Habit createHabit(Long userId, Habit habit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        habit.setUser(user);
        habit.setCurrentStreak(0);
        habit.setLongestStreak(0);

        return habitRepository.save(habit);
    }

    public List<Habit> getUserHabits(Long userId) {
        return habitRepository.findByUserId(userId);
    }

    public Habit getHabitById(Long id) {
        return habitRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Habit nicht gefunden"));
    }

    @Transactional
    public Habit updateHabit(Long id, Habit updatedHabit) {
        Habit habit = getHabitById(id);

        if (updatedHabit.getName() != null) {
            habit.setName(updatedHabit.getName());
        }
        if (updatedHabit.getDescription() != null) {
            habit.setDescription(updatedHabit.getDescription());
        }
        if (updatedHabit.getFrequency() != null) {
            habit.setFrequency(updatedHabit.getFrequency());
        }
        if (updatedHabit.getMonday() != null) {
            habit.setMonday(updatedHabit.getMonday());
        }
        if (updatedHabit.getTuesday() != null) {
            habit.setTuesday(updatedHabit.getTuesday());
        }
        if (updatedHabit.getWednesday() != null) {
            habit.setWednesday(updatedHabit.getWednesday());
        }
        if (updatedHabit.getThursday() != null) {
            habit.setThursday(updatedHabit.getThursday());
        }
        if (updatedHabit.getFriday() != null) {
            habit.setFriday(updatedHabit.getFriday());
        }
        if (updatedHabit.getSaturday() != null) {
            habit.setSaturday(updatedHabit.getSaturday());
        }
        if (updatedHabit.getSunday() != null) {
            habit.setSunday(updatedHabit.getSunday());
        }
        if (updatedHabit.getPreferredTime() != null) {
            habit.setPreferredTime(updatedHabit.getPreferredTime());
        }
        if (updatedHabit.getDurationMinutes() != null) {
            habit.setDurationMinutes(updatedHabit.getDurationMinutes());
        }
        if (updatedHabit.getStartDate() != null) {
            habit.setStartDate(updatedHabit.getStartDate());
        }
        if (updatedHabit.getEndDate() != null) {
            habit.setEndDate(updatedHabit.getEndDate());
        }

        return habitRepository.save(habit);
    }

    @Transactional
    public void deleteHabit(Long id) {
        Habit habit = getHabitById(id);
        habitRepository.delete(habit);
    }


    @Transactional
    public void markHabitComplete(Long habitId, LocalDate date) {
        Habit habit = getHabitById(habitId);


        Optional<HabitCompletion> existing = completionRepository
                .findByHabitIdAndCompletionDate(habitId, date);

        if (existing.isPresent()) {
            return;
        }
        HabitCompletion completion = new HabitCompletion();
        completion.setHabit(habit);
        completion.setCompletionDate(date);
        completion.setWasSuccessful(true);

        completionRepository.save(completion);

        updateStreaks(habit);
    }


    private void updateStreaks(Habit habit) {
        LocalDate today = LocalDate.now();
        LocalDate checkDate = today;
        int currentStreak = 0;

        while (true) {
            Optional<HabitCompletion> completion = completionRepository
                    .findByHabitIdAndCompletionDate(habit.getId(), checkDate);

            if (completion.isPresent()) {
                currentStreak++;
                checkDate = checkDate.minusDays(1);
            } else {
                break;
            }
        }

        habit.setCurrentStreak(currentStreak);

        if (currentStreak > habit.getLongestStreak()) {
            habit.setLongestStreak(currentStreak);
        }

        habitRepository.save(habit);
    }


    public List<HabitCompletionDTO> getHabitProgress(Long habitId, LocalDate start, LocalDate end) {
        List<HabitCompletion> completions = completionRepository
                .findByHabitIdAndCompletionDateBetween(habitId, start, end);

        return completions.stream().map(c -> {
            HabitCompletionDTO dto = new HabitCompletionDTO();
            dto.setId(c.getId());
            dto.setHabitId(c.getHabit().getId());
            dto.setCompletionDate(c.getCompletionDate());
            dto.setCompletedAt(c.getCompletedAt());
            dto.setNotes(c.getNotes());
            dto.setWasSuccessful(c.getWasSuccessful());
            return dto;
        }).collect(Collectors.toList());
    }

    public boolean isCompletedToday(Long habitId) {
        Optional<HabitCompletion> completion = completionRepository
                .findByHabitIdAndCompletionDate(habitId, LocalDate.now());

        return completion.isPresent();
    }
}