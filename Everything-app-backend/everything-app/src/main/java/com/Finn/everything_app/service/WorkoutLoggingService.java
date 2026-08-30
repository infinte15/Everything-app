package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.*;
import com.Finn.everything_app.event.ScheduleChangedEvent;
import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.mapper.WorkoutLogMapper;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Der Lebenszyklus eines Trainings: starten, abschliessen, nachschlagen.
 *
 * <p>Kernpunkt ist, dass ein fertiges Training in <em>einem</em> Request gespeichert wird -
 * eine Transaktion, wiederholbar, statt eines HTTP-Aufrufs pro Satz.
 */
@Service
@RequiredArgsConstructor
public class WorkoutLoggingService {

    private static final String DEFAULT_WORKOUT_NAME = "Training";
    private static final String DEFAULT_WORKOUT_TYPE = "STRENGTH";
    private static final int DEFAULT_INTENSITY = 5;

    private final WorkoutSessionRepository sessionRepository;
    private final ExerciseSetRepository setRepository;
    private final ExerciseRepository exerciseRepository;
    private final RoutineRepository routineRepository;
    private final RoutineHabitService routineHabitService;
    private final UserRepository userRepository;
    private final WorkoutPlanService workoutPlanService;
    private final WorkoutLogMapper logMapper;
    private final ProgressionService progressionService;
    private final ExerciseNoteService exerciseNoteService;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== START ====================

