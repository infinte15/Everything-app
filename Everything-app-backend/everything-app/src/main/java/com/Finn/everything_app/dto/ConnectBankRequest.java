package com.Finn.everything_app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Start der Zustimmung: welches Institut der Nutzer in der Liste ausgewaehlt hat. */
@Data
public class ConnectBankRequest {

    @NotBlank(message = "Institut erforderlich")
    private String aspspName;

    /** Zweistelliger Laendercode; ohne Angabe DE. */
    private String aspspCountry;
}
