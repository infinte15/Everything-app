package com.Finn.everything_app.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Data
public class ScheduleRequest {
    @NotNull
    private LocalDate startDate;

    // Optional: ohne Enddatum plant der Server über seinen konfigurierten Horizont. So muss der
    // Client die Horizontlänge nicht kennen — sonst deckt der manuelle Lauf einen anderen
    // Zeitraum ab als die automatische Neuplanung, und die Wochen dazwischen bleiben leer.
    private LocalDate endDate;
}