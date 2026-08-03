package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.MuscleVolumeDTO;
import com.Finn.everything_app.dto.VolumePointDTO;
import com.Finn.everything_app.dto.WeeklyStatsDTO;
import com.Finn.everything_app.model.MuscleGroup;
import com.Finn.everything_app.model.WorkoutPlan;
import com.Finn.everything_app.repository.WorkoutStatsRepository;
import com.Finn.everything_app.repository.projection.MuscleVolumeRow;
import com.Finn.everything_app.repository.projection.WeekBucketRow;
import com.Finn.everything_app.repository.projection.WeekVolumeRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkoutStatsService {

    /** Historie fuer die Serien-Berechnung. */
    private static final int STREAK_LOOKBACK_WEEKS = 52;
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

        List<MuscleVolumeDTO> result = new ArrayList<>(MuscleGroup.values().length);
        for (MuscleGroup muscle : MuscleGroup.values()) {
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
}
