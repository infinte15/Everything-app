package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.ProgressionSuggestionDTO;
import com.Finn.everything_app.dto.ProgressionSuggestionDTO.Kind;
import com.Finn.everything_app.model.Exercise;
import com.Finn.everything_app.model.ExerciseSet;
import com.Finn.everything_app.model.MuscleGroup;
import com.Finn.everything_app.model.ProgressionPolicy;
import com.Finn.everything_app.model.RoutineExercise;
import com.Finn.everything_app.model.SetType;
import com.Finn.everything_app.service.ProgressionService.SessionPerformance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die Progression laesst sich nicht erklicken - ein Deload nach drei Fehlschlaegen zeigt sich
 * erst nach drei Trainings. Deshalb steht sie hier vollstaendig als Tabelle.
 *
 * <p>Ohne Spring: {@code suggest} ist rein, der Repository-Verweis wird nie angefasst.
 */
class ProgressionServiceTest {

    private final ProgressionService service = new ProgressionService(null);

    // ------------------------------------------------------------------ Bauteile

    private Exercise exercise(String name, MuscleGroup... primary) {
        Exercise e = new Exercise();
        e.setId(1L);
        e.setName(name);
        e.setPrimaryMuscles(new java.util.LinkedHashSet<>(List.of(primary)));
        return e;
    }

    private RoutineExercise line(ProgressionPolicy policy, int sets, Integer min, Integer max) {
        RoutineExercise re = new RoutineExercise();
        re.setId(10L);
        re.setExercise(exercise("bench press", MuscleGroup.CHEST));
        re.setProgressionPolicy(policy);
        re.setTargetSets(sets);
        re.setTargetRepsMin(min);
        re.setTargetRepsMax(max);
        return re;
    }

    /** Eine Einheit: {@code sets} Arbeitssaetze mit demselben Gewicht und denselben Wdh. */
    private SessionPerformance session(double weight, int reps, int sets) {
        return session(weight, reps, sets, reps);
    }

    /** {@code reps} gilt fuer die vorderen Saetze, {@code lastSetReps} fuer den letzten. */
    private SessionPerformance session(double weight, int reps, int sets, int lastSetReps) {
        return new SessionPerformance(null, weight,
                Math.min(reps, lastSetReps), Math.max(reps, lastSetReps),
                sets, 0, lastSetReps);
    }

    // ------------------------------------------------------------------ Grundfaelle

