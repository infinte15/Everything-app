package com.Finn.everything_app.seed;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Der Katalogwechsel darf keine Trainingsdaten kosten.
 *
 * <p>Die scharfe Kante dabei: {@code Exercise} kaskadiert {@code CascadeType.ALL} auf seine
 * {@code exerciseSets}. Eine Altzeile zu loeschen, auf die noch ein geloggter Satz zeigt,
 * wuerde diesen Satz stillschweigend mitnehmen. Genau dieser Fall wird hier erzwungen.
 */
@SpringBootTest
@Transactional
class ExerciseCatalogMigrationTest {

    @Autowired ExerciseRepository exerciseRepository;
    @Autowired RoutineRepository routineRepository;
    @Autowired RoutineExerciseRepository routineExerciseRepository;
    @Autowired WorkoutSessionRepository sessionRepository;
    @Autowired ExerciseSetRepository setRepository;
    @Autowired UserRepository userRepository;

    private ExerciseCatalogMigration migration;
    private User user;

    @BeforeEach
    void setUp() {
        migration = new ExerciseCatalogMigration(
                exerciseRepository, routineExerciseRepository, setRepository);

        user = new User();
        user.setUsername("migration-tester");
        user.setEmail("migration@test.local");
        user.setPasswordHash("x");
        user = userRepository.save(user);
    }

    private Exercise exercise(String name, String source, String externalId) {
        Exercise exercise = new Exercise();
        exercise.setName(name);
        exercise.setSource(source);
        exercise.setExternalId(externalId);
        exercise.setMuscleGroup(MuscleGroup.CHEST.getSlug());
        exercise.setPrimaryMuscles(new LinkedHashSet<>(Set.of(MuscleGroup.CHEST)));
        exercise.setIsSystem(true);
        return exerciseRepository.save(exercise);
    }

    private Routine routineWith(Exercise exercise) {
        Routine routine = new Routine();
        routine.setUser(user);
        routine.setName("Push");
        routine.setOrderIndex(0);
        routine = routineRepository.save(routine);

        RoutineExercise entry = new RoutineExercise();
        entry.setRoutine(routine);
        entry.setExercise(exercise);
        entry.setOrderIndex(0);
        entry.setTargetSets(3);
        routineExerciseRepository.save(entry);
        return routine;
    }

    private ExerciseSet loggedSet(Exercise exercise, double weight) {
        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setName("Push A");
        session.setIntensity(5);
        session = sessionRepository.save(session);

        ExerciseSet set = new ExerciseSet();
        set.setWorkoutSession(session);
        set.setExercise(exercise);
        set.setSetNumber(1);
        set.setReps(8);
        set.setWeight(weight);
        set.setIsCompleted(true);
        return setRepository.save(set);
    }

    @Test
    void repointsPlansAndLogsOntoTheNewCatalogueAndDropsTheOldRow() {
        // Dieselbe Übung, in beiden Katalogen anders geschrieben.
        Exercise old = exercise("Barbell_Bench-Press", ExerciseCatalogMigration.LEGACY_SOURCE, "Barbell_Bench-Press");
        Exercise fresh = exercise("barbell bench press", ExerciseCatalogSeeder.SOURCE, "0025");

        routineWith(old);
        ExerciseSet set = loggedSet(old, 80.0);

        migration.run(null);

        assertTrue(exerciseRepository.findById(old.getId()).isEmpty(),
                "die Altzeile hat keine Verweise mehr und darf verschwinden");
        assertEquals(fresh.getId(),
                routineExerciseRepository.findAll().get(0).getExercise().getId(),
                "die Planzeile zeigt jetzt auf den neuen Katalog");
        assertEquals(fresh.getId(),
                setRepository.findById(set.getId()).orElseThrow().getExercise().getId(),
                "der geloggte Satz zeigt jetzt auf den neuen Katalog");
        assertEquals(80.0, setRepository.findById(set.getId()).orElseThrow().getWeight(),
                "das geloggte Gewicht bleibt unverändert");
    }

    @Test
    void keepsAnUnmatchedButStillReferencedLegacyExercise() {
        // Nichts im neuen Katalog heisst so - die Zeile muss ueberleben, sonst nimmt die
        // Kaskade den geloggten Satz mit.
        Exercise orphan = exercise("Hammer Strength Widowmaker", ExerciseCatalogMigration.LEGACY_SOURCE, "orphan");
        exercise("barbell bench press", ExerciseCatalogSeeder.SOURCE, "0025");
        ExerciseSet set = loggedSet(orphan, 60.0);

        migration.run(null);

        assertTrue(exerciseRepository.findById(orphan.getId()).isPresent(),
                "eine noch referenzierte Altübung darf nicht gelöscht werden");
        assertTrue(setRepository.findById(set.getId()).isPresent(),
                "der geloggte Satz muss die Migration überleben");
        assertEquals(orphan.getId(),
                setRepository.findById(set.getId()).orElseThrow().getExercise().getId());
    }

