package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SemesterDTO {
    private Long id;

    @NotBlank(message = "Bezeichnung erforderlich")
    @Size(max = 50, message = "Bezeichnung darf maximal 50 Zeichen lang sein")
    private String label;

    private LocalDate startDate;
    private LocalDate endDate;
    private Integer orderIndex;
    private Boolean isCurrent;

    /** Abgeleitet, nur lesend: wie viele Module hängen dran und wie viele ECTS sind das. */
    private Integer moduleCount;
    private Integer totalEcts;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
