package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.WorkoutProgressDTO;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkoutSessionService {

    private final WorkoutSessionRepository workoutSessionRepository;
    private final UserRepository userRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutPlanService workoutPlanService;

    @Transactional
    public WorkoutSession createSession(Long userId, WorkoutSession session, Long planId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        session.setUser(user);

        if (planId != null) {
            WorkoutPlan plan = workoutPlanRepository.findById(planId)
                    .orElseThrow(() -> new RuntimeException("Workout-Plan nicht gefunden"));
            session.setWorkoutPlan(plan);
        }

        session.setIsCompleted(session.getIsCompleted() != null ? session.getIsCompleted() : false);

        return workoutSessionRepository.save(session);
    }

    public List<WorkoutSession> getUserSessions(Long userId) {
        return workoutSessionRepository.findByUserId(userId);
    }

    public WorkoutSession getSessionById(Long id) {
        return workoutSessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Workout-Session nicht gefunden"));
    }

    public List<WorkoutSession> getSessionsByPlan(Long planId) {
        return workoutSessionRepository.findByWorkoutPlanId(planId);
    }

    public List<WorkoutSession> getSessionsInDateRange(Long userId, LocalDate start, LocalDate end) {
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        return workoutSessionRepository.findByUserIdAndStartTimeBetween(userId, startDateTime, endDateTime);
    }

    @Transactional
    public WorkoutSession updateSession(Long id, WorkoutSession updatedSession) {
        WorkoutSession session = getSessionById(id);

        if (updatedSession.getName() != null) {
            session.setName(updatedSession.getName());
        }
        if (updatedSession.getDescription() != null) {
            session.setDescription(updatedSession.getDescription());
        }
        if (updatedSession.getStartTime() != null) {
            session.setStartTime(updatedSession.getStartTime());
        }
        if (updatedSession.getEndTime() != null) {
            session.setEndTime(updatedSession.getEndTime());
        }
        if (updatedSession.getDurationMinutes() != null) {
            session.setDurationMinutes(updatedSession.getDurationMinutes());
        }
        if (updatedSession.getWorkoutType() != null) {
            session.setWorkoutType(updatedSession.getWorkoutType());
        }
        if (updatedSession.getIntensity() != null) {
            session.setIntensity(updatedSession.getIntensity());
        }
        if (updatedSession.getCaloriesBurned() != null) {
            session.setCaloriesBurned(updatedSession.getCaloriesBurned());
        }
        if (updatedSession.getNotes() != null) {
            session.setNotes(updatedSession.getNotes());
        }
        if (updatedSession.getLocation() != null) {
            session.setLocation(updatedSession.getLocation());
        }

        return workoutSessionRepository.save(session);
    }

    @Transactional
    public WorkoutSession completeSession(Long id) {
        WorkoutSession session = getSessionById(id);
        session.setIsCompleted(true);

        if (session.getEndTime() == null) {
            session.setEndTime(LocalDateTime.now());
        }

        // Update Workout Plan Statistics
        if (session.getWorkoutPlan() != null) {
            workoutPlanService.incrementCompletedWorkouts(session.getWorkoutPlan().getId());
        }

        return workoutSessionRepository.save(session);
    }

    @Transactional
    public void deleteSession(Long id) {
        WorkoutSession session = getSessionById(id);
        workoutSessionRepository.delete(session);
    }

    // STATISTICS

    public WorkoutProgressDTO calculateProgress(Long userId, LocalDate start, LocalDate end) {
        List<WorkoutSession> sessions;

        if (start != null && end != null) {
            sessions = getSessionsInDateRange(userId, start, end);
        } else {
            sessions = getUserSessions(userId);
        }

        int totalWorkouts = sessions.size();
        int completedWorkouts = (int) sessions.stream()
                .filter(WorkoutSession::getIsCompleted)
                .count();

        double completionRate = totalWorkouts > 0 ?
                ((double) completedWorkouts / totalWorkouts) * 100 : 0.0;

        int totalMinutes = sessions.stream()
                .filter(s -> s.getDurationMinutes() != null)
                .mapToInt(WorkoutSession::getDurationMinutes)
                .sum();

        double avgIntensity = sessions.stream()
                .filter(s -> s.getIntensity() != null)
                .mapToInt(WorkoutSession::getIntensity)
                .average()
                .orElse(0.0);

        int totalCalories = sessions.stream()
                .filter(s -> s.getCaloriesBurned() != null)
                .mapToInt(WorkoutSession::getCaloriesBurned)
                .sum();

        Map<String, Integer> workoutsByType = sessions.stream()
                .filter(s -> s.getWorkoutType() != null)
                .collect(Collectors.groupingBy(
                        WorkoutSession::getWorkoutType,
                        Collectors.summingInt(s -> 1)
                ));

        String mostFrequentType = workoutsByType.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        WorkoutProgressDTO progress = new WorkoutProgressDTO();
        progress.setTotalWorkouts(totalWorkouts);
        progress.setCompletedWorkouts(completedWorkouts);
        progress.setCompletionRate(completionRate);
        progress.setTotalMinutesTrained(totalMinutes);
        progress.setAverageIntensity(avgIntensity);
        progress.setTotalCaloriesBurned(totalCalories);
        progress.setWorkoutsByType(workoutsByType);
        progress.setMostFrequentWorkoutType(mostFrequentType);

        return progress;
    }
}