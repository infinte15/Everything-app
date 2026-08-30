package com.Finn.everything_app.seed;

import com.Finn.everything_app.model.MuscleGroup;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Das Mapping ist nur dann eine Uebersetzung und keine Ratemaschine, wenn es das Vokabular des
 * Datensatzes vollstaendig abdeckt. Diese Tests lesen deshalb {@code data/exercisedb.json}
 * selbst und pruefen gegen das, was wirklich drinsteht - nicht gegen eine abgeschriebene
 * Liste, die beim naechsten Datensatz-Refresh still veraltet.
 */
class ExerciseDbMuscleMappingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private List<ExerciseDbEntry> catalog() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("data/exercisedb.json")) {
            assertNotNull(in, "data/exercisedb.json fehlt - tools/build-exercisedb.py laufen lassen");
            return MAPPER.readValue(in, new TypeReference<List<ExerciseDbEntry>>() {});
        }
    }

    @Test
    void coversEveryTargetInTheCatalogue() throws Exception {
        Set<String> unmapped = new TreeSet<>();
        for (ExerciseDbEntry entry : catalog()) {
            if (!ExerciseDbMuscleMapping.knowsPrimary(entry.target())) {
                unmapped.add(entry.target());
            }
        }
        assertTrue(unmapped.isEmpty(),
                "unzugeordnete target-Werte, ExerciseDbMuscleMapping.PRIMARY ergänzen: " + unmapped);
    }

    @Test
    void coversEverySecondaryMuscleInTheCatalogue() throws Exception {
        Set<String> unmapped = new TreeSet<>();
        for (ExerciseDbEntry entry : catalog()) {
            for (String muscle : entry.secondaryMuscles()) {
                if (!ExerciseDbMuscleMapping.knowsSecondary(muscle)) {
                    unmapped.add(muscle);
                }
            }
        }
        assertTrue(unmapped.isEmpty(),
                "unzugeordnete secondary_muscles, ExerciseDbMuscleMapping.SECONDARY ergänzen: " + unmapped);
    }

    @Test
    void cardioIsTheOnlyTargetWithoutAMuscle() {
        assertEquals(MuscleGroup.CARDIO, ExerciseDbMuscleMapping.primary("cardiovascular system"));
        assertEquals(MuscleGroup.ABDOMINALS, ExerciseDbMuscleMapping.primary("abs"));
        assertEquals(MuscleGroup.SHOULDERS, ExerciseDbMuscleMapping.primary("delts"));
        assertEquals(MuscleGroup.MIDDLE_BACK, ExerciseDbMuscleMapping.primary("upper back"));
    }

    @Test
    void unknownValuesResolveToNullInsteadOfAGuess() {
        assertNull(ExerciseDbMuscleMapping.primary("gluteus interruptus"));
        assertNull(ExerciseDbMuscleMapping.primary(null));
        assertNull(ExerciseDbMuscleMapping.primary(""));
    }

    @Test
    void secondaryDropsThePrimaryMuscleAndCollapsesDuplicates() {
        // "traps" und "trapezius" sind derselbe Muskel unter zwei Namen - die Koerper-Grafik
        // darf ihn nicht zweimal zaehlen.
        Set<MuscleGroup> muscles = ExerciseDbMuscleMapping.secondary(
                List.of("traps", "trapezius", "shoulders"), null);
        assertEquals(Set.of(MuscleGroup.TRAPS, MuscleGroup.SHOULDERS), muscles);

        // Der bereits als primaer gesetzte Muskel faellt raus.
        Set<MuscleGroup> withoutPrimary = ExerciseDbMuscleMapping.secondary(
                List.of("traps", "shoulders"), MuscleGroup.SHOULDERS);
        assertEquals(Set.of(MuscleGroup.TRAPS), withoutPrimary);
    }

    @Test
    void unknownSecondaryValuesAreDroppedNotGuessed() {
        Set<MuscleGroup> muscles = ExerciseDbMuscleMapping.secondary(
                List.of("chest", "musculus imaginarius"), null);
        assertEquals(Set.of(MuscleGroup.CHEST), muscles);

        assertTrue(ExerciseDbMuscleMapping.secondary(null, null).isEmpty());
    }
}
