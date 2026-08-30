package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BodyWeightEntryDTO {

    private Long id;

    /** Ohne Datum zaehlt der Eintrag fuer heute. */
    private LocalDate date;

    @NotNull(message = "Gewicht erforderlich")
    @Positive(message = "Gewicht muss groesser als 0 sein")
    private Double weightKg;

    @Size(max = 500, message = "Notiz darf maximal 500 Zeichen lang sein")
    private String note;
}
