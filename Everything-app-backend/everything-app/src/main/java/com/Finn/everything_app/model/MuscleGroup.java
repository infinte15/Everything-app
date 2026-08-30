package com.Finn.everything_app.model;

import com.Finn.everything_app.exception.BadRequestException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Geschlossenes Muskel-Vokabular.
 *
 * <p>Der {@code slug} ist der gemeinsame Schluessel zwischen Seeder, DTOs und der
 * Koerper-Grafik im Flutter-Client - dort werden damit die Muskelflaechen der Silhouette
 * eingefaerbt.
 *
 * <p>Die urspruenglichen 17 Konstanten stammen aus der free-exercise-db (CC0) und bleiben
 * unveraendert, damit bestehende Zeilen und die Grafik weiterlaufen. {@link #CARDIO} kam mit dem
 * Wechsel auf den ExerciseDB-Katalog dazu: dessen 29 Ausdauer-Uebungen haben kein
 * Zielmuskel-Aequivalent, und {@code exercises.muscle_group} ist NOT NULL. Die Silhouette kennt
 * fuer diesen Slug keine Flaeche und faerbt schlicht nichts ein - das ist die richtige
 * Darstellung, nicht ein Mangel.
 *
 * <p>{@link #HIP_FLEXORS}, {@link #OBLIQUES}, {@link #SERRATUS} und {@link #TIBIALIS} kamen mit
 * der neuen Koerpergeometrie dazu (MuscleMap, siehe {@code body_map_paths.dart}). Der Datensatz
 * nannte diese Muskeln immer schon - 77 Uebungen den Hueftbeuger, 72 die seitlichen
 * Bauchmuskeln -, aber die von Hand gezeichnete Silhouette hatte keine Flaeche dafuer, also
 * fielen sie bisher auf den naechstgelegenen Nachbarn. Jetzt gibt es die Flaechen, und die
 * Zuordnung darf so fein sein wie die Quelle.
 *
 * <p>Die Zuordnung des ExerciseDB-Vokabulars auf diese Konstanten macht
 * {@link com.Finn.everything_app.seed.ExerciseDbMuscleMapping}.
 */
public enum MuscleGroup {

    ABDOMINALS("abdominals", "Bauch"),
    ABDUCTORS("abductors", "Abduktoren"),
    ADDUCTORS("adductors", "Adduktoren"),
    BICEPS("biceps", "Bizeps"),
    CALVES("calves", "Waden"),
    CHEST("chest", "Brust"),
    FOREARMS("forearms", "Unterarme"),
    GLUTES("glutes", "Gesäß"),
    HAMSTRINGS("hamstrings", "Beinbeuger"),
    HIP_FLEXORS("hip flexors", "Hüftbeuger"),
    LATS("lats", "Latissimus"),
    LOWER_BACK("lower back", "Unterer Rücken"),
    MIDDLE_BACK("middle back", "Mittlerer Rücken"),
    NECK("neck", "Nacken"),
    OBLIQUES("obliques", "Seitliche Bauchmuskeln"),
    QUADRICEPS("quadriceps", "Quadrizeps"),
    SERRATUS("serratus", "Sägemuskel"),
    SHOULDERS("shoulders", "Schultern"),
    TIBIALIS("tibialis", "Schienbein"),
    TRAPS("traps", "Trapez"),
    TRICEPS("triceps", "Trizeps"),
    CARDIO("cardio", "Ausdauer");

    private final String slug;
    private final String label;

    MuscleGroup(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    public String getSlug() {
        return slug;
    }

    /** Deutsche Anzeige-Bezeichnung fuer Filter-Chips und die Koerper-Grafik. */
    public String getLabel() {
        return label;
    }

    /**
     * Die Gruppen, die die Koerper-Grafik im Client zeichnen kann - alles ausser
     * {@link #CARDIO}.
     *
     * <p>Bewusst nicht dasselbe wie {@link #values()}: der Filter in der Bibliothek soll
     * "Ausdauer" anbieten (29 Uebungen haengen daran), die Muskelbilanz dagegen nicht. Eine
     * Zeile "Ausdauer: 0 kg" in einer Auswertung, die Flaechen einfaerbt, waere nur Rauschen.
     */
    private static final List<MuscleGroup> BODY_MAP =
            Arrays.stream(values()).filter(m -> m != CARDIO).toList();

    /** @see #BODY_MAP */
    public static List<MuscleGroup> bodyMapValues() {
        return BODY_MAP;
    }

    private static final Map<String, MuscleGroup> BY_SLUG = Arrays.stream(values())
            .collect(Collectors.toMap(MuscleGroup::getSlug, Function.identity()));

    /**
     * Loest einen Slug ("lower back") oder einen Enum-Namen ("LOWER_BACK") auf.
     *
     * @throws BadRequestException bei unbekanntem Wert - lieber 400 als eine kaputte Zeile.
     */
    public static MuscleGroup fromSlug(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Muskelgruppe darf nicht leer sein");
        }
        String normalized = value.trim().toLowerCase().replace('_', ' ');
        MuscleGroup match = BY_SLUG.get(normalized);
        if (match == null) {
            throw new BadRequestException("Unbekannte Muskelgruppe: " + value);
        }
        return match;
    }

    /** Wie {@link #fromSlug(String)}, liefert aber {@code null} statt einer Exception. */
    public static MuscleGroup fromSlugOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return BY_SLUG.get(value.trim().toLowerCase().replace('_', ' '));
    }
}
