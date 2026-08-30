package com.Finn.everything_app.seed;

import com.Finn.everything_app.model.Exercise;
import com.Finn.everything_app.model.MuscleGroup;
import com.Finn.everything_app.repository.ExerciseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Der Seeder ist in Tests per {@code app.exercise-seed.enabled=false} abgeschaltet; hier wird
 * er absichtlich von Hand gegen die H2-Datenbank ausgefuehrt.
 *
 * <p>{@code @Transactional} ist noetig, um die Lazy-Muskelmengen lesen zu koennen: im laufenden
 * Server haelt Open-Session-in-View die Session offen, in einem reinen Testkontext gibt es
 * keinen Web-Request, der das tun wuerde.
 */
@SpringBootTest
@Transactional
class ExerciseCatalogSeederTest {

    @Autowired ExerciseRepository exerciseRepository;
    @Autowired ObjectMapper objectMapper;

    private ExerciseCatalogSeeder seeder;

    @BeforeEach
    void setUp() {
        exerciseRepository.deleteAll();
        seeder = new ExerciseCatalogSeeder(exerciseRepository, objectMapper);
    }

    @Test
    void seedsCatalogAndIsIdempotent() throws Exception {
        seeder.run(null);
        long afterFirstRun = exerciseRepository.count();

        assertEquals(1324, afterFirstRun,
                "der ExerciseDB-Katalog hat 1324 Übungen - eine andere Zahl heißt, dass "
                        + "data/exercisedb.json nicht zur erwarteten Quelle passt");

        seeder.run(null);

        assertEquals(afterFirstRun, exerciseRepository.count(),
                "ein zweiter Start darf den Katalog nicht verdoppeln");
    }

    @Test
    void everySeededExerciseHasTheFieldsTheUiRelies0n() throws Exception {
        seeder.run(null);

        List<Exercise> all = exerciseRepository.findAll();
        for (Exercise exercise : all) {
            assertNotNull(exercise.getExternalId(), "externalId trägt die Idempotenz");
            assertNotNull(exercise.getName());
            assertNotNull(exercise.getImageUrl(), "ohne Bild bleibt die Übungsliste leer");
            assertTrue(exercise.getImageUrl().startsWith("https://"),
                    "Bild-URL muss absolut sein: " + exercise.getImageUrl());
            assertTrue(exercise.getImageUrl().endsWith(".jpg"),
                    "Vorschaubild ist ein JPG: " + exercise.getImageUrl());
            // Der Grund fuer den Katalogwechsel: jede Uebung hat eine Animation, nicht nur
            // ein Teil von ihnen. Faellt das hier durch, ist der Wechsel sinnlos geworden.
            assertNotNull(exercise.getAnimationUrl(),
                    "jede Katalog-Übung braucht eine Animation: " + exercise.getName());
            assertTrue(exercise.getAnimationUrl().endsWith(".gif"),
                    "Animation ist ein GIF: " + exercise.getAnimationUrl());
            assertNotNull(exercise.getMuscleGroup(), "muscle_group ist NOT NULL");
            assertFalse(exercise.getPrimaryMuscles().isEmpty(),
                    "ohne primäre Muskelgruppe kann die Körper-Grafik nichts einfärben");
            assertTrue(exercise.getIsSystem());
            assertNull(exercise.getCreatedBy(), "Katalog-Übungen gehören keinem User");
            assertNotNull(exercise.getDefaultRestSeconds());

            // Die Spiegel-Spalte muss zur primären Muskelgruppe passen.
            MuscleGroup primary = exercise.getPrimaryMuscles().iterator().next();
            assertEquals(primary.getSlug(), exercise.getMuscleGroup());

            // Ein Muskel darf nicht zugleich primär und unterstützend sein, sonst zählt die
            // Körper-Grafik dieselbe Fläche doppelt.
            assertFalse(exercise.getSecondaryMuscles().contains(primary),
                    "primäre Muskelgruppe darf nicht auch sekundär sein: " + exercise.getName());
        }
    }

    /**
     * Der Seeder faellt bei unbekanntem Vokabular auf {@link MuscleGroup#CARDIO} zurueck statt
     * zu raten. Genau 29 Uebungen sind echte Ausdauer-Uebungen ("cardiovascular system"); mehr
     * heisst, dass {@link ExerciseDbMuscleMapping} eine Luecke hat.
     */
    @Test
    void everyTargetMapsToARealMuscleExceptCardio() throws Exception {
        seeder.run(null);

        long cardio = exerciseRepository.findAll().stream()
                .filter(e -> MuscleGroup.CARDIO.getSlug().equals(e.getMuscleGroup()))
                .count();

        assertEquals(29, cardio,
                "nur die Ausdauer-Übungen dürfen auf CARDIO fallen - jede weitere ist eine "
                        + "Lücke im Mapping");
    }

    @Test
    void searchFindsSeededExercisesByName() throws Exception {
        seeder.run(null);

        var page = exerciseRepository.search(
                "bench", null, null, null, null, false, java.util.Set.of(""), 1L,
                org.springframework.data.domain.PageRequest.of(0, 5));

        assertTrue(page.getTotalElements() > 0, "\"bench\" sollte Treffer liefern");
        assertTrue(page.getContent().size() <= 5, "die Seitengröße muss eingehalten werden");
        assertTrue(page.getContent().stream()
                        .allMatch(e -> e.getName().toLowerCase().contains("bench")));
    }

    @Test
    void searchFiltersByMuscle() throws Exception {
        seeder.run(null);

        var page = exerciseRepository.search(
                null, MuscleGroup.CHEST, null, null, null, false, java.util.Set.of(""), 1L,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertTrue(page.getTotalElements() > 0);
        assertTrue(page.getContent().stream()
                        .allMatch(e -> e.getPrimaryMuscles().contains(MuscleGroup.CHEST)));
    }
}
