package com.Finn.everything_app.model;

/**
 * Einnahme oder Ausgabe - und die einzige dokumentierte Bruecke zum deutschen Alt-String.
 *
 * <p>{@link FinanceTransaction#getType()} ist historisch ein {@code String} mit den Werten
 * "EINNAHME"/"AUSGABE". Daran haengen die Flutter-App, die JPQL-Literale in
 * {@code FinanceTransactionRepository} und rund zwanzig Stream-Filter in
 * {@code FinanceTransactionService} - die Konvention wird deshalb nicht angetastet. Neuer Code
 * (z.B. {@link Contract#getDirection()}) arbeitet typsicher mit diesem Enum und uebersetzt an der
 * Grenze mit {@link #toLegacy()} bzw. {@link #fromLegacy(String)}.
 */
public enum TransactionType {

    INCOME("EINNAHME"),
    EXPENSE("AUSGABE");

    private final String legacyValue;

    TransactionType(String legacyValue) {
        this.legacyValue = legacyValue;
    }

    /** Der Wert, den {@code FinanceTransaction.type} in der Datenbank traegt. */
    public String toLegacy() {
        return legacyValue;
    }

    /**
     * Uebersetzt "EINNAHME"/"AUSGABE" zurueck ins Enum - und akzeptiert der Bequemlichkeit halber
     * auch die Enum-Namen selbst.
     *
     * <p>Gibt bei unbekanntem oder fehlendem Wert {@code null} zurueck statt zu werfen: der
     * einzige heutige Aufrufer bekommt einen rohen Pfadparameter, und der soll in einer 400er-
     * Antwort landen, nicht in einer ungeprueften {@code IllegalArgumentException} und damit in
     * einer 500.
     */
    public static TransactionType fromLegacy(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        for (TransactionType type : values()) {
            if (type.legacyValue.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }
}
