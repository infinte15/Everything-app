package com.Finn.everything_app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ergebnis eines Abrufs.
 *
 * <p>{@link #warnings} traegt, was kein Abbruch ist, aber ohne Erklaerung wie ein Defekt aussieht -
 * ein uebersprungenes Fremdwaehrungskonto etwa, oder ein von der Bank nicht freigegebenes Konto.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankSyncResultDTO {

    private int accounts;
    private int imported;
    private int skipped;
    private List<String> warnings;
}
