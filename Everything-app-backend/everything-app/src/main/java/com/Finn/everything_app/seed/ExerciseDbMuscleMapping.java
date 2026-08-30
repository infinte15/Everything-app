package com.Finn.everything_app.seed;

import com.Finn.everything_app.model.MuscleGroup;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Uebersetzt das Muskel-Vokabular des ExerciseDB-Katalogs in {@link MuscleGroup}.
 *
 * <p>Beide Wertemengen sind geschlossen und wurden vollstaendig aus
 * {@code data/exercisedb.json} erhoben: 19 verschiedene {@code target}-Werte und 40
 * verschiedene {@code secondary_muscles}-Werte. Es gibt also keinen Rest, der zur Laufzeit
 * geraten werden muesste - und genau deshalb wird auch nicht geraten: was hier fehlt, wird
 * verworfen und vom Seeder protokolliert. Ein spaeterer Datensatz-Refresh faellt so beim
 * ersten Start auf, statt still falsche Muskeln einzufaerben.
 *
 * <p>Die Zielmenge bleibt an einigen Stellen groeber als die Quelle - aber nur dort, wo die
 * Koerper-Grafik wirklich keine eigene Flaeche hat: "rhomboids", "trapezius" und "upper back"
 * auf drei eigene Konstanten abzubilden haette keine Entsprechung im Bild.
 *
 * <p>Vier Zusammenfassungen sind mit der MuscleMap-Geometrie entfallen, weil es die Flaechen
 * jetzt gibt: "obliques" ist nicht mehr Bauch, "hip flexors" nicht mehr Quadrizeps,
 * "serratus anterior" nicht mehr Brust und "shins" nicht mehr Wade.
 */
public final class ExerciseDbMuscleMapping {

    private ExerciseDbMuscleMapping() {
    }

    /**
     * {@code target} -> primaere Muskelgruppe. Deckt alle 19 vorkommenden Werte ab.
     *
     * <p>"cardiovascular system" ist der einzige Wert ohne Muskel-Entsprechung und der Grund
     * fuer {@link MuscleGroup#CARDIO}.
     */
    private static final Map<String, MuscleGroup> PRIMARY = Map.ofEntries(
            Map.entry("abs", MuscleGroup.ABDOMINALS),
            Map.entry("abductors", MuscleGroup.ABDUCTORS),
            Map.entry("adductors", MuscleGroup.ADDUCTORS),
            Map.entry("biceps", MuscleGroup.BICEPS),
            Map.entry("calves", MuscleGroup.CALVES),
            Map.entry("cardiovascular system", MuscleGroup.CARDIO),
            Map.entry("delts", MuscleGroup.SHOULDERS),
            Map.entry("forearms", MuscleGroup.FOREARMS),
            Map.entry("glutes", MuscleGroup.GLUTES),
            Map.entry("hamstrings", MuscleGroup.HAMSTRINGS),
            Map.entry("lats", MuscleGroup.LATS),
            Map.entry("levator scapulae", MuscleGroup.NECK),
            Map.entry("pectorals", MuscleGroup.CHEST),
            Map.entry("quads", MuscleGroup.QUADRICEPS),
            Map.entry("serratus anterior", MuscleGroup.SERRATUS),
            // "spine" meint im Datensatz durchgaengig die Rueckenstrecker.
            Map.entry("spine", MuscleGroup.LOWER_BACK),
            Map.entry("traps", MuscleGroup.TRAPS),
            Map.entry("triceps", MuscleGroup.TRICEPS),
            Map.entry("upper back", MuscleGroup.MIDDLE_BACK));

