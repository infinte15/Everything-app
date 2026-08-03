package com.Finn.everything_app.model;

import com.Finn.everything_app.exception.BadRequestException;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Geschlossenes Muskel-Vokabular.
 *
 * <p>Die 17 Konstanten entsprechen exakt den Werten, die in der free-exercise-db (CC0) in
 * {@code primaryMuscles} / {@code secondaryMuscles} vorkommen. Der {@code slug} ist der
 * gemeinsame Schluessel zwischen Seeder, DTOs und der Koerper-Grafik im Flutter-Client -
 * dort werden damit die Muskelflaechen der Silhouette eingefaerbt.
 */
public enum MuscleGroup {

    ABDOMINALS("abdominals", "Bauch"),
    ABDUCTORS("abductors", "Abduktoren"),
    ADDUCTORS("adductors", "Adduktoren"),
    BICEPS("biceps", "Bizeps"),
    CALVES("calves", "Waden"),
    CHEST("chest", "Brust"),
    FOREARMS("forearms", "Unterarme"),
    GLUTES("glutes", "Gesaess"),
    HAMSTRINGS("hamstrings", "Beinbeuger"),
    LATS("lats", "Latissimus"),
    LOWER_BACK("lower back", "Unterer Ruecken"),
    MIDDLE_BACK("middle back", "Mittlerer Ruecken"),
    NECK("neck", "Nacken"),
    QUADRICEPS("quadriceps", "Quadrizeps"),
    SHOULDERS("shoulders", "Schultern"),
    TRAPS("traps", "Trapez"),
    TRICEPS("triceps", "Trizeps");

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
