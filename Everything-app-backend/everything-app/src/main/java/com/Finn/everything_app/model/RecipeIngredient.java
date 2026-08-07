package com.Finn.everything_app.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Eine Zutat eines Rezepts - zerlegt in Menge, Einheit und Name.
 *
 * <p>Vorher lagen alle Zutaten als ein einziger TEXT-Block im Rezept, getrennt durch
 * Zeilenumbrueche. Damit war beides unmoeglich, worauf es hier ankommt: Portionen umrechnen
 * (dafuer muss die Menge eine Zahl sein) und eine Einkaufsliste zusammenfassen (dafuer muss
 * "200 g Mehl" und "300 g Mehl" als dieselbe Zutat erkennbar sein).
 *
 * <p>{@link #rawText} haelt fest, was Import oder Nutzer tatsaechlich geschrieben haben. Der
 * Parser trifft nicht jede Zeile - was er nicht zerlegen kann, landet vollstaendig in
 * {@link #name}, und {@code rawText} bleibt als Beleg daneben stehen. Damit faellt ein
 * schlechter Parse auf das alte Verhalten zurueck, statt Text zu verlieren.
 */
@Entity
@Table(name = "recipe_ingredients", indexes = {
        @Index(name = "idx_recipe_ingredients_recipe", columnList = "recipe_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Rueckverweis, von {@code toString} und {@code equals} ausgenommen.
     *
     * <p>Ohne das laufen Lomboks generierte Methoden im Kreis: Rezept vergleicht seine
     * Zutatenliste, jede Zutat vergleicht ihr Rezept - {@code StackOverflowError} beim ersten
     * {@code equals} auf einem Rezept mit Zutaten.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Recipe recipe;

    /** Reihenfolge in der Zutatenliste, bei 0 beginnend. */
    @Column(nullable = false)
    private Integer position;

    /**
     * Menge fuer {@link Recipe#getServings()} Portionen, oder {@code null}.
     *
     * <p>Nullbar, und das ist keine Nachlaessigkeit: "Salz" und "etwas Mehl zum Ausrollen" haben
     * keine Menge. Eine erfundene 0 waere schlimmer als keine Zahl - sie wuerde mitskaliert und
     * dann als "0 g Salz" auf der Einkaufsliste stehen.
     */
    @Column(precision = 10, scale = 3)
    private BigDecimal amount;

    /** Normalisierte Einheit aus dem festen Vokabular ("g", "EL", "Prise"), oder {@code null}. */
    @Column(length = 30)
    private String unit;

    @Column(nullable = false, length = 200)
    private String name;

    /** Zusatz wie "gehäuft" oder "zum Ausrollen" - alles, was den Einkauf nicht veraendert. */
    @Column(length = 200)
    private String note;

    @Column(name = "raw_text", length = 300)
    private String rawText;

    /**
     * Ueberschrift einer Zutatengruppe ("Für den Teig"), falls die Quelle eine hatte.
     *
     * <p>Eine Spalte ohne Oberflaeche: chefkochs JSON-LD klopft Gruppen flach, also gibt es beim
     * Import nichts zu uebernehmen. Sie steht hier, damit selbst eingetragene Rezepte die
     * Information nicht verlieren, wenn spaeter doch eine Ansicht dafuer entsteht.
     */
    @Column(name = "group_label", length = 100)
    private String groupLabel;
}
