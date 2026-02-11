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

    @NotNull(message = "Note erforderlich")
    @DecimalMin(value = "1.0", message = "Note muss zwischen 1.0 und 6.0 liegen")
    @DecimalMax(value = "6.0", message = "Note muss zwischen 1.0 und 6.0 liegen")
    private Double grade;

    @Min(value = 0, message = "Gewichtung muss positiv sein")
    @Max(value = 100, message = "Gewichtung darf maximal 100% sein")
    private Integer weight;

    private LocalDate examDate;

    private String examType;

    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
