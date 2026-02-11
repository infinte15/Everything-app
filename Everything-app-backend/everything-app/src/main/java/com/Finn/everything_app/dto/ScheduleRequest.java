package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Data
public class ScheduleRequest {
    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;
}