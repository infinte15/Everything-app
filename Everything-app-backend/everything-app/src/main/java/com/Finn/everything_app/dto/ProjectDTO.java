package com.Finn.everything_app.dto;

import com.Finn.everything_app.model.ProjectStatus;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Data
public class ProjectDTO {
    private Long id;

    @NotBlank
    private String name;

    private String description;
    private LocalDate startDate;
    private LocalDate targetEndDate;
    private LocalDate actualEndDate;
    private ProjectStatus status;
    private Integer completionPercentage;
    private Integer tasksTotal;
    private Integer tasksCompleted;
}