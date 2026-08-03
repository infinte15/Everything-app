package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.MuscleVolumeDTO;
import com.Finn.everything_app.model.MuscleGroup;
import com.Finn.everything_app.repository.WorkoutStatsRepository;
import com.Finn.everything_app.repository.projection.MuscleVolumeRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WorkoutStatsServiceTest {

    @Mock WorkoutStatsRepository statsRepository;
    @Mock WorkoutPlanService planService;

    @InjectMocks
    WorkoutStatsService service;

    private record Row(String muscle, Double volume, Double weightedSets, Long sessionCount)
            implements MuscleVolumeRow {
        @Override public String getMuscle() { return muscle; }
        @Override public Double getVolume() { return volume; }
        @Override public Double getWeightedSets() { return weightedSets; }
        @Override public Long getSessionCount() { return sessionCount; }
    }

    private Map<String, MuscleVolumeDTO> bySlug(List<MuscleVolumeDTO> list) {
        return list.stream().collect(Collectors.toMap(MuscleVolumeDTO::muscle, Function.identity()));
    }

    // Die Körper-Grafik färbt bedingungslos alle Flächen ein - deshalb müssen auch
    // untrainierte Muskeln mit 0 in der Antwort stehen.
    @Test
    void muscleVolumeReturnsAllSeventeenMusclesZeroFilled() {
        List<MuscleVolumeDTO> result = service.toMuscleVolumeDTOs(List.of(
                new Row("chest", 4000.0, 8.0, 2L)));

        assertEquals(MuscleGroup.values().length, result.size());
        assertEquals(17, result.size());

        var byMuscle = bySlug(result);
        assertEquals(1.0, byMuscle.get("chest").share());
        assertEquals(0.0, byMuscle.get("hamstrings").volumeKg());
        assertEquals(0.0, byMuscle.get("hamstrings").share());
        assertEquals("Beinbeuger", byMuscle.get("hamstrings").label());
    }

    @Test
    void muscleVolumeSharesAreRelativeToTheStrongestMuscle() {
        List<MuscleVolumeDTO> result = service.toMuscleVolumeDTOs(List.of(
                new Row("chest", 4000.0, 8.0, 2L),
                new Row("triceps", 1000.0, 4.0, 2L)));

        var byMuscle = bySlug(result);
        assertEquals(1.0, byMuscle.get("chest").share());
        assertEquals(0.25, byMuscle.get("triceps").share(), 0.0001);
    }

    // Bei reinem Körpergewichts-Training ist das Volumen überall 0 - ohne diesen Rückfall
    // auf die Satzanzahl bliebe die Grafik komplett grau.
    @Test
    void muscleVolumeFallsBackToSetCountWhenAllVolumesAreZero() {
        List<MuscleVolumeDTO> result = service.toMuscleVolumeDTOs(List.of(
                new Row("abdominals", 0.0, 9.0, 3L),
                new Row("chest", 0.0, 3.0, 1L)));

        var byMuscle = bySlug(result);
        assertEquals(1.0, byMuscle.get("abdominals").share());
        assertEquals(1.0 / 3.0, byMuscle.get("chest").share(), 0.0001);
    }

    @Test
    void muscleVolumeIgnoresUnknownMuscleSlugs() {
        List<MuscleVolumeDTO> result = service.toMuscleVolumeDTOs(List.of(
                new Row("gills", 500.0, 2.0, 1L),
                new Row("lats", 1000.0, 4.0, 1L)));

        assertEquals(17, result.size());
        assertEquals(1.0, bySlug(result).get("lats").share());
    }

    @Test
    void currentStreakCountsConsecutiveTrainedWeeks() {
        LocalDate week = LocalDate.of(2026, 8, 3); // Montag
        Set<LocalDate> trained = Set.of(week, week.minusWeeks(1), week.minusWeeks(2));

        assertEquals(3, service.currentStreak(trained, week));
    }

    // Die laufende Woche darf noch leer sein, ohne die Serie zu brechen - sonst stünde
    // jeden Montagmorgen 0 da.
    @Test
    void currentStreakSurvivesAnEmptyCurrentWeek() {
        LocalDate week = LocalDate.of(2026, 8, 3);
        Set<LocalDate> trained = Set.of(week.minusWeeks(1), week.minusWeeks(2));

        assertEquals(2, service.currentStreak(trained, week));
    }

    @Test
    void currentStreakIsZeroAfterTwoQuietWeeks() {
        LocalDate week = LocalDate.of(2026, 8, 3);
        Set<LocalDate> trained = Set.of(week.minusWeeks(2), week.minusWeeks(3));

        assertEquals(0, service.currentStreak(trained, week));
    }

    @Test
    void longestStreakFindsTheBestRunInTheWindow() {
        LocalDate to = LocalDate.of(2026, 8, 3);
        LocalDate from = to.minusWeeks(10);
        Set<LocalDate> trained = Set.of(
                from.plusWeeks(1), from.plusWeeks(2), from.plusWeeks(3), // 3er-Serie
                from.plusWeeks(6),
                to.minusWeeks(1), to);                                    // 2er-Serie

        assertEquals(3, service.longestStreak(trained, from, to));
    }

    @Test
    void longestStreakIsZeroWithoutAnyTraining() {
        LocalDate to = LocalDate.of(2026, 8, 3);
        assertEquals(0, service.longestStreak(Set.of(), to.minusWeeks(8), to));
    }
}