    @Transactional
    public ActiveWorkoutDTO start(Long userId, StartWorkoutRequest request) {
        WorkoutSession session;
        Routine routine = null;
        boolean adoptedPlaceholder = false;

        if (request.getSessionId() != null) {
            // Eine bereits eingeplante Einheit wird jetzt trainiert. isFlexible=false pinnt sie,
            // damit der Scheduler sie waehrend des Trainings nicht verschiebt.
            session = sessionRepository.findByIdAndUserId(request.getSessionId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Trainingseinheit nicht gefunden"));
            adoptedPlaceholder = Boolean.TRUE.equals(session.getIsFlexible());
            session.setIsFlexible(false);
            session.setStartTime(LocalDateTime.now());
            routine = session.getRoutine();
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

            session = new WorkoutSession();
            session.setUser(user);
            session.setStartTime(LocalDateTime.now());
            session.setIsFlexible(false);
            session.setIsCompleted(false);
            session.setIntensity(DEFAULT_INTENSITY);
            session.setWorkoutType(DEFAULT_WORKOUT_TYPE);

            if (request.getRoutineId() != null) {
                routine = routineRepository.findByIdAndUserId(request.getRoutineId(), userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Routine nicht gefunden"));
                session.setRoutine(routine);
                session.setWorkoutPlan(routine.getWorkoutPlan());
                session.setName(routine.getName());
            } else {
                session.setName(hasText(request.getName()) ? request.getName() : DEFAULT_WORKOUT_NAME);
            }
        }

        WorkoutSession saved = sessionRepository.save(session);

        // Nur das Festnageln eines Platzhalters aendert den Plan - ein frisches Ad-hoc-Training
        // beginnt jetzt und braucht keinen Solver-Lauf.
        if (adoptedPlaceholder) {
            eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        }

        return buildActiveWorkout(userId, saved, routine);
    }

    private ActiveWorkoutDTO buildActiveWorkout(Long userId, WorkoutSession session, Routine routine) {
        ActiveWorkoutDTO dto = new ActiveWorkoutDTO();
        dto.setSessionId(session.getId());
        dto.setName(session.getName());
        dto.setStartedAt(session.getStartTime());
        dto.setWorkoutPlanId(session.getWorkoutPlan() != null ? session.getWorkoutPlan().getId() : null);

        if (routine == null) {
            return dto;
        }
        dto.setRoutineId(routine.getId());
        dto.setRoutineName(routine.getName());

        List<RoutineExercise> planned = routine.getExercises();
        if (planned.isEmpty()) {
            return dto;
        }

        List<Long> exerciseIds = planned.stream()
                .map(re -> re.getExercise().getId())
                .distinct()
                .toList();

        // Eine Abfrage fuer die Historie aller Uebungen der Routine.
        Map<Long, List<ExerciseSetDTO>> previousByExercise = new HashMap<>();
        Map<Long, Double> recordByExercise = new HashMap<>();
        Map<Long, Long> lastSessionByExercise = new HashMap<>();

        List<ExerciseSet> completed = setRepository.findCompletedSetsForExercises(exerciseIds, userId);
        Map<Long, List<ProgressionService.SessionPerformance>> historyByExercise =
                progressionService.groupHistory(completed);
        Map<Long, String> notesByExercise = exerciseNoteService.getAll(userId, exerciseIds);

        for (ExerciseSet set : completed) {
            Long exerciseId = set.getExercise().getId();
            Long sessionId = set.getWorkoutSession().getId();

            // Die Liste ist nach Startzeit absteigend sortiert - die erste Einheit je Uebung
            // ist damit die zuletzt trainierte.
            Long kept = lastSessionByExercise.putIfAbsent(exerciseId, sessionId);
            // Nur Arbeitssaetze in die "vorher"-Spalte: sie steht Zeile fuer Zeile neben den
            // Arbeitssaetzen von heute. Ein Aufwaermsatz an erster Stelle wuerde die ganze
            // Spalte verschieben - und "45 kg x 8" neben einem 75-kg-Satz behaupten.
            if ((kept == null || kept.equals(sessionId)) && isWorkSet(set)) {
                previousByExercise.computeIfAbsent(exerciseId, k -> new ArrayList<>())
                        .add(logMapper.toSetDTO(set));
            }
            if (set.getWeight() != null) {
                recordByExercise.merge(exerciseId, set.getWeight(), Math::max);
            }
        }

        List<PlannedExerciseDTO> result = new ArrayList<>();
        for (RoutineExercise re : planned) {
            Exercise exercise = re.getExercise();
            PlannedExerciseDTO item = new PlannedExerciseDTO();
            item.setExerciseId(exercise.getId());
            item.setName(exercise.getName());
            item.setImageUrl(exercise.getImageUrl());
            item.setImageUrlEnd(exercise.getImageUrlEnd());
            item.setAnimationUrl(exercise.getAnimationUrl());
            item.setEquipment(exercise.getEquipment());
            item.setPrimaryMuscles(exercise.getPrimaryMuscles().stream()
                    .map(MuscleGroup::getSlug).toList());
            item.setSecondaryMuscles(exercise.getSecondaryMuscles().stream()
                    .map(MuscleGroup::getSlug).toList());
            item.setRoutineExerciseId(re.getId());
            item.setOrderIndex(re.getOrderIndex());
            item.setRestSeconds(re.getRestSeconds() != null
                    ? re.getRestSeconds()
                    : exercise.getDefaultRestSeconds());
            item.setTargetSets(re.getTargetSets());
            item.setTargetRepsMin(re.getTargetRepsMin());
            item.setTargetRepsMax(re.getTargetRepsMax());
            item.setTargetWeight(re.getTargetWeight());
            item.setTargetDurationSeconds(re.getTargetDurationSeconds());
            item.setNotes(re.getNotes());
            item.setSupersetGroup(re.getSupersetGroup());
            item.setPrevious(previousByExercise.getOrDefault(exercise.getId(), List.of()));
            item.setPersonalRecordWeight(recordByExercise.get(exercise.getId()));
            item.setProgression(progressionService.suggest(re,
                    historyByExercise.getOrDefault(exercise.getId(), List.of())));
            item.setExerciseNote(notesByExercise.get(exercise.getId()));
            result.add(item);
        }
        dto.setPlannedExercises(result);
        return dto;
    }

    // ==================== ABSCHLUSS ====================

    @Transactional
    public WorkoutSession finish(Long userId, Long sessionId, FinishWorkoutRequest request) {
        WorkoutSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Trainingseinheit nicht gefunden"));

        boolean wasCompleted = Boolean.TRUE.equals(session.getIsCompleted());

        // Vollstaendig ersetzen statt abgleichen: macht den Aufruf wiederholbar.
        setRepository.deleteByWorkoutSessionId(sessionId);
        persistSets(session, request.getExercises());

        LocalDateTime now = LocalDateTime.now();
        if (request.getStartTime() != null) {
            session.setStartTime(request.getStartTime());
        }
        if (session.getStartTime() == null) {
            session.setStartTime(now);
        }
        session.setEndTime(request.getEndTime() != null ? request.getEndTime() : now);
        session.setDurationMinutes(resolveDuration(request, session));
        session.setIsCompleted(true);
        session.setCompletedAt(now);
        session.setActualDurationMinutes(session.getDurationMinutes());
        session.setIsFlexible(false);

        if (request.getNotes() != null) session.setNotes(request.getNotes());
        if (request.getIntensity() != null) session.setIntensity(request.getIntensity());
        if (request.getCaloriesBurned() != null) session.setCaloriesBurned(request.getCaloriesBurned());
        if (request.getLocation() != null) session.setLocation(request.getLocation());
        if (session.getIntensity() == null) session.setIntensity(DEFAULT_INTENSITY);

        WorkoutSession saved = sessionRepository.save(session);

        // Zaehler nur beim ersten Abschluss hochzaehlen, sonst zaehlt ein wiederholter
        // Aufruf dasselbe Training mehrfach.
        if (!wasCompleted) {
            if (saved.getWorkoutPlan() != null) {
                workoutPlanService.incrementCompletedWorkouts(saved.getWorkoutPlan().getId());
            }
            Routine routine = saved.getRoutine();
            if (routine != null) {
                routine.setLastPerformedAt(now);
                routine.setPerformCount((routine.getPerformCount() == null ? 0 : routine.getPerformCount()) + 1);
                routineRepository.save(routine);

                // Traegt das Training in die Gewohnheit der Routine ein, damit die Streak im
                // Habit-Space mitlaeuft. Nach dem Ende der Einheit datiert, nicht nach "jetzt":
                // ein nachtraeglich protokolliertes Training gehoert an seinen eigenen Tag,
                // sonst haengt eine Woche alte Einheit die heutige Streak weiter.
                LocalDateTime trainedAt = saved.getEndTime() != null ? saved.getEndTime() : now;
                routineHabitService.markTrained(routine, trainedAt.toLocalDate());
            }
        }

        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return saved;
    }

    /** Ein bereits absolviertes Training nachtraeglich protokollieren. */
    @Transactional
    public WorkoutSession logCompleted(Long userId, FinishWorkoutRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setIsFlexible(false);
        session.setIsCompleted(false);
        session.setWorkoutType(DEFAULT_WORKOUT_TYPE);
        session.setIntensity(DEFAULT_INTENSITY);
        session.setStartTime(request.getStartTime() != null ? request.getStartTime() : LocalDateTime.now());

        if (request.getRoutineId() != null) {
            Routine routine = routineRepository.findByIdAndUserId(request.getRoutineId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Routine nicht gefunden"));
            session.setRoutine(routine);
            session.setWorkoutPlan(routine.getWorkoutPlan());
            session.setName(routine.getName());
        }
        if (hasText(request.getName())) {
            session.setName(request.getName());
        }
        if (!hasText(session.getName())) {
            session.setName(DEFAULT_WORKOUT_NAME);
        }

        WorkoutSession created = sessionRepository.save(session);
        return finish(userId, created.getId(), request);
    }

    private void persistSets(WorkoutSession session, List<FinishWorkoutRequest.LoggedExercise> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return;
        }

        Set<Long> exerciseIds = exercises.stream()
                .map(FinishWorkoutRequest.LoggedExercise::getExerciseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, Exercise> byId = exerciseRepository.findAllById(exerciseIds).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));

        List<ExerciseSet> toSave = new ArrayList<>();
        // Abfall- und Rest-Pause-Saetze zeigen auf ihren Arbeitssatz. Dessen ID gibt es erst
        // nach dem Speichern, deshalb wird die Verknuepfung unten in einem zweiten Durchgang
        // aufgeloest - der Client schickt nur die Satz-Nummer innerhalb des Blocks.
        Map<ExerciseSet, int[]> pendingParents = new LinkedHashMap<>();
        Map<String, ExerciseSet> byBlockAndNumber = new HashMap<>();

        for (int blockIndex = 0; blockIndex < exercises.size(); blockIndex++) {
            FinishWorkoutRequest.LoggedExercise block = exercises.get(blockIndex);
            Exercise exercise = byId.get(block.getExerciseId());
            if (exercise == null) {
                throw new BadRequestException("Übung nicht gefunden: " + block.getExerciseId());
            }

            int order = block.getOrderIndex() != null ? block.getOrderIndex() : blockIndex;
            List<ExerciseSetDTO> sets = block.getSets() != null ? block.getSets() : List.of();

            for (int i = 0; i < sets.size(); i++) {
                ExerciseSetDTO dto = sets.get(i);
                ExerciseSet set = new ExerciseSet();
                set.setWorkoutSession(session);
                set.setExercise(exercise);
                set.setSetNumber(dto.getSetNumber() != null ? dto.getSetNumber() : i + 1);
                set.setReps(dto.getReps());
                set.setWeight(dto.getWeight());
                set.setDurationSeconds(dto.getDurationSeconds());
                set.setNotes(dto.getNotes());
                set.setIsCompleted(dto.getIsCompleted() != null ? dto.getIsCompleted() : Boolean.TRUE);
                set.setSetType(SetType.orDefault(dto.getSetType()));
                set.setRestSeconds(dto.getRestSeconds() != null ? dto.getRestSeconds() : block.getRestSeconds());
                set.setRpe(dto.getRpe());
                set.setExerciseOrder(order);
                set.setRoutineExerciseId(dto.getRoutineExerciseId() != null
                        ? dto.getRoutineExerciseId()
                        : block.getRoutineExerciseId());
                if (Boolean.TRUE.equals(set.getIsCompleted())) {
                    set.setCompletedAt(dto.getCompletedAt() != null ? dto.getCompletedAt() : LocalDateTime.now());
                }
                toSave.add(set);
                byBlockAndNumber.put(blockIndex + ":" + set.getSetNumber(), set);
                if (dto.getParentSetNumber() != null) {
                    pendingParents.put(set, new int[]{blockIndex, dto.getParentSetNumber()});
                }
            }
        }
        setRepository.saveAll(toSave);
        linkParents(pendingParents, byBlockAndNumber);
    }

    /**
     * Zweiter Durchgang: aus der Satz-Nummer wird die ID des gespeicherten Arbeitssatzes.
     *
     * <p>Ein Verweis auf sich selbst oder auf eine Nummer, die es im Block nicht gibt, wird
     * still verworfen - ein kaputter Zusatzsatz ist ein normaler Satz, aber kein Grund, ein
     * fertig trainiertes Workout abzulehnen.
     */
    private void linkParents(Map<ExerciseSet, int[]> pending, Map<String, ExerciseSet> byNumber) {
        if (pending.isEmpty()) {
            return;
        }
        List<ExerciseSet> linked = new ArrayList<>();
        pending.forEach((child, ref) -> {
            ExerciseSet parent = byNumber.get(ref[0] + ":" + ref[1]);
            if (parent == null || parent == child || parent.getId() == null) {
                return;
            }
            child.setParentSetId(parent.getId());
            linked.add(child);
        });
        if (!linked.isEmpty()) {
            setRepository.saveAll(linked);
        }
    }

    private Integer resolveDuration(FinishWorkoutRequest request, WorkoutSession session) {
        if (request.getDurationMinutes() != null && request.getDurationMinutes() > 0) {
            return request.getDurationMinutes();
        }
        LocalDateTime start = session.getStartTime();
        LocalDateTime end = session.getEndTime();
        if (start == null || end == null) {
            return 1;
        }
        long minutes = Duration.between(start, end).toMinutes();
        return (int) Math.max(1, minutes);
    }

    // ==================== HISTORIE / BESTLEISTUNGEN ====================

    @Transactional(readOnly = true)
    public WorkoutSession getSessionDetail(Long userId, Long sessionId) {
        return sessionRepository.findDetailByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Trainingseinheit nicht gefunden"));
    }

    @Transactional(readOnly = true)
    public List<ExerciseHistoryEntryDTO> getExerciseHistory(Long userId, Long exerciseId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<Long> sessionIds = setRepository.findRecentSessionIdsForExercise(
                exerciseId, userId, PageRequest.of(0, safeLimit));
        if (sessionIds.isEmpty()) {
            return List.of();
        }

        Map<Long, ExerciseHistoryEntryDTO> entries = new LinkedHashMap<>();
        for (ExerciseSet set : setRepository.findSetsForExerciseInSessions(exerciseId, sessionIds)) {
            WorkoutSession session = set.getWorkoutSession();
            ExerciseHistoryEntryDTO entry = entries.computeIfAbsent(session.getId(), id -> {
                ExerciseHistoryEntryDTO created = new ExerciseHistoryEntryDTO();
                created.setSessionId(session.getId());
                created.setSessionName(session.getName());
                created.setPerformedAt(session.getStartTime());
                return created;
            });

            entry.getSets().add(logMapper.toSetDTO(set));
            if (!Boolean.TRUE.equals(set.getIsCompleted())) {
                continue;
            }
            entry.setTotalSets(entry.getTotalSets() + 1);
            entry.setTotalVolumeKg(entry.getTotalVolumeKg() + logMapper.volumeOf(set));
            if (set.getWeight() != null
                    && (entry.getBestSetWeight() == null || set.getWeight() > entry.getBestSetWeight())) {
                entry.setBestSetWeight(set.getWeight());
                entry.setBestSetReps(set.getReps());
                entry.setEstimated1RM(epley(set.getWeight(), set.getReps()));
            }
        }
        return new ArrayList<>(entries.values());
    }

    @Transactional(readOnly = true)
    public PersonalRecordDTO getPersonalRecords(Long userId, Long exerciseId) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ResourceNotFoundException("Übung nicht gefunden"));

