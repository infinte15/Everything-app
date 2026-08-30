package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.MuscleRecoveryDTO;
import com.Finn.everything_app.dto.MuscleVolumeDTO;
import com.Finn.everything_app.dto.VolumePointDTO;
import com.Finn.everything_app.dto.WeeklyStatsDTO;
import com.Finn.everything_app.model.MuscleGroup;
import com.Finn.everything_app.model.WorkoutPlan;
import com.Finn.everything_app.repository.WorkoutStatsRepository;
import com.Finn.everything_app.repository.projection.ExerciseOneRmRow;
import com.Finn.everything_app.repository.projection.MuscleVolumeRow;
import com.Finn.everything_app.repository.projection.RecoverySetRow;
import com.Finn.everything_app.repository.projection.WeekBucketRow;
import com.Finn.everything_app.repository.projection.WeekVolumeRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkoutStatsService {

    /** Historie fuer die Serien-Berechnung. */
    private static final int STREAK_LOOKBACK_WEEKS = 52;

    // ── Erholungsmodell ─────────────────────────────────────────────────────────
    //
    // Die Ermuedung eines Satzes ist  Last x Wdh x min(1, Last/1RM)^1,5  und klingt mit der
    // Zeit exponentiell ab. Der Exponent 1,5 sorgt dafuer, dass drei schwere Saetze mehr
    // nachwirken als sechs leichte mit demselben Volumen - genau der Unterschied, den eine
    // reine Volumenrechnung nicht sieht.
    //
    // Die Zahl fuer sich sagt nichts: 12 000 kann fuer den einen ein Aufwaermen und fuer den
    // anderen die Woche sein. Deshalb wird gegen die *eigene* haerteste Einheit der letzten
    // Wochen normiert - ein Massstab, der mitwaechst.

    /** Halbwertszeit der Ermuedung. Nach zwei Tagen die Haelfte, nach vier ein Viertel. */
    private static final double FATIGUE_HALF_LIFE_DAYS = 2.0;
    /** Fenster fuer den Vergleichsmassstab "eine harte Einheit fuer diesen Muskel". */
    private static final int RECOVERY_LOOKBACK_DAYS = 56;
    /** Unterhalb dieses Anteils gilt der Muskel als erholt. */
    private static final double RECOVERED_BELOW = 0.25;
    /** Ueberproportionale Gewichtung schwerer Saetze. */
    private static final double INTENSITY_EXPONENT = 1.5;
    /** Ohne bekanntes 1RM: Koerpergewichts- und Maschinenarbeit als mittlere Intensitaet. */
    private static final double DEFAULT_INTENSITY = 0.6;
    /** Punkte der Volumen-Kurve. */
    private static final int VOLUME_SERIES_WEEKS = 8;

    private final WorkoutStatsRepository statsRepository;
    private final WorkoutPlanService planService;

    @Transactional(readOnly = true)
    public WeeklyStatsDTO getWeeklyStats(Long userId, LocalDate weekStartParam) {
        LocalDate weekStart = mondayOf(weekStartParam != null ? weekStartParam : LocalDate.now());
        LocalDate seriesStart = weekStart.minusWeeks(VOLUME_SERIES_WEEKS - 1L);
        LocalDate lookbackStart = weekStart.minusWeeks(STREAK_LOOKBACK_WEEKS);

        Map<LocalDate, WeekBucketRow> sessionBuckets = index(
                statsRepository.weeklySessionBuckets(userId, lookbackStart.atStartOfDay()),
                WeekBucketRow::getWeekStart);
        Map<LocalDate, WeekVolumeRow> volumeBuckets = index(
                statsRepository.weeklyVolumeBuckets(userId, seriesStart.atStartOfDay()),
                WeekVolumeRow::getWeekStart);

        WeeklyStatsDTO dto = new WeeklyStatsDTO();
        dto.setWeekStart(weekStart);

        WeekBucketRow current = sessionBuckets.get(weekStart);
        dto.setWorkoutsCompleted(current != null ? intOf(current.getWorkouts()) : 0);
        dto.setTotalMinutes(current != null ? intOf(current.getMinutes()) : 0);

        WeekVolumeRow currentVolume = volumeBuckets.get(weekStart);
        dto.setTotalVolumeKg(currentVolume != null ? doubleOf(currentVolume.getVolume()) : 0d);
        dto.setTotalSets(currentVolume != null ? intOf(currentVolume.getSetCount()) : 0);

        WorkoutPlan activePlan = planService.getActivePlan(userId);
        dto.setWorkoutGoal(activePlan != null ? activePlan.getWorkoutsPerWeek() : null);

        Set<LocalDate> trainedWeeks = new HashSet<>();
        sessionBuckets.forEach((week, row) -> {
            if (intOf(row.getWorkouts()) > 0) {
                trainedWeeks.add(week);
            }
        });
        dto.setCurrentStreakWeeks(currentStreak(trainedWeeks, weekStart));
        dto.setLongestStreakWeeks(longestStreak(trainedWeeks, lookbackStart, weekStart));

        List<VolumePointDTO> series = new ArrayList<>();
        for (int i = 0; i < VOLUME_SERIES_WEEKS; i++) {
            LocalDate week = seriesStart.plusWeeks(i);
            WeekVolumeRow volume = volumeBuckets.get(week);
            WeekBucketRow sessions = sessionBuckets.get(week);
            series.add(new VolumePointDTO(
                    week,
                    volume != null ? doubleOf(volume.getVolume()) : 0d,
                    sessions != null ? intOf(sessions.getWorkouts()) : 0,
                    sessions != null ? intOf(sessions.getMinutes()) : 0
            ));
        }
        dto.setVolumeSeries(series);
        return dto;
    }

    /**
     * Belastung je Muskelgruppe. Es werden immer alle 17 Muskeln zurueckgegeben - der Client
     * kann die Silhouette dann ohne Fallunterscheidung einfaerben.
     */
    @Transactional(readOnly = true)
    public List<MuscleVolumeDTO> getMuscleVolume(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDate to = endDate != null ? endDate : LocalDate.now();
        LocalDate from = startDate != null ? startDate : to.minusWeeks(1);

        List<MuscleVolumeRow> rows = statsRepository.muscleVolume(
                userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        return toMuscleVolumeDTOs(rows);
    }

    /** Rein rechnerischer Teil - ohne Datenbank, damit direkt testbar. */
    List<MuscleVolumeDTO> toMuscleVolumeDTOs(List<MuscleVolumeRow> rows) {
        Map<MuscleGroup, MuscleVolumeRow> byMuscle = new EnumMap<>(MuscleGroup.class);
        for (MuscleVolumeRow row : rows) {
            MuscleGroup muscle = MuscleGroup.fromSlugOrNull(row.getMuscle());
            if (muscle != null) {
                byMuscle.put(muscle, row);
            }
        }

        double maxVolume = byMuscle.values().stream()
                .mapToDouble(r -> doubleOf(r.getVolume())).max().orElse(0d);
        double maxSets = byMuscle.values().stream()
                .mapToDouble(r -> doubleOf(r.getWeightedSets())).max().orElse(0d);
        // Bei reinem Koerpergewichts-Training ist das Volumen ueberall 0 - dann traegt die
        // Satzanzahl die Einfaerbung, sonst bliebe die Grafik leer.
        boolean useVolume = maxVolume > 0;
        double max = useVolume ? maxVolume : maxSets;

        // bodyMapValues() statt values(): CARDIO hat keine Flaeche in der Koerper-Grafik und
        // gehoert deshalb nicht in die Muskelbilanz.
        List<MuscleVolumeDTO> result = new ArrayList<>(MuscleGroup.bodyMapValues().size());
        for (MuscleGroup muscle : MuscleGroup.bodyMapValues()) {
            MuscleVolumeRow row = byMuscle.get(muscle);
            double volume = row != null ? doubleOf(row.getVolume()) : 0d;
            double sets = row != null ? doubleOf(row.getWeightedSets()) : 0d;
            long sessions = row != null && row.getSessionCount() != null ? row.getSessionCount() : 0L;
            double basis = useVolume ? volume : sets;
            double share = max > 0 ? Math.min(1d, basis / max) : 0d;

            result.add(new MuscleVolumeDTO(
                    muscle.getSlug(), muscle.getLabel(), volume, sets, sessions, share));
        }
        return result;
    }

    /** Ununterbrochene Wochen mit mindestens einem Training, rueckwaerts ab der aktuellen Woche. */
    int currentStreak(Set<LocalDate> trainedWeeks, LocalDate weekStart) {
        int streak = 0;
        // Die laufende Woche darf noch leer sein, ohne die Serie zu brechen.
        LocalDate cursor = trainedWeeks.contains(weekStart) ? weekStart : weekStart.minusWeeks(1);
        while (trainedWeeks.contains(cursor)) {
            streak++;
            cursor = cursor.minusWeeks(1);
        }
        return streak;
    }

    int longestStreak(Set<LocalDate> trainedWeeks, LocalDate from, LocalDate to) {
        int longest = 0;
        int running = 0;
        for (LocalDate week = from; !week.isAfter(to); week = week.plusWeeks(1)) {
            if (trainedWeeks.contains(week)) {
                running++;
                longest = Math.max(longest, running);
            } else {
                running = 0;
            }
        }
        return longest;
    }

    private <T> Map<LocalDate, T> index(List<T> rows, java.util.function.Function<T, java.sql.Date> key) {
        Map<LocalDate, T> map = new HashMap<>();
        for (T row : rows) {
            java.sql.Date date = key.apply(row);
            if (date != null) {
                map.put(date.toLocalDate(), row);
            }
        }
        return map;
    }

    private static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static int intOf(Long value) {
        return value != null ? value.intValue() : 0;
    }

    private static double doubleOf(Double value) {
        return value != null ? value : 0d;
    }

    // ── Erholung ────────────────────────────────────────────────────────────────

    /**
     * Erholungsstand je Muskelgruppe - liefert wie {@link #getMuscleVolume} immer jede
     * zeichenbare Flaeche, auch die unbelasteten.
     */
    @Transactional(readOnly = true)
    public List<MuscleRecoveryDTO> getRecovery(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<RecoverySetRow> rows =
                statsRepository.recoverySets(userId, now.minusDays(RECOVERY_LOOKBACK_DAYS));

        Map<Long, Double> oneRepMax = new HashMap<>();
        for (ExerciseOneRmRow row : statsRepository.bestOneRepMaxPerExercise(userId)) {
            if (row.getExerciseId() != null && row.getBestOneRm() != null) {
                oneRepMax.put(row.getExerciseId(), row.getBestOneRm());
            }
        }

        // Ueber die Enum-Konstante schluesseln, nicht ueber die Zeichenkette: die Spalte
        // enthaelt den Enum-Namen ("LOWER_BACK"), die Antwort den Slug ("lower back").
        Map<MuscleGroup, Double> current = new EnumMap<>(MuscleGroup.class);
        Map<MuscleGroup, LocalDateTime> lastTrained = new EnumMap<>(MuscleGroup.class);
        // Massstab: die staerkste einzelne Einheit je Muskel im Rueckblick-Fenster.
        Map<MuscleGroup, Map<Long, Double>> perSession = new EnumMap<>(MuscleGroup.class);

        for (RecoverySetRow row : rows) {
            MuscleGroup muscle = MuscleGroup.fromSlugOrNull(row.getMuscle());
            LocalDateTime at = row.getPerformedAt();
            if (muscle == null || at == null) continue;

            double load = load(row, oneRepMax);
            if (load <= 0) continue;

            double ageDays = Math.max(0, Duration.between(at, now).toMinutes() / 1440.0);
            current.merge(muscle, load * Math.pow(0.5, ageDays / FATIGUE_HALF_LIFE_DAYS),
                    Double::sum);
            perSession.computeIfAbsent(muscle, m -> new HashMap<>())
                    .merge(row.getSessionId(), load, Double::sum);
            lastTrained.merge(muscle, at, (a, b) -> a.isAfter(b) ? a : b);
        }

        List<MuscleRecoveryDTO> result = new ArrayList<>();
        for (MuscleGroup muscle : MuscleGroup.bodyMapValues()) {
            double reference = perSession.getOrDefault(muscle, Map.of()).values().stream()
                    .mapToDouble(Double::doubleValue).max().orElse(0d);
            double raw = current.getOrDefault(muscle, 0d);

            // Ohne Massstab gibt es keine Aussage - dann steht der Muskel als erholt da,
            // was auch stimmt: es wurde nie etwas dafuer getan.
            double fatigue = reference <= 0 ? 0d : Math.min(1d, raw / reference);
            result.add(new MuscleRecoveryDTO(
                    muscle.getSlug(),
                    muscle.getLabel(),
                    round(fatigue),
                    round(1d - fatigue),
                    lastTrained.get(muscle),
                    hoursToReady(raw, reference)));
        }
        return result;
    }

    /** Ermuedung eines Satzes: Last x Wdh, ueberproportional gewichtet nach Intensitaet. */
    private double load(RecoverySetRow row, Map<Long, Double> oneRepMax) {
        int reps = row.getReps() != null ? row.getReps() : 0;
        if (reps <= 0) return 0d;
        double weight = row.getWeight() != null ? row.getWeight() : 0d;
        double factor = row.getFactor() != null ? row.getFactor() : 1d;

        Double max = oneRepMax.get(row.getExerciseId());
        double intensity = (max != null && max > 0 && weight > 0)
                ? Math.min(1d, weight / max)
                : DEFAULT_INTENSITY;

        // Koerpergewichtsarbeit wird mit 0 kg geloggt und waere sonst gar keine Belastung.
        double work = weight > 0 ? weight * reps : reps;
        return work * Math.pow(intensity, INTENSITY_EXPONENT) * factor;
    }

    /**
     * Stunden, bis die Ermuedung unter {@link #RECOVERED_BELOW} des Massstabs faellt.
     *
     * <p>Umkehrung des Abklingens: aus {@code raw * 0,5^(t/HL) = Schwelle} folgt
     * {@code t = HL * log2(raw / Schwelle)}.
     */
    private int hoursToReady(double raw, double reference) {
        double threshold = reference * RECOVERED_BELOW;
        if (threshold <= 0 || raw <= threshold) return 0;
        double days = FATIGUE_HALF_LIFE_DAYS * (Math.log(raw / threshold) / Math.log(2));
        // Aufrunden, aber nicht auf Rechenrauschen: exakt 24,000000000000004 Stunden sind
        // 24 und nicht 25.
        return (int) Math.ceil(days * 24 - 1e-6);
    }

    private static double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}
