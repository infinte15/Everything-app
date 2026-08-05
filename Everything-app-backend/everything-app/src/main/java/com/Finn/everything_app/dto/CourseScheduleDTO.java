package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Ein wiederkehrender Termin im Stundenplan.
 *
 * Gilt nur innerhalb des Semesters seines Moduls — {@code SmartSchedulerService} filtert danach.
 * Ohne Semester (oder ohne Datumsgrenzen daran) blockiert er unbegrenzt jede Woche; deshalb
 * stehen {@code semesterLabel} und die beiden Kursfelder als Lesefelder mit drin: sonst ist im
 * Frontend nicht zu sehen, warum ein Termin im Kalender auftaucht oder eben nicht.
 */
@Data
public class CourseScheduleDTO {
    private Long id;

    /** Kommt aus dem Pfad, nicht aus dem Rumpf — hier nur lesend. */
    private Long courseId;
    private String courseName;
    private String courseColor;
    private String courseInstructor;
    private String semesterLabel;

    @NotNull(message = "Wochentag erforderlich")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "Startzeit erforderlich")
    private LocalTime startTime;

    @NotNull(message = "Endzeit erforderlich")
    private LocalTime endTime;

    @Size(max = 255, message = "Ort darf maximal 255 Zeichen lang sein")
    private String location;
}
