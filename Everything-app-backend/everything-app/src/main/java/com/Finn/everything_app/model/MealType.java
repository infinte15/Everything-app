package com.Finn.everything_app.model;

/**
 * Wann eine geplante Mahlzeit gegessen wird.
 *
 * <p>Bis hierher war das ein freies {@code varchar(50)}, in das je nach Aufrufer
 * {@code "FRÜHSTÜCK"}, {@code "BREAKFAST"} oder {@code "Breakfast"} geschrieben wurde - drei
 * Schreibweisen fuer dieselbe Mahlzeit, die sich gegenseitig nie gefunden haben.
 *
 * <p>Die Konstanten sind bewusst ASCII. Ein Umlaut im Enum-Namen landet in der
 * CHECK-Bedingung der Spalte und in jedem Vergleich, und genau daran ist die alte
 * Wochenplanung gescheitert. Der Umlaut gehoert in {@link #getDisplayName()}.
 */
public enum MealType {

    FRUEHSTUECK("Frühstück"),
    MITTAGESSEN("Mittagessen"),
    ABENDESSEN("Abendessen"),
    SNACK("Snack");

    private final String displayName;

    MealType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
