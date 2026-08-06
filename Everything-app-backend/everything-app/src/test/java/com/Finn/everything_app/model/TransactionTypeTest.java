package com.Finn.everything_app.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TransactionType ist die einzige Bruecke zwischen dem typsicheren Enum und dem deutschen
 * Alt-String in {@code FinanceTransaction.type}. Geht die kaputt, sucht die Datenbank nach Werten,
 * die in der Spalte nie stehen - und liefert stillschweigend leere Ergebnisse.
 */
class TransactionTypeTest {

    @Test
    void legacyWerteEntsprechenDenSpaltenwerten() {
        assertEquals("EINNAHME", TransactionType.INCOME.toLegacy());
        assertEquals("AUSGABE", TransactionType.EXPENSE.toLegacy());
    }

    @Test
    void fromLegacyErkenntDieDeutschenWerte() {
        assertEquals(TransactionType.INCOME, TransactionType.fromLegacy("EINNAHME"));
        assertEquals(TransactionType.EXPENSE, TransactionType.fromLegacy("AUSGABE"));
    }

    @Test
    void fromLegacyErkenntAuchDieEnumNamenUndIgnoriertGrossschreibung() {
        assertEquals(TransactionType.INCOME, TransactionType.fromLegacy("INCOME"));
        assertEquals(TransactionType.EXPENSE, TransactionType.fromLegacy(" ausgabe "));
    }

    @Test
    void fromLegacyLiefertNullStattZuWerfen() {
        // Der Aufrufer bekommt einen rohen Pfadparameter - der soll in einer 400er-Antwort landen,
        // nicht in einer ungeprueften Exception und damit in einer 500.
        assertNull(TransactionType.fromLegacy("Quatsch"));
        assertNull(TransactionType.fromLegacy(null));
        assertNull(TransactionType.fromLegacy(""));
    }

    @Test
    void jederWertUeberstehtDenRundlauf() {
        for (TransactionType type : TransactionType.values()) {
            assertEquals(type, TransactionType.fromLegacy(type.toLegacy()),
                    "Rundlauf über toLegacy/fromLegacy muss für " + type + " stabil sein");
        }
    }
}
