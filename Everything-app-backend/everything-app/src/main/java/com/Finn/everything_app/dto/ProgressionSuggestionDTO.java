package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.ProgressionPolicy;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Was beim naechsten Mal ansteht - abgeleitet aus dem Verlauf, nicht gespeichert.
 *
 * <p>{@link #why} ist Teil der Antwort und nicht nur Beiwerk: eine Vorgabe, die sich nicht
 * erklaert, wird im Training ignoriert.
 */
@Data
public class ProgressionSuggestionDTO {

    public enum Kind {
        /** Noch nie geloggt - die Vorgabe kommt aus der Routine. */
        FIRST,
        /** Letztes Mal geschafft, jetzt mehr. */
        UP,
        /** Gleich bleiben - Spanne noch nicht ausgereizt oder erster Fehlschlag. */
        HOLD,
        /** Zu oft haengen geblieben, Gewicht runter. */
        DELOAD,
        /** Keine Automatik fuer diese Zeile. */
        OFF
    }

    private Long routineExerciseId;
    private Long exerciseId;
    private String exerciseName;

    private ProgressionPolicy policy;
    private Kind kind;

    /** Arbeitsgewicht in kg. {@code null} bei Koerpergewichts- und Zeituebungen. */
    private Double weight;
    private Integer reps;
    private Integer sets;
    /** Vorgabe fuer Uebungen auf Zeit. */
    private Integer seconds;

    /** Wie oft in Folge an diesem Gewicht die Vorgabe verfehlt wurde. */
    private int stallCount;

    private String why;

    /** Rampe zum Arbeitsgewicht, leer wenn sich das Aufwaermen nicht lohnt. */
    private List<WarmupSetDTO> warmup = new ArrayList<>();

    /** Ein Aufwaermsatz der automatischen Rampe. */
    @Data
    public static class WarmupSetDTO {
        private Double weight;
        private Integer reps;
        /** Anteil am Arbeitsgewicht, damit die UI "40 %" anzeigen kann. */
        private Integer percent;

        public WarmupSetDTO() {
        }

        public WarmupSetDTO(Double weight, Integer reps, Integer percent) {
            this.weight = weight;
            this.reps = reps;
            this.percent = percent;
        }
    }
}
