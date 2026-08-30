package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.ProgressionSuggestionDTO;
import com.Finn.everything_app.dto.ProgressionSuggestionDTO.Kind;
import com.Finn.everything_app.dto.ProgressionSuggestionDTO.WarmupSetDTO;
import com.Finn.everything_app.model.Exercise;
import com.Finn.everything_app.model.ExerciseSet;
import com.Finn.everything_app.model.MuscleGroup;
import com.Finn.everything_app.model.ProgressionPolicy;
import com.Finn.everything_app.model.Routine;
import com.Finn.everything_app.model.RoutineExercise;
import com.Finn.everything_app.model.SetType;
import com.Finn.everything_app.repository.ExerciseSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Leitet aus dem Verlauf ab, was beim naechsten Mal ansteht.
 *
 * <p><b>Nichts wird gespeichert.</b> Weder Gewicht noch Fehlschlagzaehler landen in der
 * Datenbank; jede Abfrage rechnet aus den geloggten Saetzen neu. Ein Zaehler wuerde
 * auseinanderlaufen, sobald eine Einheit nachtraeglich korrigiert oder geloescht wird - und
 * genau das passiert in einer Trainings-App staendig.
 *
 * <p>Die Ableitung ist deshalb bewusst nur von zwei Dingen abhaengig: der Vorgabe in der
 * Routine und den abgeschlossenen Saetzen der letzten Einheiten. Beides steckt in
 * {@link #suggest(RoutineExercise, List)}, das ohne Spring-Kontext testbar ist.
 */
@Service
@RequiredArgsConstructor
public class ProgressionService {

    /** Fehlschlaege in Folge, bis das Gewicht runtergeht. Greyskull nimmt {@link #GREYSKULL_STALL_LIMIT}. */
    static final int STALL_LIMIT = 3;
    static final int GREYSKULL_STALL_LIMIT = 1;

    /** Ein Deload nimmt ein Zehntel weg - genug, um Schwung zu holen, wenig genug, um zurueckzukommen. */
    static final double DELOAD_FACTOR = 0.9;

    /** Standardsprung, wenn die Routine keinen eigenen nennt. */
    static final double DEFAULT_INCREMENT_KG = 2.5;
    /** Bein- und Rueckenarbeit vertraegt groessere Spruenge als Isolationsarbeit. */
    static final double HEAVY_INCREMENT_KG = 5.0;

    private static final Set<MuscleGroup> HEAVY_MUSCLES = EnumSet.of(
            MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES,
            MuscleGroup.LOWER_BACK, MuscleGroup.MIDDLE_BACK, MuscleGroup.LATS,
            MuscleGroup.TRAPS);

    /** Mehr Saetze bringen bei Koerpergewichtsarbeit irgendwann nichts mehr - dann muss Last dazu. */
    static final int MAX_BW_SETS = 6;

    /** Zeituebungen steigen in Fuenf-Sekunden-Schritten. */
    static final int TIME_STEP_SECONDS = 5;

    /** Unter diesem Arbeitsgewicht lohnt die Rampe nicht - eine leere Stange waermt niemanden auf. */
    static final double MIN_WARMUP_WEIGHT_KG = 20.0;
    private static final double[] WARMUP_FRACTIONS = {0.4, 0.6, 0.8};
    private static final int[] WARMUP_REPS = {8, 5, 3};

    /** So viele Einheiten zurueck reicht fuer jeden Zaehler, den hier jemand braucht. */
    private static final int HISTORY_DEPTH = 8;

    private static final double EPS = 0.01;

    private final ExerciseSetRepository setRepository;

    // ------------------------------------------------------------------ Einstieg

    /** Vorgaben fuer jede Zeile einer Routine - eine Abfrage fuer das ganze Training. */
    @Transactional(readOnly = true)
    public List<ProgressionSuggestionDTO> suggestForRoutine(Long userId, Routine routine) {
        List<RoutineExercise> lines = routine.getExercises();
        if (lines.isEmpty()) {
            return List.of();
        }

        Set<Long> exerciseIds = new java.util.LinkedHashSet<>();
        for (RoutineExercise line : lines) {
            if (line.getExercise() != null) {
                exerciseIds.add(line.getExercise().getId());
            }
        }
        Map<Long, List<SessionPerformance>> history = history(userId, exerciseIds);

        List<ProgressionSuggestionDTO> out = new ArrayList<>(lines.size());
        for (RoutineExercise line : lines) {
            Long exerciseId = line.getExercise() != null ? line.getExercise().getId() : null;
            out.add(suggest(line, history.getOrDefault(exerciseId, List.of())));
        }
        return out;
    }

    /**
     * Fasst die geloggten Saetze je Uebung zu einer Reihe von Einheiten zusammen, neueste
     * zuerst. Eine Abfrage fuer alle Uebungen - sonst waere der Trainingsstart eine
     * N+1-Schleife.
     */
    Map<Long, List<SessionPerformance>> history(Long userId, Collection<Long> exerciseIds) {
        if (exerciseIds.isEmpty()) {
            return Map.of();
        }
        return groupHistory(setRepository.findCompletedSetsForExercises(exerciseIds, userId));
    }

    /**
     * Dieselbe Gruppierung fuer Aufrufer, die die Saetze schon geladen haben - der
     * Trainingsstart etwa holt sie ohnehin fuer die "vorher"-Spalte.
     *
     * @param sets abgeschlossene Saetze, <em>nach Startzeit absteigend</em> sortiert.
     */
    public Map<Long, List<SessionPerformance>> groupHistory(List<ExerciseSet> sets) {
        // exerciseId -> sessionId -> Saetze. LinkedHashMap haelt die Reihenfolge der Abfrage
        // (neueste Einheit zuerst) fest, ohne dass hier noch einmal sortiert werden muss.
        Map<Long, Map<Long, List<ExerciseSet>>> grouped = new LinkedHashMap<>();
        for (ExerciseSet set : sets) {
            if (set.getExercise() == null || set.getWorkoutSession() == null) continue;
            grouped.computeIfAbsent(set.getExercise().getId(), id -> new LinkedHashMap<>())
                    .computeIfAbsent(set.getWorkoutSession().getId(), id -> new ArrayList<>())
                    .add(set);
        }

        Map<Long, List<SessionPerformance>> out = new LinkedHashMap<>();
        grouped.forEach((exerciseId, sessions) -> {
            List<SessionPerformance> perf = new ArrayList<>();
            for (List<ExerciseSet> sessionSets : sessions.values()) {
                SessionPerformance p = SessionPerformance.of(sessionSets);
                if (p != null) perf.add(p);
                if (perf.size() >= HISTORY_DEPTH) break;
            }
            out.put(exerciseId, perf);
        });
        return out;
    }

    // ------------------------------------------------------------------ Kern

    /**
     * Die eigentliche Ableitung. Rein - keine Datenbank, kein Schreiben.
     *
     * @param history Einheiten mit dieser Uebung, <em>neueste zuerst</em>.
     */
    public ProgressionSuggestionDTO suggest(RoutineExercise line, List<SessionPerformance> history) {
        Targets t = Targets.of(line);
        ProgressionSuggestionDTO dto = base(line, t);

        if (t.policy() == ProgressionPolicy.OFF) {
            dto.setKind(Kind.OFF);
            dto.setWhy("Keine Automatik — die Vorgabe kommt aus der Routine.");
            return withWarmup(dto, t);
        }
        if (history.isEmpty()) {
            dto.setKind(Kind.FIRST);
            dto.setWhy("Zum ersten Mal — die Vorgabe kommt aus der Routine. "
                    + "Ab der nächsten Einheit rechnet sie sich aus dem Verlauf.");
            return withWarmup(dto, t);
        }

        SessionPerformance last = history.get(0);
        if (t.bodyweight()) {
            applyBodyweight(dto, t, history, last);
        } else {
            switch (t.policy()) {
                case LINEAR -> applyLinear(dto, t, history, last);
                case GREYSKULL -> applyGreyskull(dto, t, history, last);
                case DOUBLE -> applyDouble(dto, t, history, last);
                case TIME -> applyTime(dto, t, history, last);
                default -> throw new IllegalStateException("unbehandelt: " + t.policy());
            }
        }
        return withWarmup(dto, t);
    }

    // ------------------------------------------------------------------ Policies

    private void applyLinear(ProgressionSuggestionDTO dto, Targets t,
                             List<SessionPerformance> history, SessionPerformance last) {
        dto.setReps(t.repsMin());
        dto.setSets(t.sets());

        if (allSetsHit(last, t, t.repsMin())) {
            dto.setKind(Kind.UP);
            dto.setWeight(round(last.topWeight() + t.step()));
            dto.setWhy(fmt("%s kg × %d in %d Sätzen standen — %s kg drauf.",
                    kg(last.topWeight()), last.minReps(), last.workSets(), kg(t.step())));
            return;
        }

        int stalls = stalls(history, p -> allSetsHit(p, t, t.repsMin()));
        dto.setStallCount(stalls);
        if (stalls >= STALL_LIMIT) {
            double next = deload(last.topWeight(), t.step());
            dto.setKind(Kind.DELOAD);
            dto.setWeight(next);
            dto.setWhy(fmt("%d× in Folge an %s kg hängen geblieben (%s) — zurück auf %s kg.",
                    stalls, kg(last.topWeight()), shortfall(last, t, t.repsMin()), kg(next)));
        } else {
            dto.setKind(Kind.HOLD);
            dto.setWeight(round(last.topWeight()));
            dto.setWhy(fmt("%s kg noch einmal — letztes Mal %s.",
                    kg(last.topWeight()), shortfall(last, t, t.repsMin())));
        }
    }

    private void applyGreyskull(ProgressionSuggestionDTO dto, Targets t,
                                List<SessionPerformance> history, SessionPerformance last) {
        dto.setReps(t.repsMin());
        dto.setSets(t.sets());

        if (allSetsHit(last, t, t.repsMin())) {
            // Der letzte Satz geht bis zum Anschlag: das Doppelte der Sollwiederholungen
            // heisst, dass das Gewicht deutlich zu leicht war.
            boolean doubled = last.lastSetReps() >= 2 * t.repsMin();
            double add = doubled ? 2 * t.step() : t.step();
            dto.setKind(Kind.UP);
            dto.setWeight(round(last.topWeight() + add));
            dto.setWhy(doubled
                    ? fmt("Letzter Satz %d Wdh statt %d — doppelter Sprung, %s kg drauf.",
                          last.lastSetReps(), t.repsMin(), kg(add))
                    : fmt("%s kg × %d geschafft — %s kg drauf.",
                          kg(last.topWeight()), last.lastSetReps(), kg(add)));
            return;
        }

        int stalls = stalls(history, p -> allSetsHit(p, t, t.repsMin()));
        dto.setStallCount(stalls);
        // Greyskull deloadet sofort - der AMRAP-Satz sagt schon nach einem Fehlschlag genug.
        double next = deload(last.topWeight(), t.step());
        dto.setKind(Kind.DELOAD);
        dto.setWeight(next);
        dto.setWhy(fmt("Vorgabe verfehlt (%s) — zurück auf %s kg.",
                shortfall(last, t, t.repsMin()), kg(next)));
    }

    private void applyDouble(ProgressionSuggestionDTO dto, Targets t,
                             List<SessionPerformance> history, SessionPerformance last) {
        dto.setSets(t.sets());

        if (allSetsHit(last, t, t.repsMax())) {
            dto.setKind(Kind.UP);
            dto.setWeight(round(last.topWeight() + t.step()));
            dto.setReps(t.repsMin());
            dto.setWhy(fmt("Oberes Ende der Spanne (%d Wdh) in allen Sätzen — %s kg drauf, "
                            + "zurück auf %d Wdh.",
                    t.repsMax(), kg(t.step()), t.repsMin()));
            return;
        }
        if (allSetsHit(last, t, t.repsMin())) {
            int next = Math.min(last.minReps() + 1, t.repsMax());
            dto.setKind(Kind.HOLD);
            dto.setWeight(round(last.topWeight()));
            dto.setReps(next);
            dto.setWhy(fmt("Gewicht bleibt bei %s kg — eine Wiederholung mehr: %d.",
                    kg(last.topWeight()), next));
            return;
        }

        int stalls = stalls(history, p -> allSetsHit(p, t, t.repsMin()));
        dto.setStallCount(stalls);
        if (stalls >= STALL_LIMIT) {
            double next = deload(last.topWeight(), t.step());
            dto.setKind(Kind.DELOAD);
            dto.setWeight(next);
            dto.setReps(t.repsMin());
            dto.setWhy(fmt("%d× in Folge verfehlt (%s) — zurück auf %s kg.",
                    stalls, shortfall(last, t, t.repsMin()), kg(next)));
        } else {
            dto.setKind(Kind.HOLD);
            dto.setWeight(round(last.topWeight()));
            dto.setReps(t.repsMin());
            dto.setWhy(fmt("Vorgabe verfehlt (%s) — %s kg noch einmal.",
                    shortfall(last, t, t.repsMin()), kg(last.topWeight())));
        }
    }

    private void applyTime(ProgressionSuggestionDTO dto, Targets t,
                           List<SessionPerformance> history, SessionPerformance last) {
        dto.setSets(t.sets());
        dto.setReps(null);
        dto.setWeight(last.topWeight() > EPS ? round(last.topWeight()) : null);

        int goal = t.seconds() > 0 ? t.seconds() : last.minDurationSeconds();
        Predicate<SessionPerformance> hit =
                p -> p.workSets() >= t.sets() && p.minDurationSeconds() >= goal;

        if (hit.test(last)) {
            int next = last.minDurationSeconds() + TIME_STEP_SECONDS;
            dto.setKind(Kind.UP);
            dto.setSeconds(next);
            dto.setWhy(fmt("%d s in allen Sätzen gehalten — jetzt %d s.",
                    last.minDurationSeconds(), next));
            return;
        }

        int stalls = stalls(history, hit);
        dto.setStallCount(stalls);
        if (stalls >= STALL_LIMIT) {
            int next = Math.max(TIME_STEP_SECONDS, roundToStep(goal * DELOAD_FACTOR, TIME_STEP_SECONDS));
            if (next >= goal) next = Math.max(TIME_STEP_SECONDS, goal - TIME_STEP_SECONDS);
            dto.setKind(Kind.DELOAD);
            dto.setSeconds(next);
            dto.setWhy(fmt("%d× in Folge unter %d s — zurück auf %d s.", stalls, goal, next));
        } else {
            dto.setKind(Kind.HOLD);
            dto.setSeconds(goal);
            dto.setWhy(fmt("%d s noch einmal — letztes Mal %d s.", goal, last.minDurationSeconds()));
        }
    }

    /**
     * Koerpergewicht: erst Wiederholungen bis zur Obergrenze, dann ein Satz mehr. Die Last
     * bleibt aussen vor - sie ist der eigene Koerper und laesst sich nicht in 2,5-kg-Stufen
     * regeln.
     */
    private void applyBodyweight(ProgressionSuggestionDTO dto, Targets t,
                                 List<SessionPerformance> history, SessionPerformance last) {
        dto.setWeight(null);

        if (allSetsHit(last, t, t.repsMax())) {
            if (last.workSets() < MAX_BW_SETS) {
                dto.setKind(Kind.UP);
                dto.setSets(last.workSets() + 1);
                dto.setReps(t.repsMin());
                dto.setWhy(fmt("%d Wdh in allen %d Sätzen — ein Satz mehr (%d), wieder ab %d Wdh.",
                        t.repsMax(), last.workSets(), last.workSets() + 1, t.repsMin()));
            } else {
                dto.setKind(Kind.HOLD);
                dto.setSets(MAX_BW_SETS);
                dto.setReps(t.repsMax());
                dto.setWhy(fmt("%d Sätze × %d Wdh — mehr Volumen bringt hier nichts, "
                                + "häng Zusatzgewicht an.",
                        MAX_BW_SETS, t.repsMax()));
            }
            return;
        }
        if (allSetsHit(last, t, t.repsMin())) {
            int next = Math.min(last.minReps() + 1, t.repsMax());
            dto.setKind(Kind.UP);
            dto.setSets(last.workSets());
            dto.setReps(next);
            dto.setWhy(fmt("%d Wdh standen — jetzt %d.", last.minReps(), next));
            return;
        }

        int stalls = stalls(history, p -> allSetsHit(p, t, t.repsMin()));
        dto.setStallCount(stalls);
        dto.setSets(t.sets());
        if (stalls >= STALL_LIMIT) {
            dto.setKind(Kind.DELOAD);
            dto.setReps(t.repsMin());
            dto.setWhy(fmt("%d× in Folge verfehlt (%s) — zurück auf die Untergrenze.",
                    stalls, shortfall(last, t, t.repsMin())));
        } else {
            dto.setKind(Kind.HOLD);
            dto.setReps(t.repsMin());
            dto.setWhy(fmt("Vorgabe verfehlt (%s) — noch einmal dieselbe.",
                    shortfall(last, t, t.repsMin())));
        }
    }

    // ------------------------------------------------------------------ Helfer

    /**
     * Woran es lag - Satzanzahl oder Wiederholungen.
     *
     * <p>Getrennt benannt, weil beides eine Vorgabe verfehlt, aber Verschiedenes bedeutet:
     * zwei von vier Saetzen ist eine abgebrochene Einheit, sechs statt acht Wiederholungen
     * ein zu schweres Gewicht. Eine Begruendung, die das verwechselt, ist schlimmer als gar
     * keine.
     */
    private static String shortfall(SessionPerformance p, Targets t, int reps) {
        if (p.workSets() < t.sets()) {
            return fmt("nur %d von %d Sätzen", p.workSets(), t.sets());
        }
        return fmt("%d statt %d Wdh", p.minReps(), reps);
    }

    /** Vorgabe erfuellt: genug Arbeitssaetze, und keiner davon unter den Sollwiederholungen. */
    private static boolean allSetsHit(SessionPerformance p, Targets t, int reps) {
        return p.workSets() >= t.sets() && p.minReps() >= reps;
    }

    /**
     * Wie oft in Folge - von der letzten Einheit rueckwaerts - die Vorgabe <em>an demselben
     * Gewicht</em> verfehlt wurde. Ein anderes Gewicht beendet die Zaehlung: haengen bleibt
     * man an einer Last, nicht an einer Uebung.
     */
    static int stalls(List<SessionPerformance> history, Predicate<SessionPerformance> hit) {
        int n = 0;
        Double anchor = null;
        for (SessionPerformance p : history) {
            if (hit.test(p)) break;
            if (anchor == null) {
                anchor = p.topWeight();
            } else if (Math.abs(p.topWeight() - anchor) > EPS) {
                break;
            }
            n++;
        }
        return n;
    }

    /** Zehn Prozent runter, auf eine ladbare Stufe - und garantiert unter dem Ausgangswert. */
    static double deload(double weight, double step) {
        double next = roundToStep(weight * DELOAD_FACTOR, step);
        if (next >= weight - EPS) {
            next = weight - step;
        }
        return round(Math.max(step, next));
    }

    private static double roundToStep(double value, double step) {
        return Math.round(value / step) * step;
    }

    private static int roundToStep(double value, int step) {
        return (int) Math.round(value / step) * step;
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private static String kg(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.valueOf(round(value)).replace('.', ',');
    }

    private static String fmt(String pattern, Object... args) {
        return String.format(java.util.Locale.GERMANY, pattern, args);
    }

    private ProgressionSuggestionDTO base(RoutineExercise line, Targets t) {
        ProgressionSuggestionDTO dto = new ProgressionSuggestionDTO();
        dto.setRoutineExerciseId(line.getId());
        dto.setPolicy(t.policy());
        dto.setSets(t.sets());
        dto.setReps(t.bodyweight() || t.policy() != ProgressionPolicy.TIME ? t.repsMin() : null);
        dto.setWeight(line.getTargetWeight());
        dto.setSeconds(t.seconds() > 0 ? t.seconds() : null);
        if (line.getExercise() != null) {
            dto.setExerciseId(line.getExercise().getId());
            dto.setExerciseName(line.getExercise().getName());
        }
        return dto;
    }

    /**
     * Haengt die Aufwaermrampe an - 40/60/80 % des Arbeitsgewichts mit 8/5/3 Wiederholungen.
     *
     * <p>Stufen, die nach dem Runden auf dieselbe Last fallen oder das Arbeitsgewicht
     * erreichen, fallen weg: zweimal dasselbe aufzulegen waermt nicht mehr auf, es haelt nur
     * auf. Unterhalb von {@value #MIN_WARMUP_WEIGHT_KG} kg gibt es gar keine Rampe.
     */
    private ProgressionSuggestionDTO withWarmup(ProgressionSuggestionDTO dto, Targets t) {
        Double work = dto.getWeight();
        if (t.bodyweight() || t.policy() == ProgressionPolicy.TIME
                || work == null || work < MIN_WARMUP_WEIGHT_KG) {
            return dto;
        }
        List<WarmupSetDTO> ramp = new ArrayList<>();
        double previous = 0;
        for (int i = 0; i < WARMUP_FRACTIONS.length; i++) {
            double weight = round(roundToStep(work * WARMUP_FRACTIONS[i], t.step()));
            if (weight <= 0 || weight >= work - EPS || Math.abs(weight - previous) < EPS) {
                continue;
            }
            previous = weight;
            ramp.add(new WarmupSetDTO(weight, WARMUP_REPS[i],
                    (int) Math.round(WARMUP_FRACTIONS[i] * 100)));
        }
        dto.setWarmup(ramp);
        return dto;
    }

    // ------------------------------------------------------------------ Werttypen

    /** Die Vorgabe der Routinen-Zeile, mit allen Standardwerten schon eingesetzt. */
    record Targets(ProgressionPolicy policy, int sets, int repsMin, int repsMax,
                   int seconds, double step, boolean bodyweight) {

        private static final int DEFAULT_SETS = 3;
        private static final int DEFAULT_REPS = 8;

        static Targets of(RoutineExercise line) {
            int sets = line.getTargetSets() != null && line.getTargetSets() > 0
                    ? line.getTargetSets() : DEFAULT_SETS;

            Integer min = line.getTargetRepsMin();
            Integer max = line.getTargetRepsMax();
            int repsMin = min != null ? min : (max != null ? max : DEFAULT_REPS);
            int repsMax = max != null ? max : repsMin;
            if (repsMax < repsMin) repsMax = repsMin;

            int seconds = line.getTargetDurationSeconds() != null
                    ? line.getTargetDurationSeconds() : 0;

            return new Targets(
                    ProgressionPolicy.orDefault(line.getProgressionPolicy()),
                    sets, repsMin, repsMax, seconds,
                    step(line),
                    Boolean.TRUE.equals(line.getIsBodyweight()));
        }

        private static double step(RoutineExercise line) {
            if (line.getIncrementKg() != null && line.getIncrementKg() > 0) {
                return line.getIncrementKg();
            }
            Exercise exercise = line.getExercise();
            if (exercise != null) {
                for (MuscleGroup muscle : exercise.getPrimaryMuscles()) {
                    if (HEAVY_MUSCLES.contains(muscle)) return HEAVY_INCREMENT_KG;
                }
            }
            return DEFAULT_INCREMENT_KG;
        }
    }

    /**
     * Was in einer Einheit an einer Uebung tatsaechlich passiert ist.
     *
     * <p>Nur Arbeitssaetze: Aufwaermsaetze, Abfallsaetze und Rest-Pause-Cluster bleiben
     * draussen. Ein Aufwaermsatz mit 3 Wiederholungen wuerde sonst jede Vorgabe als verfehlt
     * erscheinen lassen.
     */
    public record SessionPerformance(LocalDateTime performedAt, double topWeight,
                                     int minReps, int maxReps, int workSets,
                                     int minDurationSeconds, int lastSetReps) {

        static SessionPerformance of(List<ExerciseSet> sets) {
            List<ExerciseSet> work = sets.stream().filter(SessionPerformance::isWorkSet).toList();
            if (work.isEmpty()) return null;

            double top = 0;
            int min = Integer.MAX_VALUE;
            int max = 0;
            int minDuration = Integer.MAX_VALUE;
            LocalDateTime at = null;
            for (ExerciseSet s : work) {
                double w = s.getWeight() != null ? s.getWeight() : 0;
                if (w > top) top = w;
                int reps = s.getReps() != null ? s.getReps() : 0;
                min = Math.min(min, reps);
                max = Math.max(max, reps);
                int seconds = s.getDurationSeconds() != null ? s.getDurationSeconds() : 0;
                minDuration = Math.min(minDuration, seconds);
                if (at == null && s.getWorkoutSession() != null) {
                    at = s.getWorkoutSession().getStartTime();
                }
            }
            // Der letzte Satz ist bei Greyskull der AMRAP-Satz - die Reihenfolge kommt aus
            // setNumber, nicht aus der Listenposition.
            ExerciseSet lastSet = work.stream()
                    .max(java.util.Comparator.comparing(
                            s -> s.getSetNumber() != null ? s.getSetNumber() : 0))
                    .orElseThrow();

            return new SessionPerformance(at, top,
                    min == Integer.MAX_VALUE ? 0 : min, max, work.size(),
                    minDuration == Integer.MAX_VALUE ? 0 : minDuration,
                    lastSet.getReps() != null ? lastSet.getReps() : 0);
        }

        private static boolean isWorkSet(ExerciseSet s) {
            if (!Boolean.TRUE.equals(s.getIsCompleted())) return false;
            if (s.getParentSetId() != null) return false;
            SetType type = SetType.orDefault(s.getSetType());
            return type != SetType.WARMUP && type != SetType.DROP && type != SetType.RESTPAUSE;
        }
    }
}
