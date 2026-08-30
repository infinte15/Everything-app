package com.Finn.everything_app.service;

import com.Finn.everything_app.event.ScheduleChangedEvent;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkoutSessionService {

    private final WorkoutSessionRepository workoutSessionRepository;
    private final UserRepository userRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutPlanService workoutPlanService;
    private final CalendarEventRepository calendarEventRepository;
    private final ApplicationEventPublisher eventPublisher;

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

        WorkoutSession saved = workoutSessionRepository.save(session);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return saved;
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

        WorkoutSession saved = workoutSessionRepository.save(session);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, saved.getUser().getId()));
        return saved;
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

        WorkoutSession saved = workoutSessionRepository.save(session);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, saved.getUser().getId()));
        return saved;
    }

    @Transactional
    public void deleteSession(Long id) {
        WorkoutSession session = getSessionById(id);
        Long userId = session.getUser().getId();
        // Calendar events reference this session via related_workout_id — clean them up
        // first or the delete violates that foreign key and 500s.
        calendarEventRepository.deleteAll(calendarEventRepository.findByRelatedWorkoutId(id));
        workoutSessionRepository.delete(session);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
    }
}
