package com.Finn.everything_app.seed;

import com.Finn.everything_app.model.Exercise;
import com.Finn.everything_app.repository.ExerciseRepository;
import com.Finn.everything_app.repository.ExerciseSetRepository;
import com.Finn.everything_app.repository.RoutineExerciseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Einmaliger Wechsel vom free-exercise-db-Katalog auf den ExerciseDB-Katalog.
 *
 * <p>Der alte Katalog hatte 873 Uebungen mit zwei Standfotos, der neue hat 1324 mit
 * Vorschaubild <em>und</em> Animation. Bestehende Routinen und geloggte Saetze zeigen aber auf
 * die alten Zeilen, also werden sie hier umgebogen, bevor die alten Zeilen verschwinden.
 *
 * <p>Die drei Regeln, in dieser Reihenfolge:
 *
 * <ol>
 *   <li>Ueber den normalisierten Namen matchen. Beide Kataloge benennen dieselbe Uebung nur
 *       anders geschrieben ("3/4 Sit-Up" gegen "3/4 sit-up").</li>
 *   <li>Alles, was auf eine alte Zeile zeigt, auf die neue umbiegen.</li>
 *   <li>Alte Zeilen loeschen - <b>aber nur, wenn danach nichts mehr auf sie zeigt</b>. Eine
 *       nicht gematchte Uebung, die in einer Routine oder im Verlauf vorkommt, bleibt
 *       bestehen. {@code Exercise} kaskadiert {@code CascadeType.ALL} auf seine
 *       {@code exerciseSets}: eine noch referenzierte Zeile zu loeschen wuerde geloggte
 *       Trainingsdaten mitnehmen. Das ist der Grund fuer die Pruefung, nicht Vorsicht.</li>
 * </ol>
 *
 * <p>Idempotent: laeuft nichts mehr aus der alten Quelle herum, ist das ein sofortiges No-op.
 * Der Lauf danach kostet genau eine Abfrage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
// Nach dem Seeder (@Order(0)) - erst muss der neue Katalog stehen.
@Order(1)
@ConditionalOnProperty(name = "app.exercise-seed.enabled", havingValue = "true", matchIfMissing = true)
public class ExerciseCatalogMigration implements ApplicationRunner {

    static final String LEGACY_SOURCE = "free-exercise-db";

    /**
     * Von Hand geprueftes Woerterbuch fuer Uebungen, die beide Kataloge fuehren, aber unter zu
     * verschiedenen Namen, als dass eine Regel sie zusammenbraechte.
     *
     * <p>Automatisch geht hier nichts. Ein Abgleich ueber Wort-Teilmengen wurde probiert und
     * verworfen: er findet fuer elf dieser fuenfzehn Zeilen entweder nichts oder mehrere
     * Kandidaten, und sein einziger "eindeutiger" Treffer war falsch - "Cable Crossover" landete
     * auf "cable rope crossover seated row", einer Ruderuebung. Eine falsch zugeordnete Uebung
     * ist schlimmer als eine nicht zugeordnete: die eine bleibt sichtbar liegen, die andere
     * verfaelscht still den Verlauf.
     *
     * <p>Aufgenommen ist genau das, was in echten Routinen und im Verlauf vorkam. Die restlichen
     * Altuebungen benutzt niemand und werden ersatzlos geloescht.
     *
     * <p>Schluessel sind bereits normalisiert (siehe {@link #normalize(String)}).
     */
    static final Map<String, String> LEGACY_ALIASES = Map.ofEntries(
            Map.entry("barbell bench press medium grip", "barbell bench press"),
            Map.entry("barbell incline bench press medium grip", "barbell incline bench press"),
            Map.entry("bent over barbell row", "barbell bent over row"),
            Map.entry("cable crossover", "cable cross-over variation"),
            Map.entry("dumbbell shoulder press", "dumbbell seated shoulder press"),
            // Der neue Katalog fuehrt kein "face pull"; die hintere Schulter am Kabel ist die
            // Uebung, die dasselbe trainiert.
            Map.entry("face pull", "cable seated rear lateral raise"),
            Map.entry("leg press", "sled 45° leg press"),
            // Ein blosser Unterarmstuetz fehlt im neuen Katalog - die Variante mit Drehung ist
            // die naechstliegende Plank, die es dort gibt.
            Map.entry("plank", "front plank with twist"),
            Map.entry("pullups", "pull-up"),
            Map.entry("romanian deadlift", "barbell romanian deadlift"),
            Map.entry("seated cable rows", "cable seated row"),
            Map.entry("seated leg curl", "lever seated leg curl"),
            Map.entry("side lateral raise", "dumbbell lateral raise"),
            Map.entry("standing calf raises", "lever standing calf raise"),
            Map.entry("triceps pushdown", "cable pushdown"));