        PersonalRecordDTO dto = new PersonalRecordDTO();
        dto.setExerciseId(exerciseId);
        dto.setExerciseName(exercise.getName());

        // Die Historie ist pro Uebung klein genug, um sie im Speicher auszuwerten - das spart
        // eine zweite, native Abfrage nur fuer den Zeitpunkt der Bestleistung.
        List<ExerciseSet> sets = setRepository.findCompletedSetsForExercises(List.of(exerciseId), userId);
        if (sets.isEmpty()) {
            dto.setTotalSetsAllTime(0);
            return dto;
        }

        double maxSetVolume = 0;
        for (ExerciseSet set : sets) {
            WorkoutSession session = set.getWorkoutSession();
            LocalDateTime performedAt = session.getStartTime();

            if (set.getWeight() != null && (dto.getMaxWeight() == null || set.getWeight() > dto.getMaxWeight())) {
                dto.setMaxWeight(set.getWeight());
                dto.setMaxWeightReps(set.getReps());
                dto.setMaxWeightAt(performedAt);
                dto.setMaxWeightSessionId(session.getId());
            }
            if (set.getReps() != null && (dto.getMaxReps() == null || set.getReps() > dto.getMaxReps())) {
                dto.setMaxReps(set.getReps());
            }
            maxSetVolume = Math.max(maxSetVolume, logMapper.volumeOf(set));

            Double e1rm = epley(set.getWeight(), set.getReps());
            if (e1rm != null && (dto.getBest1RM() == null || e1rm > dto.getBest1RM())) {
                dto.setBest1RM(e1rm);
            }
            if (performedAt != null) {
                if (dto.getFirstPerformedAt() == null || performedAt.isBefore(dto.getFirstPerformedAt())) {
                    dto.setFirstPerformedAt(performedAt);
                }
                if (dto.getLastPerformedAt() == null || performedAt.isAfter(dto.getLastPerformedAt())) {
                    dto.setLastPerformedAt(performedAt);
                }
            }
        }
        dto.setMaxSetVolumeKg(maxSetVolume);
        dto.setTotalSetsAllTime(sets.size());
        return dto;
    }

    /**
     * Ein eigenstaendiger Arbeitssatz - kein Aufwaermsatz und kein Zusatzsatz, der an einem
     * anderen haengt. Dieselbe Abgrenzung wie in {@code ProgressionService}.
     */
    private boolean isWorkSet(ExerciseSet set) {
        return set.getParentSetId() == null
                && SetType.countsTowardVolume(set.getSetType());
    }

    /** Epley-Formel fuer das geschaetzte Ein-Wiederholungs-Maximum. */
    private Double epley(Double weight, Integer reps) {
        if (weight == null || reps == null || reps <= 0) return null;
        return weight * (1 + reps / 30.0);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
