package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkoutPlanService {

    private final WorkoutPlanRepository workoutPlanRepository;
    private final UserRepository userRepository;

    @Transactional
    public WorkoutPlan createPlan(Long userId, WorkoutPlan plan) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        plan.setUser(user);
        plan.setIsActive(plan.getIsActive() != null ? plan.getIsActive() : false);
        plan.setTotalWorkouts(plan.getTotalWorkouts() != null ? plan.getTotalWorkouts() : 0);
        plan.setCompletedWorkouts(plan.getCompletedWorkouts() != null ? plan.getCompletedWorkouts() : 0);

        return workoutPlanRepository.save(plan);
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

        return workoutPlanRepository.save(plan);
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

        return workoutPlanRepository.save(plan);
    }

    @Transactional
    public void deletePlan(Long id) {
        WorkoutPlan plan = getPlanById(id);
        workoutPlanRepository.delete(plan);
    }

    @Transactional
    public void incrementCompletedWorkouts(Long planId) {
        WorkoutPlan plan = getPlanById(planId);
        plan.setCompletedWorkouts(plan.getCompletedWorkouts() + 1);
        workoutPlanRepository.save(plan);
    }
}