    /**
     * {@code secondary_muscles} -> unterstuetzende Muskelgruppe. Deckt alle 40 vorkommenden
     * Werte ab; feine anatomische Unterscheidungen fallen auf die Flaeche zusammen, die die
     * Grafik tatsaechlich zeichnen kann.
     */
    private static final Map<String, MuscleGroup> SECONDARY = Map.ofEntries(
            Map.entry("abdominals", MuscleGroup.ABDOMINALS),
            Map.entry("core", MuscleGroup.ABDOMINALS),
            Map.entry("lower abs", MuscleGroup.ABDOMINALS),
            Map.entry("obliques", MuscleGroup.OBLIQUES),
            Map.entry("back", MuscleGroup.MIDDLE_BACK),
            Map.entry("rhomboids", MuscleGroup.MIDDLE_BACK),
            Map.entry("upper back", MuscleGroup.MIDDLE_BACK),
            Map.entry("lower back", MuscleGroup.LOWER_BACK),
            Map.entry("lats", MuscleGroup.LATS),
            Map.entry("latissimus dorsi", MuscleGroup.LATS),
            Map.entry("traps", MuscleGroup.TRAPS),
            Map.entry("trapezius", MuscleGroup.TRAPS),
            Map.entry("shoulders", MuscleGroup.SHOULDERS),
            Map.entry("deltoids", MuscleGroup.SHOULDERS),
            Map.entry("rear deltoids", MuscleGroup.SHOULDERS),
            Map.entry("rotator cuff", MuscleGroup.SHOULDERS),
            Map.entry("chest", MuscleGroup.CHEST),
            Map.entry("upper chest", MuscleGroup.CHEST),
            Map.entry("biceps", MuscleGroup.BICEPS),
            Map.entry("brachialis", MuscleGroup.BICEPS),
            Map.entry("triceps", MuscleGroup.TRICEPS),
            Map.entry("forearms", MuscleGroup.FOREARMS),
            // Griff- und Handgelenksarbeit ist im Bild Unterarm.
            Map.entry("grip muscles", MuscleGroup.FOREARMS),
            Map.entry("hands", MuscleGroup.FOREARMS),
            Map.entry("wrists", MuscleGroup.FOREARMS),
            Map.entry("wrist extensors", MuscleGroup.FOREARMS),
            Map.entry("wrist flexors", MuscleGroup.FOREARMS),
            Map.entry("quadriceps", MuscleGroup.QUADRICEPS),
            Map.entry("hip flexors", MuscleGroup.HIP_FLEXORS),
            Map.entry("hamstrings", MuscleGroup.HAMSTRINGS),
            Map.entry("glutes", MuscleGroup.GLUTES),
            Map.entry("calves", MuscleGroup.CALVES),
            Map.entry("soleus", MuscleGroup.CALVES),
            Map.entry("ankles", MuscleGroup.CALVES),
            Map.entry("ankle stabilizers", MuscleGroup.CALVES),
            Map.entry("feet", MuscleGroup.CALVES),
            // Der Schienbeinmuskel ist der Gegenspieler der Wade, nicht Teil von ihr.
            Map.entry("shins", MuscleGroup.TIBIALIS),
            Map.entry("groin", MuscleGroup.ADDUCTORS),
            Map.entry("inner thighs", MuscleGroup.ADDUCTORS),
            Map.entry("sternocleidomastoid", MuscleGroup.NECK));

    /** Der {@code target}-Wert eines Datensatzes, oder {@code null} wenn unbekannt. */
    public static MuscleGroup primary(String target) {
        return PRIMARY.get(normalize(target));
    }

    /**
     * Die {@code secondary_muscles} eines Datensatzes.
     *
     * <p>Reihenfolge bleibt erhalten, Duplikate fallen weg - mehrere Quellwerte landen
     * regelmaessig auf derselben Flaeche ("traps" und "trapezius" etwa). {@code exclude} ist
     * die bereits gesetzte primaere Gruppe: sie darf nicht zusaetzlich als unterstuetzend
     * gelten, sonst zaehlt die Grafik denselben Muskel doppelt.
     */
    public static Set<MuscleGroup> secondary(List<String> muscles, MuscleGroup exclude) {
        Set<MuscleGroup> out = new LinkedHashSet<>();
        if (muscles == null) {
            return out;
        }
        for (String muscle : muscles) {
            MuscleGroup mapped = SECONDARY.get(normalize(muscle));
            if (mapped != null && mapped != exclude) {
                out.add(mapped);
            }
        }
        return out;
    }

    /** Quellwerte, die dieses Mapping nicht kennt - fuer die Warnung des Seeders. */
    public static boolean knowsPrimary(String target) {
        return PRIMARY.containsKey(normalize(target));
    }

    /** Wie {@link #knowsPrimary(String)}, fuer die unterstuetzenden Muskeln. */
    public static boolean knowsSecondary(String muscle) {
        return SECONDARY.containsKey(normalize(muscle));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