    @Test
    void deletesAnUnmatchedLegacyExerciseNobodyUses() {
        Exercise unused = exercise("Nautilus Pullover Machine", ExerciseCatalogMigration.LEGACY_SOURCE, "unused");
        exercise("barbell bench press", ExerciseCatalogSeeder.SOURCE, "0025");

        migration.run(null);

        assertTrue(exerciseRepository.findById(unused.getId()).isEmpty(),
                "eine ungenutzte Altübung ist nur noch Ballast in der Bibliothek");
    }

    @Test
    void isANoOpWhenThereIsNothingLeftToMigrate() {
        Exercise fresh = exercise("barbell bench press", ExerciseCatalogSeeder.SOURCE, "0025");
        routineWith(fresh);

        migration.run(null);
        migration.run(null);

        assertEquals(1, exerciseRepository.findBySource(ExerciseCatalogSeeder.SOURCE).size());
        assertEquals(fresh.getId(),
                routineExerciseRepository.findAll().get(0).getExercise().getId());
    }

    @Test
    void leavesEverythingAloneWhenTheNewCatalogueIsMissing() {
        Exercise old = exercise("Barbell Bench Press", ExerciseCatalogMigration.LEGACY_SOURCE, "legacy");
        routineWith(old);

        migration.run(null);

        assertTrue(exerciseRepository.findById(old.getId()).isPresent(),
                "ohne neuen Katalog gibt es kein Ziel - dann wird nichts angefasst");
    }

    /**
     * Ein Alias, dessen Ziel es nicht gibt, faellt sonst erst beim Migrationslauf auf einer
     * echten Datenbank auf - und dann still, als "nicht gematcht".
     */
    @Test
    void everyLegacyAliasPointsAtAnExerciseThatActuallyExists() throws Exception {
        new ExerciseCatalogSeeder(exerciseRepository, new com.fasterxml.jackson.databind.ObjectMapper())
                .run(null);

        Set<String> catalogue = exerciseRepository.findBySource(ExerciseCatalogSeeder.SOURCE).stream()
                .map(e -> ExerciseCatalogMigration.normalize(e.getName()))
                .collect(java.util.stream.Collectors.toSet());

        List<String> dangling = ExerciseCatalogMigration.LEGACY_ALIASES.values().stream()
                .filter(target -> !catalogue.contains(ExerciseCatalogMigration.normalize(target)))
                .toList();

        assertTrue(dangling.isEmpty(), "Alias-Ziele fehlen im Katalog: " + dangling);
    }

    /** Die Schluessel muessen bereits normalisiert sein, sonst schlaegt der Lookup nie an. */
    @Test
    void everyLegacyAliasKeyIsAlreadyNormalized() {
        for (String key : ExerciseCatalogMigration.LEGACY_ALIASES.keySet()) {
            assertEquals(key, ExerciseCatalogMigration.normalize(key),
                    "Alias-Schlüssel muss normalisiert sein: " + key);
        }
    }

    @Test
    void usesTheAliasTableWhenTheNameDoesNotMatchDirectly() {
        Exercise old = exercise("Pullups", ExerciseCatalogMigration.LEGACY_SOURCE, "Pullups");
        Exercise fresh = exercise("pull-up", ExerciseCatalogSeeder.SOURCE, "0652");
        ExerciseSet set = loggedSet(old, 0.0);

        migration.run(null);

        assertEquals(fresh.getId(),
                setRepository.findById(set.getId()).orElseThrow().getExercise().getId(),
                "\"Pullups\" und \"pull-up\" sind dieselbe Übung");
        assertTrue(exerciseRepository.findById(old.getId()).isEmpty());
    }

    @Test
    void normalizesNamesAcrossSpellings() {
        assertEquals("3 4 sit up", ExerciseCatalogMigration.normalize("3/4 Sit-Up"));
        assertEquals("3 4 sit up", ExerciseCatalogMigration.normalize("3/4 sit-up"));
        assertEquals("barbell bench press", ExerciseCatalogMigration.normalize("Barbell_Bench_Press"));
        assertEquals("", ExerciseCatalogMigration.normalize(null));
    }

    @Test
    void picksTheLowestExternalIdWhenTheNewCatalogueHasDuplicateNames() {
        // Sechs Uebungen kommen im Quelldatensatz doppelt vor. Welche gewinnt, darf nicht
        // davon abhaengen, in welcher Reihenfolge die Datenbank liefert.
        Exercise old = exercise("Lever Chest Press", ExerciseCatalogMigration.LEGACY_SOURCE, "legacy");
        exercise("lever chest press", ExerciseCatalogSeeder.SOURCE, "0577");
        Exercise first = exercise("lever chest press", ExerciseCatalogSeeder.SOURCE, "0576");
        routineWith(old);

        migration.run(null);

        assertEquals(first.getId(), routineExerciseRepository.findAll().get(0).getExercise().getId(),
                "bei doppelten Namen gewinnt die kleinste externalId");
    }
}
