package com.Finn.everything_app.service.recipe;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceNamesTest {

    private String from(String url) {
        return SourceNames.fromUri(URI.create(url));
    }

    @Test
    void nimmtDenHostOhneWww() {
        assertEquals("chefkoch.de", from("https://www.chefkoch.de/rezepte/1/x.html"));
        assertEquals("chefkoch.de", from("https://chefkoch.de/x"));
        assertEquals("kochblog.example", from("https://m.kochblog.example/x"));
    }

    // Nicht auf die registrierbare Domaene kuerzen: "cooking.nytimes.com" sagt einem Menschen
    // mehr als "nytimes.com", und "bbc.co.uk" richtig zu kuerzen braeuchte eine
    // Public-Suffix-Liste.
    @Test
    void behaeltSprechendeUnterdomaenen() {
        assertEquals("cooking.nytimes.com", from("https://cooking.nytimes.com/recipes/1"));
        assertEquals("bbc.co.uk", from("https://www.bbc.co.uk/food/recipes/1"));
    }

    @Test
    void raeumtSchreibweisenAuf() {
        assertEquals("chefkoch.de", from("https://WWW.CHEFKOCH.DE/x"));
        assertEquals("chefkoch.de", from("https://www.chefkoch.de./x"));
        assertEquals("chefkoch.de", from("https://www.chefkoch.de:443/x"));
    }

    // Punycode ist kein Name, den man an ein Rezept schreibt.
    @Test
    void zeigtUmlautNamenLesbar() {
        assertEquals("bücher.example", from("https://xn--bcher-kva.example/x"));
    }
}
