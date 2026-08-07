package com.Finn.everything_app.seed;

import com.Finn.everything_app.model.Exercise;
import com.Finn.everything_app.model.MuscleGroup;
import com.Finn.everything_app.repository.ExerciseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Befuellt den Uebungs-Katalog beim Start aus {@code data/exercises.json}.
 *
 * <p>Idempotent ueber {@code Exercise.externalId}: bereits vorhandene Datensaetze werden
 * uebersprungen, im Normalfall kostet der Seeder also genau eine SELECT-Abfrage. Die
 * UNIQUE-Bedingung auf der Spalte ist die zusaetzliche Absicherung.
 *
 * <p>Abschaltbar mit {@code app.exercise-seed.enabled=false} (so laeuft er in Tests nicht).
 */
@Component
@RequiredArgsConstructor
@Slf4j
// Vor allen anderen Runnern: der Demo-Seeder baut seine Routinen aus diesem Katalog.
@Order(0)
@ConditionalOnProperty(name = "app.exercise-seed.enabled", havingValue = "true", matchIfMissing = true)
public class ExerciseCatalogSeeder implements ApplicationRunner {

    /** Bilder liegen im selben Repository; cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@main ist der Spiegel. */
    private static final String IMAGE_BASE =
            "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/";
    private static final String SOURCE = "free-exercise-db";
    private static final String RESOURCE_PATH = "data/exercises.json";
    private static final int CHUNK_SIZE = 200;

    private final ExerciseRepository exerciseRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        Set<String> existing = exerciseRepository.findAllExternalIds();

        List<FreeExerciseDbEntry> entries = readCatalog();
        if (entries.isEmpty()) {
            log.warn("Übungs-Katalog {} ist leer oder fehlt - kein Seeding", RESOURCE_PATH);
            return;
        }

        List<Exercise> toInsert = new ArrayList<>();
        for (FreeExerciseDbEntry entry : entries) {
            if (entry.id() == null || existing.contains(entry.id())) {
                continue;
            }
            toInsert.add(toExercise(entry));
        }

        if (toInsert.isEmpty()) {
            log.info("Übungs-Katalog bereits vollständig ({} Einträge), kein Seeding nötig", existing.size());
            return;
        }

        for (int i = 0; i < toInsert.size(); i += CHUNK_SIZE) {
            exerciseRepository.saveAll(toInsert.subList(i, Math.min(i + CHUNK_SIZE, toInsert.size())));
        }
        log.info("Übungs-Katalog geseedet: {} neue Übungen ({} waren bereits vorhanden)",
                toInsert.size(), existing.size());
    }

    private List<FreeExerciseDbEntry> readCatalog() throws Exception {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<FreeExerciseDbEntry>>() {});
        }
    }

    private Exercise toExercise(FreeExerciseDbEntry entry) {
        Exercise exercise = new Exercise();
        exercise.setExternalId(entry.id());
        exercise.setName(entry.name());
        exercise.setDifficulty(entry.level());
        exercise.setEquipment(entry.equipment());
        exercise.setCategory(entry.category());
        exercise.setForce(entry.force());
        exercise.setMechanic(entry.mechanic());
        exercise.setSource(SOURCE);
        exercise.setIsSystem(true);
        exercise.setCreatedBy(null);

        if (entry.instructions() != null && !entry.instructions().isEmpty()) {
            exercise.setInstructions(String.join("\n\n", entry.instructions()));
        }

        List<String> images = entry.images();
        if (images != null && !images.isEmpty()) {
            exercise.setImageUrl(IMAGE_BASE + images.get(0));
            if (images.size() > 1) {
                exercise.setImageUrlEnd(IMAGE_BASE + images.get(1));
            }
        }

        Set<MuscleGroup> primary = toMuscles(entry.primaryMuscles());
        exercise.setPrimaryMuscles(primary);
        exercise.setSecondaryMuscles(toMuscles(entry.secondaryMuscles()));
        // muscle_group ist NOT NULL und spiegelt die primäre Muskelgruppe.
        exercise.setMuscleGroup(primary.isEmpty()
                ? MuscleGroup.ABDOMINALS.getSlug()
                : primary.iterator().next().getSlug());

        exercise.setDefaultRestSeconds("compound".equalsIgnoreCase(entry.mechanic()) ? 120 : 60);

        return exercise;
    }

    private Set<MuscleGroup> toMuscles(List<String> slugs) {
        Set<MuscleGroup> result = new LinkedHashSet<>();
        if (slugs == null) {
            return result;
        }
        for (String slug : slugs) {
            MuscleGroup muscle = MuscleGroup.fromSlugOrNull(slug);
            if (muscle == null) {
                log.warn("Unbekannte Muskelgruppe '{}' im Katalog - übersprungen", slug);
                continue;
            }
            result.add(muscle);
        }
        return result;
    }
}
