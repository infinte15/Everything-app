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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Befuellt den Uebungs-Katalog beim Start aus {@code data/exercisedb.json}.
 *
 * <p>Quelle ist <a href="https://github.com/hasaneyldrm/exercises-dataset">
 * hasaneyldrm/exercises-dataset</a> (1324 Uebungen), erzeugt von
 * {@code tools/build-exercisedb.py}. Anders als die zuvor benutzte free-exercise-db hat dort
 * jede einzelne Uebung ein Vorschaubild und eine Animation.
 *
 * <p><b>Medien liegen nicht im Repository.</b> Gesetzt werden nur URLs auf einen oeffentlichen
 * Spiegel; die Bilder und Animationen sind (c) Gym visual (https://gymvisual.com/) und werden
 * vom Client zur Laufzeit geladen und gecached. Die Metadaten selbst stehen unter MIT.
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

    /**
     * jsDelivr spiegelt das Quell-Repository und liefert mit CORS-Freigabe aus, was der Client
     * fuer die Bilder braucht. Die Pfade in {@code image}/{@code gif_url} sind bereits relativ
     * zur Repository-Wurzel ("images/0001-2gPfomN.jpg").
     */
    private static final String MEDIA_BASE =
            "https://cdn.jsdelivr.net/gh/hasaneyldrm/exercises-dataset@main/";

    static final String SOURCE = "exercisedb";
    private static final String RESOURCE_PATH = "data/exercisedb.json";
    private static final int CHUNK_SIZE = 200;

    /**
     * Koerperregionen, bei denen die Uebungen ueberwiegend mehrgelenkig und schwer sind.
     * ExerciseDB fuehrt kein {@code mechanic}-Feld, ueber das sich compound/isolation direkt
     * ablesen liesse - die Region ist der beste vorhandene Ersatz fuer eine Default-Pause.
     */
    private static final Set<String> LONG_REST_BODY_PARTS =
            Set.of("upper legs", "lower legs", "back", "chest");
    private static final int LONG_REST_SECONDS = 120;
    private static final int SHORT_REST_SECONDS = 60;

    private final ExerciseRepository exerciseRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        Set<String> existing = exerciseRepository.findAllExternalIds();

        List<ExerciseDbEntry> entries = readCatalog();
        if (entries.isEmpty()) {
            log.warn("Übungs-Katalog {} ist leer oder fehlt - kein Seeding", RESOURCE_PATH);
            return;
        }

        // Unbekanntes Vokabular wird gesammelt statt je Zeile geloggt: bei einem
        // Datensatz-Refresh sind das sonst schnell hunderte identische Warnungen.
        Set<String> unknownMuscles = new TreeSet<>();

        List<Exercise> toInsert = new ArrayList<>();
        for (ExerciseDbEntry entry : entries) {
            if (entry.id() == null || existing.contains(entry.id())) {
                continue;
            }
            toInsert.add(toExercise(entry, unknownMuscles));
        }

        if (!unknownMuscles.isEmpty()) {
            log.warn("Unbekanntes Muskel-Vokabular im Katalog, nicht zugeordnet: {}. "
                    + "ExerciseDbMuscleMapping ergänzen.", unknownMuscles);
        }

        for (int i = 0; i < toInsert.size(); i += CHUNK_SIZE) {
            exerciseRepository.saveAll(toInsert.subList(i, Math.min(i + CHUNK_SIZE, toInsert.size())));
        }
        if (toInsert.isEmpty()) {
            log.info("Übungs-Katalog bereits vollständig ({} Einträge), kein Seeding nötig", existing.size());
        } else {
            log.info("Übungs-Katalog geseedet: {} neue Übungen ({} waren bereits vorhanden)",
                    toInsert.size(), existing.size());
        }

        refreshMuscles(entries);
    }

    /**
     * Bringt die Muskel-Zuordnung bestehender Katalog-Zeilen auf den Stand von
     * {@link ExerciseDbMuscleMapping}.
     *
     * <p><b>Warum das noetig ist.</b> Der Seeder oben ueberspringt jede {@code externalId}, die
     * es schon gibt - richtig so, sonst wuerde jeder Start 1324 Zeilen neu schreiben. Damit
     * erreicht eine Aenderung am Mapping aber nur frische Datenbanken. Genau das ist beim
     * Feinerwerden der Zuordnung passiert: "obliques" sollte nicht mehr auf den Bauch fallen,
     * tat es auf jeder gewachsenen Datenbank aber weiter, und die Koerpergrafik zeigte
     * weiterhin die alte, groebere Einfaerbung.
     *
     * <p>Verglichen wird gegen den Katalog, geschrieben nur bei Unterschied. Ein Lauf ohne
     * Aenderung kostet einen Vergleich je Zeile und kein einziges UPDATE.
     *
     * <p>Dass das beim Start bezahlbar ist, haengt am {@code @BatchSize(200)} auf
     * {@link Exercise#getPrimaryMuscles()} und {@link Exercise#getSecondaryMuscles()}: die
     * Muskeln aller 1324 Zeilen kommen so in rund 14 Abfragen statt in 2648.
     *
     * <p>Ruehrt nur {@code source = exercisedb} an. Selbst angelegte Uebungen gehoeren dem
     * Nutzer; ihre Muskeln kommen aus dem Formular, nicht aus dem Katalog.
     */
    private void refreshMuscles(List<ExerciseDbEntry> entries) {
        Map<String, ExerciseDbEntry> byId = new HashMap<>();
        for (ExerciseDbEntry entry : entries) {
            if (entry.id() != null) {
                byId.put(entry.id(), entry);
            }
        }

        List<Exercise> changed = new ArrayList<>();

        for (Exercise exercise : exerciseRepository.findBySource(SOURCE)) {
            ExerciseDbEntry entry = byId.get(exercise.getExternalId());
            if (entry == null) {
                continue;
            }

            MuscleGroup primary = ExerciseDbMuscleMapping.primary(entry.target());
            if (primary == null) {
                primary = MuscleGroup.CARDIO;
            }
            Set<MuscleGroup> secondary =
                    ExerciseDbMuscleMapping.secondary(entry.secondaryMuscles(), primary);

            Set<MuscleGroup> wantedPrimary = new LinkedHashSet<>(Set.of(primary));
            if (wantedPrimary.equals(exercise.getPrimaryMuscles())
                    && secondary.equals(exercise.getSecondaryMuscles())) {
                continue;
            }

            exercise.setPrimaryMuscles(wantedPrimary);
            exercise.setSecondaryMuscles(secondary);
            exercise.setMuscleGroup(primary.getSlug());
            changed.add(exercise);
        }

        if (changed.isEmpty()) {
            return;
        }
        for (int i = 0; i < changed.size(); i += CHUNK_SIZE) {
            exerciseRepository.saveAll(changed.subList(i, Math.min(i + CHUNK_SIZE, changed.size())));
        }
        log.info("Muskel-Zuordnung von {} Katalog-Übungen aktualisiert", changed.size());
    }

    private List<ExerciseDbEntry> readCatalog() throws Exception {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<ExerciseDbEntry>>() {});
        }
    }

    private Exercise toExercise(ExerciseDbEntry entry, Set<String> unknownMuscles) {
        Exercise exercise = new Exercise();
        exercise.setExternalId(entry.id());
        exercise.setName(entry.name());
        exercise.setEquipment(entry.equipment());
        // ExerciseDB kennt weder Schwierigkeitsgrad noch mechanic; die Koerperregion ist die
        // einzige Kategorie, die es liefert, und passt zu den bestehenden Filter-Chips.
        exercise.setCategory(entry.bodyPart());
        exercise.setSource(SOURCE);
        exercise.setIsSystem(true);
        exercise.setCreatedBy(null);

        if (entry.instructions() != null && !entry.instructions().isEmpty()) {
            exercise.setInstructions(String.join("\n\n", entry.instructions()));
        }

        if (entry.image() != null) {
            exercise.setImageUrl(MEDIA_BASE + entry.image());
        }
        if (entry.gifUrl() != null) {
            exercise.setAnimationUrl(MEDIA_BASE + entry.gifUrl());
        }

        MuscleGroup primary = ExerciseDbMuscleMapping.primary(entry.target());
        if (primary == null) {
            if (entry.target() != null && !entry.target().isBlank()) {
                unknownMuscles.add(entry.target());
            }
            // NOT NULL will einen Wert, und geraten wird nicht: eine Uebung ohne zuordenbaren
            // Zielmuskel ist fachlich dasselbe wie eine Ausdauer-Uebung - keine Flaeche.
            primary = MuscleGroup.CARDIO;
        }
        exercise.setPrimaryMuscles(new LinkedHashSet<>(Set.of(primary)));

        for (String muscle : entry.secondaryMuscles() == null ? List.<String>of() : entry.secondaryMuscles()) {
            if (!ExerciseDbMuscleMapping.knowsSecondary(muscle)) {
                unknownMuscles.add(muscle);
            }
        }
        exercise.setSecondaryMuscles(ExerciseDbMuscleMapping.secondary(entry.secondaryMuscles(), primary));

        // muscle_group ist NOT NULL und spiegelt die primäre Muskelgruppe.
        exercise.setMuscleGroup(primary.getSlug());

        exercise.setDefaultRestSeconds(LONG_REST_BODY_PARTS.contains(entry.bodyPart())
                ? LONG_REST_SECONDS
                : SHORT_REST_SECONDS);

        return exercise;
    }
}
