package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class GradeDTO {
    private Long id;

    @NotBlank(message = "Prüfungsname erforderlich")
    private String examName;

    @NotNull(message = "Kurs erforderlich")
    private Long courseId;
    private String courseName;

    // Uni-Skala. Die Validierung liegt nur hier, nicht auf der Entität und nicht als
    // Check-Constraint — Bestandsnoten über 5,0 bleiben also lesbar, nur neue Schreibzugriffe
    // werden abgewiesen.
    @NotNull(message = "Note erforderlich")
    @DecimalMin(value = "1.0", message = "Note muss zwischen 1,0 und 5,0 liegen")
    @DecimalMax(value = "5.0", message = "Note muss zwischen 1,0 und 5,0 liegen")
    private Double grade;

    @Min(value = 0, message = "Gewichtung muss positiv sein")
    @Max(value = 100, message = "Gewichtung darf maximal 100% sein")
    private Integer weight;

    private LocalDate examDate;

    private String examType;

    /** false = Schein: wird angezeigt, zählt aber nicht in den Modulschnitt. */
    private Boolean countsTowardGrade;

    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
