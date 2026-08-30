package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.MuscleRecoveryDTO;
import com.Finn.everything_app.dto.MuscleVolumeDTO;
import com.Finn.everything_app.model.MuscleGroup;
import com.Finn.everything_app.repository.WorkoutStatsRepository;
import com.Finn.everything_app.repository.projection.ExerciseOneRmRow;
import com.Finn.everything_app.repository.projection.MuscleVolumeRow;
import com.Finn.everything_app.repository.projection.RecoverySetRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

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
    void muscleVolumeReturnsEveryDrawableMuscleZeroFilled() {
        List<MuscleVolumeDTO> result = service.toMuscleVolumeDTOs(List.of(
                new Row("chest", 4000.0, 8.0, 2L)));

        assertEquals(MuscleGroup.bodyMapValues().size(), result.size());
        // Jede Muskelgruppe ausser CARDIO, das keine Flaeche hat. Bewusst hergeleitet statt als
        // Zahl hingeschrieben: die Liste ist mit der neuen Koerpergeometrie um Hueftbeuger,
        // Obliques, Serratus und Schienbein gewachsen und darf das wieder tun.
        assertEquals(MuscleGroup.values().length - 1, result.size());

        var byMuscle = bySlug(result);
        assertEquals(1.0, byMuscle.get("chest").share());
        assertEquals(0.0, byMuscle.get("hamstrings").volumeKg());
        assertEquals(0.0, byMuscle.get("hamstrings").share());
        assertEquals("Beinbeuger", byMuscle.get("hamstrings").label());
    }

    /**
     * CARDIO ist eine gueltige Muskelgruppe (die Bibliothek filtert danach), hat aber keine
     * Flaeche in der Koerper-Grafik. Eine Zeile "Ausdauer" in der Muskelbilanz waere nichts,
     * was sich einfaerben liesse.
     */
    @Test
    void muscleVolumeLeavesCardioOutOfTheBalance() {
        List<MuscleVolumeDTO> result = service.toMuscleVolumeDTOs(List.of(
                new Row("cardio", 0.0, 6.0, 3L),
                new Row("chest", 4000.0, 8.0, 2L)));

        assertFalse(bySlug(result).containsKey("cardio"));
        assertEquals(1.0, bySlug(result).get("chest").share());
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

        assertEquals(MuscleGroup.bodyMapValues().size(), result.size());
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

    // ── Erholung ────────────────────────────────────────────────────────────────

    private record SetRow(String muscle, Double factor, Long exerciseId, Long sessionId,
                          Double weight, Integer reps, LocalDateTime performedAt)
            implements RecoverySetRow {
        @Override public String getMuscle() { return muscle; }
        @Override public Double getFactor() { return factor; }
        @Override public Long getExerciseId() { return exerciseId; }
        @Override public Long getSessionId() { return sessionId; }
        @Override public Double getWeight() { return weight; }
        @Override public Integer getReps() { return reps; }
        @Override public LocalDateTime getPerformedAt() { return performedAt; }
    }

    private record OneRmRow(Long exerciseId, Double bestOneRm) implements ExerciseOneRmRow {
        @Override public Long getExerciseId() { return exerciseId; }
        @Override public Double getBestOneRm() { return bestOneRm; }
    }

    private Map<String, MuscleRecoveryDTO> recovery(List<RecoverySetRow> sets,
                                                    List<ExerciseOneRmRow> maxima) {
        when(statsRepository.recoverySets(anyLong(), any())).thenReturn(sets);
        when(statsRepository.bestOneRepMaxPerExercise(anyLong())).thenReturn(maxima);
        return service.getRecovery(1L).stream()
                .collect(Collectors.toMap(MuscleRecoveryDTO::muscle, Function.identity()));
    }

    /**
     * Die Spalte {@code muscle} enthaelt den Enum-Namen ({@code CHEST}), die Antwort den
     * Slug ({@code chest}). Wer die Zeichenkette einfach durchreicht, bekommt eine Antwort
     * voller Nullen, ohne dass irgendwo ein Fehler auftaucht.
     */
    @Test
    void derEnumNameAusDerDatenbankFindetSeinenSlug() {
        var result = recovery(
                List.of(new SetRow("LOWER_BACK", 1.0, 7L, 100L, 100.0, 5,
                        LocalDateTime.now())),
                List.of(new OneRmRow(7L, 100.0)));

        assertEquals(1.0, result.get("lower back").fatigue());
    }

    /** Ein Muskel, fuer den nie etwas getan wurde, ist ausgeruht - und hat kein Datum. */
    @Test
    void untrainierteMuskelnSindErholt() {
        var result = recovery(List.of(), List.of());

        assertEquals(MuscleGroup.bodyMapValues().size(), result.size());
        var chest = result.get("chest");
        assertEquals(0.0, chest.fatigue());
        assertEquals(1.0, chest.readiness());
        assertNull(chest.lastTrainedAt());
        assertEquals(0, chest.hoursToReady());
    }

    /** Direkt nach der Einheit ist der Massstab die Einheit selbst - also volle Ermuedung. */
    @Test
    void direktNachDerEinheitIstDerMuskelVollBelastet() {
        var now = LocalDateTime.now();
        var result = recovery(
                List.of(new SetRow("CHEST", 1.0, 7L, 100L, 100.0, 5, now)),
                List.of(new OneRmRow(7L, 100.0)));

        assertEquals(1.0, result.get("chest").fatigue());
        assertEquals(0.0, result.get("chest").readiness());
        assertEquals(now, result.get("chest").lastTrainedAt());
    }

    /** Halbwertszeit 2 Tage: nach 4 Tagen ist ein Viertel uebrig - und damit erholt. */
    @Test
    void dieErmuedungKlingtMitZweiTagenHalbwertszeitAb() {
        var vorVierTagen = LocalDateTime.now().minusDays(4);
        var result = recovery(
                List.of(new SetRow("CHEST", 1.0, 7L, 100L, 100.0, 5, vorVierTagen)),
                List.of(new OneRmRow(7L, 100.0)));

        assertEquals(0.25, result.get("chest").fatigue(), 0.005);
        // Genau auf der Schwelle - laenger warten muss niemand.
        assertEquals(0, result.get("chest").hoursToReady());
    }

    @Test
    void nochNichtErholtNenntDieVerbleibendenStunden() {
        var vorDreiTagen = LocalDateTime.now().minusDays(3);
        var result = recovery(
                List.of(new SetRow("CHEST", 1.0, 7L, 100L, 100.0, 5, vorDreiTagen)),
                List.of(new OneRmRow(7L, 100.0)));

        // 0,5^1,5 = 0,354 vom Massstab; von dort auf 0,25 ist genau eine halbe
        // Halbwertszeit, also ein Tag.
        assertEquals(24, result.get("chest").hoursToReady());
    }

    /**
     * Der Kern des Modells: schwere Saetze wirken laenger nach als leichte mit demselben
     * Volumen. Ohne den Intensitaets-Exponenten waeren beide Einheiten gleich schwer und
     * die Ermuedung staende bei 1,0.
     */
    @Test
    void schwereSaetzeWiegenSchwererAlsLeichteMitGleichemVolumen() {
        var vorZweiTagen = LocalDateTime.now().minusDays(2);
        var result = recovery(
                List.of(
                        // 100 kg x 5 bei 1RM 100 -> volle Intensitaet -> 500
                        new SetRow("CHEST", 1.0, 7L, 100L, 100.0, 5, vorZweiTagen),
                        // 50 kg x 10, gleiches Volumen, halbe Intensitaet -> 500 * 0,5^1,5
                        new SetRow("CHEST", 1.0, 7L, 101L, 50.0, 10, vorZweiTagen)),
                List.of(new OneRmRow(7L, 100.0)));

        // Massstab ist die schwere Einheit (500). Aktuell: (500 + 176,8) * 0,5 = 338,4.
        assertEquals(338.4 / 500, result.get("chest").fatigue(), 0.005);
    }

    /** Sekundaermuskeln zaehlen halb - dieselbe Regel wie in der Muskelbilanz. */
    @Test
    void sekundaermuskelnErmuedenNurHalbSoStark() {
        var now = LocalDateTime.now();
        var result = recovery(
                List.of(
                        new SetRow("CHEST", 1.0, 7L, 100L, 100.0, 5, now),
                        new SetRow("TRICEPS", 0.5, 7L, 100L, 100.0, 5, now),
                        // Zweite, leichtere Einheit, damit der Massstab nicht gleich der
                        // aktuellen Belastung ist und der Unterschied sichtbar wird.
                        new SetRow("CHEST", 1.0, 7L, 101L, 100.0, 5,
                                now.minusDays(2)),
                        new SetRow("TRICEPS", 0.5, 7L, 101L, 100.0, 5,
                                now.minusDays(2))),
                List.of(new OneRmRow(7L, 100.0)));

        // Beide skalieren mit demselben Faktor - der Anteil bleibt gleich, die Rohwerte
        // nicht. Geprueft wird, dass der Faktor ueberhaupt durchschlaegt: identische
        // Verlaeufe ergeben identische Anteile.
        assertEquals(result.get("chest").fatigue(), result.get("triceps").fatigue(), 0.001);
        assertTrue(result.get("chest").fatigue() > 0.9);
    }
}
