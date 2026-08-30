package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ScheduledItem {
    private Task task;
    private Habit habit;
    private WorkoutSession workoutSession;
    /** Nur für CLASS: der Stundenplaneintrag, aus dem dieser Termin entstanden ist. */
    private CourseSchedule courseSchedule;
    /** Nur für PROJECT: das Projekt, dessen Wochenpensum dieser Block abdeckt. */
    private Project project;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private ScheduledItemType type;

    // Nur für Tasks, die in mehrere Blöcke aufgeteilt wurden: 1-basierte Nummer und Gesamtzahl,
    // damit im Kalender "Report (2/3)" steht. Bei einem einzigen Block bleibt chunkCount 1.
    private Integer chunkIndex;
    private Integer chunkCount;

    /**
     * Nur für PROJECT: die ISO-Woche (Montag), deren Pensum dieser Block abdeckt.
     *
     * Bewusst getrennt vom tatsächlichen Termin: verschiebt der Nutzer den Block in eine andere
     * Woche, bleibt er auf die ursprüngliche gebucht. Ohne diese Trennung stünde die verlassene
     * Woche wieder unter ihrem Pensum und bekäme einen Ersatzblock — für den Nutzer sähe das aus,
     * als wäre der verschobene Termin am alten Platz stehen geblieben.
     */
    private LocalDate targetWeekStart;

    /**
     * Nur für HABIT an festen Wochentagen: der Tag, dessen Ausführung dieser Block abdeckt.
     *
     * Dieselbe Idee wie {@link #targetWeekStart}, nur auf Tagesebene — siehe
     * {@code CalendarEvent.targetDate}. Bei flexiblen Gewohnheiten null, dort trägt die Woche.
     */
    private LocalDate targetDate;

    /**
     * Nur für TASK: aus welchem Pass dieser Block stammt. 0 = Hauptlauf, 1 = nachgerückt (hinter
     * dem Nahbereich, aber noch vor der Deadline), 2 = in die gelockerten Zeiten gequetscht.
     *
     * Gebraucht wird nur die 2: so ein Block liegt bewusst außerhalb der Arbeitszeit oder ohne
     * Pause davor, und ohne eine Notiz daran sähe das im Kalender nach einem Fehler aus.
     */
    private int reliefLevel;

    /**
     * Nur für TASK: der Block endet innerhalb des Sicherheitspuffers vor der Deadline.
     *
     * Getrennt von {@link #reliefLevel}, weil beides verschiedene Fragen beantwortet — der eine
     * sagt, WELCHER Pass den Block untergebracht hat, dieses Feld, wie knapp es geworden ist. Ein
     * Block aus dem Hauptlauf kann im Puffer liegen (die Deadline ist nah, der Puffer schrumpft
     * mit), und ein nachgerückter Block kann weit davor liegen.
     */
    private boolean insideDeadlineBuffer;
}

enum ScheduledItemType {
    TASK, HABIT, WORKOUT, PROJECT, CLASS
}