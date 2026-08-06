package com.Finn.everything_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Antwort auf eine Umkategorisierung.
 *
 * <p>{@link #affectedCount} zaehlt die weiteren Buchungen derselben Gegenpartei - damit kann die App
 * "auch auf 23 frühere Buchungen anwenden?" anbieten, statt es stillschweigend zu tun oder
 * stillschweigend zu lassen.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecategorizeResultDTO {

    private FinanceTransactionDTO transaction;
    private int affectedCount;
    private boolean appliedToPast;
}
