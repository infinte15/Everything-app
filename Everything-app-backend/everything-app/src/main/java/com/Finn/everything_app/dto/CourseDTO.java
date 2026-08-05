package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CourseDTO {
    private Long id;

    @NotBlank(message = "Kursname erforderlich")
    @Size(max = 200, message = "Name darf maximal 200 Zeichen lang sein")
    private String name;

    private String code;
    private String instructor;

    /** Freitext-Bezeichnung. Wird serverseitig aus dem verknüpften Semester gepflegt. */
    private String semester;

    /** Verknüpftes Semester; null = keinem zugeordnet. */
    private Long semesterId;

    /** Nur lesend — die Bezeichnung des verknüpften Semesters. */
    private String semesterLabel;

    private String description;

    private LocalDate startDate;
    private LocalDate endDate;

    @Size(max = 100, message = "Farbe darf maximal 100 Zeichen lang sein")
    private String color;

    /** ECTS des Moduls. Gewichtet den Modulschnitt im Gesamtschnitt. */
    @Min(value = 0, message = "ECTS müssen positiv sein")
    private Integer ectsCredits;

    private Integer totalNotes;
    private Integer totalFlashcards;
    private Integer totalAssignments;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
