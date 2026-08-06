package com.Finn.everything_app.model;

/**
 * Herkunft einer Buchung: von Hand eingetippt oder aus dem Bankimport.
 *
 * <p>Bestandszeilen aus der Zeit vor dieser Spalte tragen {@code null} - die Spalte konnte auf der
 * bereits gefuellten Tabelle nicht als NOT NULL angelegt werden. Lesende Stellen muessen
 * {@code null} deshalb wie {@code MANUAL} behandeln; die Handmigration
 * db/manual/2026-08-06-finance-bank-import.sql zieht die Altbestaende einmalig nach.
 */
public enum TransactionSource {
    MANUAL,
    BANK
}
