package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.WorkoutSessionDetailDTO;
import com.Finn.everything_app.mapper.WorkoutLogMapper;
import com.Finn.everything_app.model.Exercise;
import com.Finn.everything_app.model.ExerciseSet;
import com.Finn.everything_app.model.MuscleGroup;
import com.Finn.everything_app.model.SetType;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.model.WorkoutSession;
import com.Finn.everything_app.repository.ExerciseRepository;
import com.Finn.everything_app.repository.ExerciseSetRepository;
import com.Finn.everything_app.repository.UserRepository;
import com.Finn.everything_app.repository.WorkoutSessionRepository;
import com.Finn.everything_app.repository.WorkoutStatsRepository;
import com.Finn.everything_app.repository.projection.MuscleVolumeRow;
import com.Finn.everything_app.repository.projection.WeekVolumeRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Welche Satzarten ins Volumen zaehlen - und welche nicht.
 *
 * <p><b>Warum als eigener Test.</b> Die Regel steht an drei Stellen: in
 * {@link SetType#countsTowardVolume(SetType)}, in der Auswertungs-SQL und in der JPQL-Summe je
 * Einheit. Postgres kennt das Java-Enum nicht, die Bedingung laesst sich also nicht an einer
 * Stelle buendeln - was sie zum klassischen Ort fuer stilles Auseinanderlaufen macht.
 *
 * <p><b>Die Regel.</b> Ein Rest-Pause-Cluster haengt am Arbeitssatz, dessen {@code reps}
 * bereits die Summe aller Cluster tragen - mitzaehlen hiesse doppelt zaehlen. Ein Abfallsatz
 * ist dagegen zusaetzliche Arbeit mit eigener Last und zaehlt voll. Aufwaermsaetze zaehlen
 * nicht, seit die Rampe sie automatisch anlegt.
 */
@SpringBootTest
@Transactional
class SetVolumeRuleTest {

    @Autowired UserRepository userRepository;
    @Autowired ExerciseRepository exerciseRepository;
    @Autowired WorkoutSessionRepository sessionRepository;
    @Autowired ExerciseSetRepository setRepository;
    @Autowired WorkoutStatsRepository statsRepository;
    @Autowired WorkoutLogMapper mapper;

    private User user;
    private Exercise exercise;
    private WorkoutSession session;

    /** Arbeit, die zaehlen muss: 3 x 60 kg x 5 = 900, plus Abfallsatz 40 x 8 = 320. */
    private static final double EXPECTED_VOLUME = 3 * 60 * 5 + 40 * 8;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("volume-tester");
        user.setEmail("volume@test.local");
        user.setPasswordHash("x");
        user = userRepository.save(user);

        exercise = new Exercise();
        exercise.setName("bench press");
        exercise.setMuscleGroup(MuscleGroup.CHEST.getSlug());
        exercise.setPrimaryMuscles(new LinkedHashSet<>(List.of(MuscleGroup.CHEST)));
        exercise = exerciseRepository.save(exercise);

        session = new WorkoutSession();
        session.setUser(user);
        session.setName("Push");
        // Mitten in der Woche, damit der Wochen-Bucket nicht an einer Zeitzonengrenze haengt.
        session.setStartTime(LocalDateTime.now().withHour(12).minusDays(1));
        session.setEndTime(session.getStartTime().plusHours(1));
        session.setDurationMinutes(60);
        session.setIsCompleted(true);
        session = sessionRepository.save(session);

        ExerciseSet warmup = save(1, 20.0, 10, SetType.WARMUP, null);
        ExerciseSet a = save(2, 60.0, 5, SetType.NORMAL, null);
        save(3, 60.0, 5, SetType.NORMAL, null);
        ExerciseSet c = save(4, 60.0, 5, SetType.NORMAL, null);
        save(5, 40.0, 8, SetType.DROP, c.getId());
        // Der Arbeitssatz c trug schon alle Cluster-Wiederholungen; der Burst ist nur der
        // Nachweis, wie sie zustande kamen.
        save(6, 60.0, 3, SetType.RESTPAUSE, c.getId());

        assertNotNull(warmup.getId());
        assertNotNull(a.getId());
    }

    private ExerciseSet save(int number, Double weight, Integer reps, SetType type, Long parent) {
        ExerciseSet set = new ExerciseSet();
        set.setWorkoutSession(session);
        set.setExercise(exercise);
        set.setSetNumber(number);
        set.setWeight(weight);
        set.setReps(reps);
        set.setSetType(type);
        set.setParentSetId(parent);
        set.setIsCompleted(true);
        set.setExerciseOrder(0);
        return setRepository.saveAndFlush(set);
    }

    @Test
    void dieRegelStehtImEnum() {
        assertTrue(SetType.countsTowardVolume(SetType.NORMAL));
        assertTrue(SetType.countsTowardVolume(null), "null wird als NORMAL gelesen");
        assertTrue(SetType.countsTowardVolume(SetType.DROP), "Abfallsatz ist eigene Arbeit");
        assertFalse(SetType.countsTowardVolume(SetType.WARMUP));
        assertFalse(SetType.countsTowardVolume(SetType.RESTPAUSE), "steckt schon im Elternsatz");
    }

    @Test
    void dieEinheitZaehltNurArbeitssaetze() {
        session.setExerciseSets(new ArrayList<>(
                setRepository.findByWorkoutSessionIdOrderBySetNumberAsc(session.getId())));

        WorkoutSessionDetailDTO dto = mapper.toDetail(session);

        assertEquals(EXPECTED_VOLUME, dto.getTotalVolumeKg(), 0.001);
        assertEquals(4, dto.getTotalSets(), "3 Arbeitssaetze + 1 Abfallsatz");
        // Die ausgeschlossenen Saetze bleiben trotzdem sichtbar - das Protokoll ist vollstaendig.
        assertEquals(6, dto.getExercises().get(0).getSets().size());
    }

    @Test
    void dieWochenauswertungZaehltNurArbeitssaetze() {
        List<WeekVolumeRow> rows = statsRepository.weeklyVolumeBuckets(
                user.getId(), LocalDateTime.now().minusDays(30));

        double volume = rows.stream().mapToDouble(r -> r.getVolume().doubleValue()).sum();
        long sets = rows.stream().mapToLong(r -> r.getSetCount().longValue()).sum();

        assertEquals(EXPECTED_VOLUME, volume, 0.001);
        assertEquals(4, sets);
    }

    @Test
    void dieMuskelbilanzZaehltNurArbeitssaetze() {
        List<MuscleVolumeRow> rows = statsRepository.muscleVolume(
                user.getId(),
                LocalDateTime.now().minusDays(30),
                LocalDateTime.now().plusDays(1));

        MuscleVolumeRow chest = rows.stream()
                .filter(r -> "CHEST".equalsIgnoreCase(r.getMuscle()))
                .findFirst()
                .orElseThrow();

        assertEquals(EXPECTED_VOLUME, chest.getVolume(), 0.001);
    }
}