    @Test
    void ohneVerlaufKommtDieVorgabeAusDerRoutine() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 5, 5);
        re.setTargetWeight(60.0);

        ProgressionSuggestionDTO s = service.suggest(re, List.of());

        assertEquals(Kind.FIRST, s.getKind());
        assertEquals(60.0, s.getWeight());
        assertEquals(5, s.getReps());
        assertEquals(3, s.getSets());
    }

    @Test
    void ohneAutomatikBleibtAllesStehen() {
        RoutineExercise re = line(ProgressionPolicy.OFF, 3, 8, 12);
        re.setTargetWeight(40.0);

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(40, 12, 3)));

        assertEquals(Kind.OFF, s.getKind());
        assertEquals(40.0, s.getWeight());
    }

    // ------------------------------------------------------------------ LINEAR

    @Test
    void linearSteigtNachEinerSauberenEinheit() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 5, 5);

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(60, 5, 3)));

        assertEquals(Kind.UP, s.getKind());
        assertEquals(62.5, s.getWeight());
        assertTrue(s.getWhy().contains("2,5"), s.getWhy());
    }

    @Test
    void linearHaeltNachDemErstenFehlschlag() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 5, 5);

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(60, 4, 3)));

        assertEquals(Kind.HOLD, s.getKind());
        assertEquals(60.0, s.getWeight());
        assertEquals(1, s.getStallCount());
    }

    @Test
    void linearDeloadetNachDreiFehlschlaegen() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 5, 5);
        List<SessionPerformance> history = List.of(
                session(60, 4, 3), session(60, 4, 3), session(60, 3, 3), session(57.5, 5, 3));

        ProgressionSuggestionDTO s = service.suggest(re, history);

        assertEquals(Kind.DELOAD, s.getKind());
        assertEquals(3, s.getStallCount());
        // 60 * 0,9 = 54, auf 2,5-kg-Stufen gerundet = 55.
        assertEquals(55.0, s.getWeight());
    }

    @Test
    void einDeloadDazwischenSetztDenZaehlerZurueck() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 5, 5);
        // Zwei Fehlschlaege an 55 kg, davor drei an 60 kg: gezaehlt werden nur die an 55.
        List<SessionPerformance> history = List.of(
                session(55, 4, 3), session(55, 4, 3),
                session(60, 4, 3), session(60, 4, 3), session(60, 4, 3));

        ProgressionSuggestionDTO s = service.suggest(re, history);

        assertEquals(Kind.HOLD, s.getKind());
        assertEquals(2, s.getStallCount());
    }

    @Test
    void beinuebungenSpringenInFuenferSchritten() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 5, 5);
        re.setExercise(exercise("barbell squat", MuscleGroup.QUADRICEPS));

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(100, 5, 3)));

        assertEquals(105.0, s.getWeight());
    }

    @Test
    void derEigeneSprungSchlaegtDieAbleitung() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 5, 5);
        re.setExercise(exercise("barbell squat", MuscleGroup.QUADRICEPS));
        re.setIncrementKg(1.25);

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(100, 5, 3)));

        assertEquals(101.25, s.getWeight());
    }

    @Test
    void zuWenigeSaetzeSindEinFehlschlag() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 5, 5);

        // Wiederholungen stimmen, aber es waren nur zwei von drei Saetzen.
        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(60, 5, 2)));

        assertEquals(Kind.HOLD, s.getKind());
    }

    /**
     * Zwei von vier Saetzen ist eine abgebrochene Einheit, sechs statt acht Wiederholungen
     * ein zu schweres Gewicht. Die Begruendung muss sagen, was davon zutraf.
     */
    @Test
    void dieBegruendungNenntDenEchtenGrund() {
        RoutineExercise re = line(ProgressionPolicy.DOUBLE, 4, 5, 8);

        // Wiederholungen in Ordnung, aber nur zwei von vier Saetzen.
        String abgebrochen = service.suggest(re, List.of(session(75, 6, 2))).getWhy();
        assertTrue(abgebrochen.contains("nur 2 von 4 Sätzen"), abgebrochen);

        // Alle Saetze da, aber unter der Untergrenze.
        String zuSchwer = service.suggest(re, List.of(session(75, 4, 4))).getWhy();
        assertTrue(zuSchwer.contains("4 statt 5 Wdh"), zuSchwer);
    }

    // ------------------------------------------------------------------ GREYSKULL

    @Test
    void greyskullDeloadetSchonNachEinemFehlschlag() {
        RoutineExercise re = line(ProgressionPolicy.GREYSKULL, 3, 5, 5);

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(60, 5, 3, 4)));

        assertEquals(Kind.DELOAD, s.getKind());
        assertEquals(55.0, s.getWeight());
    }

    @Test
    void greyskullVerdoppeltDenSprungBeiDoppeltenWiederholungen() {
        RoutineExercise re = line(ProgressionPolicy.GREYSKULL, 3, 5, 5);

        // Letzter Satz bis zum Anschlag: 10 statt 5 - das Gewicht war deutlich zu leicht.
        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(60, 5, 3, 10)));

        assertEquals(Kind.UP, s.getKind());
        assertEquals(65.0, s.getWeight());
    }

    @Test
    void greyskullSteigtNormalOhneAusreisser() {
        RoutineExercise re = line(ProgressionPolicy.GREYSKULL, 3, 5, 5);

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(60, 5, 3, 7)));

        assertEquals(Kind.UP, s.getKind());
        assertEquals(62.5, s.getWeight());
    }

    // ------------------------------------------------------------------ DOUBLE

    @Test
    void doppelteProgressionArbeitetErstInDerSpanne() {
        RoutineExercise re = line(ProgressionPolicy.DOUBLE, 3, 8, 12);

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(40, 9, 3)));

        assertEquals(Kind.HOLD, s.getKind());
        assertEquals(40.0, s.getWeight());
        assertEquals(10, s.getReps());
    }

    @Test
    void doppelteProgressionErhoehtErstAmOberenEnde() {
        RoutineExercise re = line(ProgressionPolicy.DOUBLE, 3, 8, 12);

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(40, 12, 3)));

        assertEquals(Kind.UP, s.getKind());
        assertEquals(42.5, s.getWeight());
        assertEquals(8, s.getReps());
    }

    @Test
    void doppelteProgressionDeloadetUnterDerUntergrenze() {
        RoutineExercise re = line(ProgressionPolicy.DOUBLE, 3, 8, 12);
        List<SessionPerformance> history = List.of(
                session(40, 7, 3), session(40, 6, 3), session(40, 7, 3));

        ProgressionSuggestionDTO s = service.suggest(re, history);

        assertEquals(Kind.DELOAD, s.getKind());
        assertEquals(35.0, s.getWeight());
        assertEquals(8, s.getReps());
    }

    // ------------------------------------------------------------------ TIME

    @Test
    void zeitUebungenSteigenInFuenfSekunden() {
        RoutineExercise re = line(ProgressionPolicy.TIME, 3, null, null);
        re.setTargetDurationSeconds(45);

        SessionPerformance held = new SessionPerformance(null, 0, 0, 0, 3, 45, 0);
        ProgressionSuggestionDTO s = service.suggest(re, List.of(held));

        assertEquals(Kind.UP, s.getKind());
        assertEquals(50, s.getSeconds());
        assertNull(s.getWeight());
    }

    @Test
    void zeitUebungenDeloadenNachDreiFehlschlaegen() {
        RoutineExercise re = line(ProgressionPolicy.TIME, 3, null, null);
        re.setTargetDurationSeconds(60);

        SessionPerformance kurz = new SessionPerformance(null, 0, 0, 0, 3, 40, 0);
        ProgressionSuggestionDTO s = service.suggest(re, List.of(kurz, kurz, kurz));

        assertEquals(Kind.DELOAD, s.getKind());
        // 60 * 0,9 = 54, auf Fuenferstufen = 55.
        assertEquals(55, s.getSeconds());
    }

    // ------------------------------------------------------------------ Koerpergewicht

    @Test
    void koerpergewichtSteigertWiederholungenStattLast() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 8, 12);
        re.setIsBodyweight(true);

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(0, 9, 3)));

        assertEquals(Kind.UP, s.getKind());
        assertEquals(10, s.getReps());
        assertNull(s.getWeight());
    }

    @Test
    void koerpergewichtHaengtEinenSatzAnWennDieSpanneAusgereiztIst() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 8, 12);
        re.setIsBodyweight(true);

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(0, 12, 3)));

        assertEquals(Kind.UP, s.getKind());
        assertEquals(4, s.getSets());
        assertEquals(8, s.getReps());
    }

    @Test
    void koerpergewichtHoertBeiSechsSaetzenAuf() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 6, 8, 12);
        re.setIsBodyweight(true);

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(0, 12, 6)));

        assertEquals(Kind.HOLD, s.getKind());
        assertEquals(ProgressionService.MAX_BW_SETS, s.getSets());
        assertTrue(s.getWhy().contains("Zusatzgewicht"), s.getWhy());
    }

    // ------------------------------------------------------------------ Aufwaermrampe

    @Test
    void dieRampeFuehrtInDreiStufenAnsArbeitsgewicht() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 5, 5);

        ProgressionSuggestionDTO s = service.suggest(re, List.of(session(100, 5, 3)));

        // Arbeitsgewicht 102,5 -> 40/60/80 % auf 2,5er Stufen.
        assertEquals(List.of(40.0, 62.5, 82.5),
                s.getWarmup().stream().map(w -> w.getWeight()).toList());
        assertEquals(List.of(8, 5, 3),
                s.getWarmup().stream().map(w -> w.getReps()).toList());
    }

    @Test
    void unterZwanzigKiloGibtEsKeineRampe() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 5, 5);
        re.setTargetWeight(15.0);

        ProgressionSuggestionDTO s = service.suggest(re, List.of());

        assertTrue(s.getWarmup().isEmpty());
    }

    @Test
    void koerpergewichtUndZeitBekommenKeineRampe() {
        RoutineExercise bw = line(ProgressionPolicy.LINEAR, 3, 8, 12);
        bw.setIsBodyweight(true);
        assertTrue(service.suggest(bw, List.of(session(0, 9, 3))).getWarmup().isEmpty());

        RoutineExercise zeit = line(ProgressionPolicy.TIME, 3, null, null);
        zeit.setTargetDurationSeconds(45);
        SessionPerformance held = new SessionPerformance(null, 20, 0, 0, 3, 45, 0);
        assertTrue(service.suggest(zeit, List.of(held)).getWarmup().isEmpty());
    }

    // ------------------------------------------------------------------ Satzarten

    @Test
    void aufwaermUndZusatzsaetzeZaehlenNichtAlsArbeitssaetze() {
        List<ExerciseSet> sets = List.of(
                set(1, 20.0, 8, SetType.WARMUP, null),
                set(2, 60.0, 5, SetType.NORMAL, null),
                set(3, 60.0, 5, SetType.NORMAL, null),
                set(4, 60.0, 5, SetType.NORMAL, null),
                set(5, 40.0, 6, SetType.DROP, 4L),
                set(6, 60.0, 3, SetType.RESTPAUSE, 4L));

        SessionPerformance p = SessionPerformance.of(sets);

        assertNotNull(p);
        assertEquals(3, p.workSets());
        assertEquals(60.0, p.topWeight());
        assertEquals(5, p.minReps());
    }

    @Test
    void einAufwaermsatzDarfDieVorgabeNichtVerhageln() {
        RoutineExercise re = line(ProgressionPolicy.LINEAR, 3, 5, 5);

        // Ohne die Filterung waere minReps = 8 aus dem Aufwaermsatz... bzw. 3, und die
        // Einheit gaelte als verfehlt.
        List<ExerciseSet> sets = List.of(
                set(1, 30.0, 3, SetType.WARMUP, null),
                set(2, 60.0, 5, SetType.NORMAL, null),
                set(3, 60.0, 5, SetType.NORMAL, null),
                set(4, 60.0, 5, SetType.NORMAL, null));

        ProgressionSuggestionDTO s = service.suggest(re, List.of(SessionPerformance.of(sets)));

        assertEquals(Kind.UP, s.getKind());
        assertEquals(62.5, s.getWeight());
    }

    @Test
    void nichtAbgehakteSaetzeZaehlenNicht() {
        ExerciseSet offen = set(4, 60.0, 5, SetType.NORMAL, null);
        offen.setIsCompleted(false);

        List<ExerciseSet> sets = List.of(
                set(1, 60.0, 5, SetType.NORMAL, null),
                set(2, 60.0, 5, SetType.NORMAL, null),
                offen);

        assertEquals(2, SessionPerformance.of(sets).workSets());
    }

    private ExerciseSet set(int number, Double weight, Integer reps, SetType type, Long parent) {
        ExerciseSet s = new ExerciseSet();
        s.setSetNumber(number);
        s.setWeight(weight);
        s.setReps(reps);
        s.setSetType(type);
        s.setParentSetId(parent);
        s.setIsCompleted(true);
        return s;
    }
}
