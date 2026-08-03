package com.Finn.everything_app.service;

import com.Finn.everything_app.event.ScheduleChangedEvent;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkoutPlanService {

    private static final int DEFAULT_SESSION_DURATION_MINUTES = 45;

    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final RoutineRepository routineRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public WorkoutPlan createPlan(Long userId, WorkoutPlan plan) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        plan.setUser(user);
        plan.setIsActive(plan.getIsActive() != null ? plan.getIsActive() : false);
        plan.setTotalWorkouts(plan.getTotalWorkouts() != null ? plan.getTotalWorkouts() : 0);
        plan.setCompletedWorkouts(plan.getCompletedWorkouts() != null ? plan.getCompletedWorkouts() : 0);

        WorkoutPlan saved = workoutPlanRepository.save(plan);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return saved;
    }

    public List<WorkoutPlan> getUserPlans(Long userId) {
        return workoutPlanRepository.findByUserId(userId);
    }

    public WorkoutPlan getActivePlan(Long userId) {
        return workoutPlanRepository.findByUserIdAndIsActiveTrue(userId)
                .orElse(null);
    }

    public WorkoutPlan getPlanById(Long id) {
        return workoutPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Workout-Plan nicht gefunden"));
    }

    @Transactional
    public WorkoutPlan updatePlan(Long id, WorkoutPlan updatedPlan) {
        WorkoutPlan plan = getPlanById(id);

        if (updatedPlan.getName() != null) {
            plan.setName(updatedPlan.getName());
        }
        if (updatedPlan.getDescription() != null) {
            plan.setDescription(updatedPlan.getDescription());
        }
        if (updatedPlan.getGoal() != null) {
            plan.setGoal(updatedPlan.getGoal());
        }
        if (updatedPlan.getDifficulty() != null) {
            plan.setDifficulty(updatedPlan.getDifficulty());
        }
        if (updatedPlan.getDurationWeeks() != null) {
            plan.setDurationWeeks(updatedPlan.getDurationWeeks());
        }
        if (updatedPlan.getWorkoutsPerWeek() != null) {
            plan.setWorkoutsPerWeek(updatedPlan.getWorkoutsPerWeek());
        }
        if (updatedPlan.getStartDate() != null) {
            plan.setStartDate(updatedPlan.getStartDate());
        }
        if (updatedPlan.getEndDate() != null) {
            plan.setEndDate(updatedPlan.getEndDate());
        }

        WorkoutPlan saved = workoutPlanRepository.save(plan);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, saved.getUser().getId()));
        return saved;
    }

    @Transactional
    public WorkoutPlan activatePlan(Long userId, Long planId) {
        // Deaktiviere alle anderen Pläne
        List<WorkoutPlan> allPlans = getUserPlans(userId);
        for (WorkoutPlan p : allPlans) {
            if (p.getIsActive()) {
                p.setIsActive(false);
                workoutPlanRepository.save(p);
            }
        }

        // Aktiviere den gewählten Plan
        WorkoutPlan plan = getPlanById(planId);
        plan.setIsActive(true);

        WorkoutPlan saved = workoutPlanRepository.save(plan);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return saved;
    }

    @Transactional
    public void deletePlan(Long id) {
        WorkoutPlan plan = getPlanById(id);
        Long userId = plan.getUser().getId();
        // Routinen sind wiederverwendbar und ueberleben ihr Programm - erst die Zuordnung
        // loesen, sonst laeuft das Delete in die Fremdschluessel-Bedingung.
        routineRepository.detachFromPlan(id);
        workoutPlanRepository.delete(plan);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
    }

    @Transactional
    public void incrementCompletedWorkouts(Long planId) {
        WorkoutPlan plan = getPlanById(planId);
        plan.setCompletedWorkouts(plan.getCompletedWorkouts() + 1);
        workoutPlanRepository.save(plan);
    }

    /**
     * Tops up flexible WorkoutSession placeholders for a given ISO week (Monday start) so the
     * plan's weekly target is met, without creating duplicates on repeated calls (checks existing
     * placeholders + manually pinned sessions already counting toward that week first).
     */
    @Transactional
    public void generateWeeklyPlaceholders(Long userId, WorkoutPlan plan, LocalDate weekStart) {
        if (plan.getWorkoutsPerWeek() == null || plan.getWorkoutsPerWeek() <= 0) return;

        LocalDateTime weekStartDt = weekStart.atStartOfDay();
        LocalDateTime weekEndDt = weekStart.plusDays(7).atStartOfDay();

        long placeholderCount = workoutSessionRepository
                .countByWorkoutPlanIdAndTargetWeekStart(plan.getId(), weekStart);
        long manualCount = workoutSessionRepository
                .countByWorkoutPlanIdAndIsFlexibleAndStartTimeBetween(plan.getId(), false, weekStartDt, weekEndDt);

        int shortfall = plan.getWorkoutsPerWeek() - (int) (placeholderCount + manualCount);
        if (shortfall <= 0) return;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        // Hat der Plan Routinen, werden sie der Reihe nach eingeplant. Der Versatz um
        // placeholderCount setzt die Rotation fort, wenn die Woche schon teilweise belegt ist.
        List<Routine> routines = routineRepository
                .findByWorkoutPlanIdAndIsArchivedFalseOrderByOrderIndexAscIdAsc(plan.getId());

        for (int i = 0; i < shortfall; i++) {
            WorkoutSession placeholder = new WorkoutSession();
            placeholder.setUser(user);
            placeholder.setWorkoutPlan(plan);
            placeholder.setIntensity(5);
            placeholder.setIsCompleted(false);
            placeholder.setIsFlexible(true);
            placeholder.setTargetWeekStart(weekStart);

            if (routines.isEmpty()) {
                placeholder.setName(plan.getName() + " Session");
                placeholder.setDurationMinutes(DEFAULT_SESSION_DURATION_MINUTES);
            } else {
                Routine routine = routines.get((int) ((placeholderCount + i) % routines.size()));
                placeholder.setRoutine(routine);
                placeholder.setName(routine.getName());
                placeholder.setDurationMinutes(routine.getEstimatedDurationMinutes() != null
                        ? routine.getEstimatedDurationMinutes()
                        : DEFAULT_SESSION_DURATION_MINUTES);
            }

            workoutSessionRepository.save(placeholder);
        }
    }
}