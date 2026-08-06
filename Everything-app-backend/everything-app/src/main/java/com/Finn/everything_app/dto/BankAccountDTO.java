package com.Finn.everything_app.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** Ein Konto samt Saldo. Die IBAN wird nur verkuerzt herausgegeben. */
@Data
public class BankAccountDTO {

    private Long id;
    private String displayName;

    /** Nur die letzten vier Stellen - die vollstaendige IBAN braucht die Oberflaeche nirgends. */
    private String ibanSuffix;

    private String currency;
    private Double currentBalance;
    private LocalDateTime balanceUpdatedAt;
    private Boolean syncEnabled;

    private Long connectionId;
    private String aspspName;
}
