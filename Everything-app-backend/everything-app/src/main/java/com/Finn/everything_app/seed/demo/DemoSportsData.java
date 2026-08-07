package com.Finn.everything_app.seed.demo;

import com.Finn.everything_app.model.Exercise;
import com.Finn.everything_app.model.ExerciseSet;
import com.Finn.everything_app.model.Routine;
import com.Finn.everything_app.model.RoutineExercise;
import com.Finn.everything_app.model.SetType;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.model.WorkoutPlan;
import com.Finn.everything_app.model.WorkoutSession;
import com.Finn.everything_app.repository.ExerciseRepository;
import com.Finn.everything_app.repository.ExerciseSetRepository;
import com.Finn.everything_app.repository.RoutineExerciseRepository;
import com.Finn.everything_app.repository.RoutineRepository;
import com.Finn.everything_app.repository.WorkoutPlanRepository;
import com.Finn.everything_app.repository.WorkoutSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo-Bestand des Sport-Space: ein aktiver Push/Pull/Legs-Plan mit vier Routinen und sechs
 * Wochen protokollierter Trainingshistorie.
 *
 * <p>Die Gewichte steigen über die Wochen leicht an — ohne diese Steigerung sind alle
 * Fortschrittsdiagramme der App waagerechte Linien und zeigen nichts.
 *
 * <p>Zukünftige Einheiten legt der Seeder <b>nicht</b> an: der Scheduler erzeugt für jede Woche
 * im Horizont selbst flexible Platzhalter aus dem aktiven Plan
 * ({@code WorkoutPlanService.generateWeeklyPlaceholders}) und sucht ihnen freie Slots. Eigene
 * Platzhalter hier würden nur doppelt zählen.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemoSportsData {

    private final WorkoutPlanRepository planRepository;
    private final RoutineRepository routineRepository;
    private final RoutineExerciseRepository routineExerciseRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final ExerciseSetRepository setRepository;
    private final ExerciseRepository exerciseRepository;

    /** Wochen Trainingshistorie vor heute. */
    private static final int HISTORY_WEEKS = 6;

    /** Eine Zeile einer Routine: Übungsname aus dem Katalog + Zielvorgaben. */
    private record Slot(String exerciseName, int sets, int repsMin, int repsMax,
                        double startWeight, int restSeconds) {
    }

    @Transactional
    public void seed(User user, LocalDate today) {
        Map<String, Exercise> catalog = catalogByName();
        if (catalog.isEmpty()) {
            log.warn("Übungs-Katalog ist leer - Sport-Demo übersprungen");
            return;
        }

        WorkoutPlan plan = plan(user, today);

        Routine push = routine(user, plan, catalog, 0, "Push A — Brust, Schultern, Trizeps", "#FF6B6B", "Tag 1", 70,
                "Schwerer Druck zuerst, danach Volumen für Schultern und Trizeps.", List.of(
                        new Slot("Barbell Bench Press - Medium Grip", 4, 5, 8, 72.5, 180),
                        new Slot("Barbell Incline Bench Press - Medium Grip", 3, 8, 10, 50.0, 120),
                        new Slot("Dumbbell Shoulder Press", 3, 8, 12, 20.0, 120),
                        new Slot("Side Lateral Raise", 3, 12, 15, 10.0, 60),
                        new Slot("Cable Crossover", 3, 12, 15, 15.0, 60),
                        new Slot("Triceps Pushdown", 3, 10, 15, 30.0, 60)));

        Routine pull = routine(user, plan, catalog, 1, "Pull A — Rücken & Bizeps", "#4D96FF", "Tag 2", 75,
                "Kreuzheben schwer, danach horizontales und vertikales Ziehen.", List.of(
                        new Slot("Barbell Deadlift", 3, 3, 5, 110.0, 240),
                        new Slot("Pullups", 4, 6, 10, 0.0, 150),
                        new Slot("Bent Over Barbell Row", 3, 8, 10, 60.0, 120),
                        new Slot("Seated Cable Rows", 3, 10, 12, 55.0, 90),
                        new Slot("Face Pull", 3, 15, 20, 20.0, 60),
                        new Slot("Barbell Curl", 3, 8, 12, 30.0, 90)));

        Routine legs = routine(user, plan, catalog, 2, "Legs — Beine & Rumpf", "#6BCB77", "Tag 3", 80,
                "Kniebeuge als Hauptübung, hinterer Oberschenkel bewusst danach.", List.of(
                        new Slot("Barbell Full Squat", 4, 5, 8, 95.0, 210),
                        new Slot("Romanian Deadlift", 3, 8, 10, 70.0, 150),
                        new Slot("Leg Press", 3, 10, 12, 160.0, 120),
                        new Slot("Seated Leg Curl", 3, 10, 15, 45.0, 90),
                        new Slot("Standing Calf Raises", 4, 12, 20, 60.0, 60),
                        new Slot("Hanging Leg Raise", 3, 10, 15, 0.0, 60)));

        Routine full = routine(user, plan, catalog, 3, "Ganzkörper kurz (45 min)", "#FFD93D", "Ersatztag", 45,
                "Für Wochen, in denen drei Einheiten nicht in den Stundenplan passen.", List.of(
                        new Slot("Barbell Full Squat", 3, 6, 8, 90.0, 180),
                        new Slot("Barbell Bench Press - Medium Grip", 3, 6, 8, 70.0, 180),
                        new Slot("Pullups", 3, 6, 10, 0.0, 150),
                        new Slot("Plank", 3, 1, 1, 0.0, 60)));

        List<Routine> rotation = List.of(push, pull, legs, full);
        seedHistory(user, plan, rotation, today);
    }

    // ------------------------------------------------------------------- Plan

    private WorkoutPlan plan(User user, LocalDate today) {
        WorkoutPlan plan = new WorkoutPlan();
        plan.setUser(user);
        plan.setName("Push / Pull / Legs — Hypertrophie");
        plan.setDescription("""
                Dreier-Split über zwölf Wochen, drei bis vier Einheiten pro Woche. \
                Die Hauptübung jeder Einheit läuft nach doppelter Progression: erst die \
                Wiederholungen bis zur oberen Grenze hochziehen, dann das Gewicht.""");
        plan.setGoal("Muskelaufbau");
        plan.setDifficulty("fortgeschritten");
        plan.setDurationWeeks(12);
        plan.setWorkoutsPerWeek(3);
        plan.setStartDate(today.minusWeeks(HISTORY_WEEKS));
        plan.setEndDate(today.plusWeeks(6));
        plan.setIsActive(true);
        return planRepository.save(plan);
    }

    private Routine routine(User user, WorkoutPlan plan, Map<String, Exercise> catalog, int order,
                            String name, String color, String dayLabel, int minutes,
                            String description, List<Slot> slots) {
        Routine routine = new Routine();
        routine.setUser(user);
        routine.setWorkoutPlan(plan);
        routine.setName(name);
        routine.setDescription(description);
        routine.setColorHex(color);
        routine.setDayLabel(dayLabel);
        routine.setEstimatedDurationMinutes(minutes);
        routine.setOrderIndex(order);
        routine = routineRepository.save(routine);

        int position = 0;
        for (Slot slot : slots) {
            Exercise exercise = catalog.get(slot.exerciseName().toLowerCase());
            if (exercise == null) {
                // Der Katalog ist eine externe Quelle; ein umbenannter Eintrag darf den Seeder
                // nicht abbrechen - dann fehlt eben eine Zeile in der Routine.
                log.warn("Übung '{}' nicht im Katalog - in Routine '{}' übersprungen",
                        slot.exerciseName(), name);
                continue;
            }
            RoutineExercise entry = new RoutineExercise();
            entry.setRoutine(routine);
            entry.setExercise(exercise);
            entry.setOrderIndex(position++);
            entry.setTargetSets(slot.sets());
            entry.setTargetRepsMin(slot.repsMin());
            entry.setTargetRepsMax(slot.repsMax());
            entry.setTargetWeight(slot.startWeight() > 0 ? slot.startWeight() : null);
            entry.setRestSeconds(slot.restSeconds());
            routineExerciseRepository.save(entry);
        }
        return routine;
    }

    // --------------------------------------------------------------- Historie

    /**
     * Drei Einheiten je Woche über {@link #HISTORY_WEEKS} Wochen, Montag / Mittwoch / Freitag.
     * Die laufende Woche bekommt nur die Einheiten, die schon vorbei sind — den Rest füllt der
     * Scheduler als Platzhalter auf.
     */
    private void seedHistory(User user, WorkoutPlan plan, List<Routine> rotation, LocalDate today) {
        DayOfWeek[] trainingDays = {DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY};
        int completed = 0;

        for (int weeksAgo = HISTORY_WEEKS; weeksAgo >= 0; weeksAgo--) {
            LocalDate weekStart = DemoDates.monday(today.minusWeeks(weeksAgo));

            for (int day = 0; day < trainingDays.length; day++) {
                LocalDate date = DemoDates.next(weekStart, trainingDays[day]);
                if (!date.isBefore(today)) {
                    continue; // ab heute übernimmt der Scheduler
                }
                // Woche 4 vor heute war Klausurenwoche - eine Lücke macht die Statistik ehrlich.
                if (weeksAgo == 4 && day == 2) {
                    continue;
                }

                Routine routine = rotation.get((completed) % rotation.size());
                int week = HISTORY_WEEKS - weeksAgo;
                session(user, plan, routine, date, week);
                completed++;
            }
        }

        plan.setTotalWorkouts(completed);
        plan.setCompletedWorkouts(completed);
        planRepository.save(plan);
    }

    private void session(User user, WorkoutPlan plan, Routine routine, LocalDate date, int week) {
        List<RoutineExercise> entries =
                routineExerciseRepository.findByRoutineIdOrderByOrderIndexAsc(routine.getId());

        int planned = routine.getEstimatedDurationMinutes() != null
                ? routine.getEstimatedDurationMinutes() : 60;
        // Leichte Streuung um die geplante Dauer, damit die Auswertung nicht wie kopiert aussieht.
        int actual = planned + ((week * 7 + date.getDayOfMonth()) % 13) - 6;

        LocalDateTime start = date.atTime(18, 0);

        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setWorkoutPlan(plan);
        session.setRoutine(routine);
        session.setName(routine.getName());
        session.setWorkoutType("Krafttraining");
        session.setLocation("Fitnessstudio");
        session.setStartTime(start);
        session.setEndTime(start.plusMinutes(actual));
        session.setDurationMinutes(planned);
        session.setActualDurationMinutes(actual);
        session.setIntensity(7 + (week % 3));
        session.setCaloriesBurned(actual * 8);
        session.setIsCompleted(true);
        session.setCompletedAt(start.plusMinutes(actual));
        session.setIsFlexible(false);
        session = sessionRepository.save(session);

        int exerciseOrder = 0;
        for (RoutineExercise entry : entries) {
            double base = entry.getTargetWeight() != null ? entry.getTargetWeight() : 0.0;
            // 2,5 % Steigerung je Woche, auf 2,5-kg-Schritte gerundet - so, wie man es
            // tatsächlich auf die Hantel bekommt.
            double weight = base > 0 ? roundToPlate(base * (1 + 0.025 * week)) : 0.0;
            int targetSets = entry.getTargetSets() != null ? entry.getTargetSets() : 3;
            int repsMin = entry.getTargetRepsMin() != null ? entry.getTargetRepsMin() : 8;
            int repsMax = entry.getTargetRepsMax() != null ? entry.getTargetRepsMax() : 12;

            for (int setNumber = 1; setNumber <= targetSets; setNumber++) {
                ExerciseSet set = new ExerciseSet();
                set.setWorkoutSession(session);
                set.setExercise(entry.getExercise());
                set.setRoutineExerciseId(entry.getId());
                set.setExerciseOrder(exerciseOrder);
                set.setSetNumber(setNumber);
                set.setRestSeconds(entry.getRestSeconds());
                set.setIsCompleted(true);
                set.setCompletedAt(start.plusMinutes(6L * exerciseOrder + 3L * setNumber));

                if (setNumber == 1 && weight > 0) {
                    set.setSetType(SetType.WARMUP);
                    set.setReps(repsMax);
                    set.setWeight(roundToPlate(weight * 0.6));
                    set.setRpe(4);
                } else {
                    set.setSetType(SetType.NORMAL);
                    // Die Wiederholungen fallen über die Sätze - der letzte Satz ist der harte.
                    set.setReps(Math.max(repsMin, repsMax - (setNumber - 1)));
                    set.setWeight(weight > 0 ? weight : null);
                    set.setRpe(Math.min(10, 6 + setNumber));
                }

                if (entry.getExercise().getName().equalsIgnoreCase("Plank")) {
                    set.setReps(null);
                    set.setWeight(null);
                    set.setDurationSeconds(45 + 5 * week);
                }
                setRepository.save(set);
            }
            exerciseOrder++;
        }

        routine.setLastPerformedAt(session.getCompletedAt());
        routine.setPerformCount(routine.getPerformCount() + 1);
        routineRepository.save(routine);
    }

    private double roundToPlate(double weight) {
        return Math.round(weight / 2.5) * 2.5;
    }

    /** Der Katalog hat knapp 900 Zeilen — einmal laden schlägt 30 Einzelabfragen nach Namen. */
    private Map<String, Exercise> catalogByName() {
        Map<String, Exercise> byName = new HashMap<>();
        for (Exercise exercise : exerciseRepository.findAll()) {
            byName.putIfAbsent(exercise.getName().toLowerCase(), exercise);
        }
        return byName;
    }
}