    private final ExerciseRepository exerciseRepository;
    private final RoutineExerciseRepository routineExerciseRepository;
    private final ExerciseSetRepository exerciseSetRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Exercise> legacy = exerciseRepository.findBySource(LEGACY_SOURCE);
        if (legacy.isEmpty()) {
            return;
        }

        Map<String, Exercise> byName = indexByName(exerciseRepository.findBySource(ExerciseCatalogSeeder.SOURCE));
        if (byName.isEmpty()) {
            log.warn("Katalog-Migration übersprungen: der neue Katalog ist leer. "
                    + "Läuft ExerciseCatalogSeeder?");
            return;
        }

        int matched = 0;
        int repointedPlans = 0;
        int repointedSets = 0;
        List<Exercise> unmatched = new ArrayList<>();

        for (Exercise old : legacy) {
            String key = normalize(old.getName());
            // Erst der direkte Name, dann das Woerterbuch - so bleibt ein exakter Treffer
            // immer stark, und der Alias greift nur dort, wo es keinen gibt.
            Exercise replacement = byName.get(key);
            if (replacement == null) {
                String alias = LEGACY_ALIASES.get(key);
                if (alias != null) {
                    replacement = byName.get(normalize(alias));
                    if (replacement == null) {
                        log.warn("Alias '{}' -> '{}' zeigt ins Leere - steht die Übung noch "
                                + "im Katalog?", old.getName(), alias);
                    }
                }
            }
            if (replacement == null) {
                unmatched.add(old);
                continue;
            }
            matched++;
            repointedPlans += routineExerciseRepository.repointExercise(old, replacement);
            repointedSets += exerciseSetRepository.repointExercise(old, replacement);
        }

        // Nach dem Umbiegen neu erheben, nicht vorher: die Verweise von eben sind weg.
        Set<Long> stillReferenced = new HashSet<>(routineExerciseRepository.findReferencedExerciseIds());
        stillReferenced.addAll(exerciseSetRepository.findReferencedExerciseIds());

        List<Exercise> deletable = legacy.stream()
                .filter(e -> !stillReferenced.contains(e.getId()))
                .toList();
        exerciseRepository.deleteAll(deletable);

        int kept = legacy.size() - deletable.size();
        log.info("Katalog-Migration: {} von {} Altübungen über den Namen gematcht "
                        + "({} Planzeilen, {} geloggte Sätze umgehängt). "
                        + "{} Altzeilen gelöscht, {} bleiben (noch referenziert).",
                matched, legacy.size(), repointedPlans, repointedSets, deletable.size(), kept);

        if (!unmatched.isEmpty()) {
            log.info("Ohne Entsprechung im neuen Katalog: {}{}",
                    unmatched.stream().limit(15).map(Exercise::getName).toList(),
                    unmatched.size() > 15 ? " … (+" + (unmatched.size() - 15) + " weitere)" : "");
        }
    }

    /**
     * Namensindex des neuen Katalogs.
     *
     * <p>Sechs Uebungen kommen im Quelldatensatz doppelt vor (dieselbe Uebung, zwei IDs).
     * Der erste Treffer gewinnt, und weil die Ressource nach ID sortiert ist, ist das
     * deterministisch - nicht davon abhaengig, in welcher Reihenfolge die DB liefert.
     */
    private Map<String, Exercise> indexByName(List<Exercise> catalog) {
        List<Exercise> sorted = catalog.stream()
                .sorted((a, b) -> String.valueOf(a.getExternalId()).compareTo(String.valueOf(b.getExternalId())))
                .toList();
        Map<String, Exercise> index = new HashMap<>();
        for (Exercise exercise : sorted) {
            index.putIfAbsent(normalize(exercise.getName()), exercise);
        }
        return index;
    }

    /**
     * "3/4 Sit-Up" und "3/4 sit-up" muessen denselben Schluessel ergeben, "Barbell_Bench_Press"
     * und "barbell bench press" ebenso. Alles, was kein Buchstabe oder Ziffer ist, wird zu
     * einem einzelnen Leerzeichen.
     */
    static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
