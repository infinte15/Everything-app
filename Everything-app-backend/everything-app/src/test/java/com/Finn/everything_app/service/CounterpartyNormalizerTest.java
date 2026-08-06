package com.Finn.everything_app.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die Normalisierung ist die gemeinsame Grundlage von gelernten Regeln und Vertragserkennung.
 *
 * <p>Laufen die beiden auseinander, faellt das nicht als Fehler auf, sondern als merkwuerdiges
 * Verhalten: eine gelernte Regel greift auf den eigenen Vertrag nicht mehr. Deshalb steht hier fest,
 * was genau wegfaellt.
 */
class CounterpartyNormalizerTest {

    @Test
    void filialUndTerminalnummernFallenWeg() {
        // Derselbe Supermarkt, drei Buchungen, drei verschiedene Rohtexte.
        assertEquals("rewe", CounterpartyNormalizer.normalize("REWE SAGT DANKE. 12345//KONSTANZ/DE"));
        assertEquals("rewe", CounterpartyNormalizer.normalize("REWE 998877"));
        assertEquals("rewe", CounterpartyNormalizer.normalize("  rewe  "));
    }

    @Test
    void rechtsformenWerdenNurAlsGanzesWortEntfernt() {
        // Regression: als Textersatz angewandt machte "ag" aus "rewe sagt danke" ein "rewe s t
        // danke" - ab da gruppierte nichts mehr zusammen, was zusammengehört.
        assertEquals("rewe", CounterpartyNormalizer.normalize("REWE SAGT DANKE"));
        assertEquals("hagebaumarkt", CounterpartyNormalizer.normalize("Hagebaumarkt"));
        assertEquals("kglass", CounterpartyNormalizer.normalize("KGlass"));
    }

    @Test
    void verschiedeneQuellenBleibenVerschieden() {
        // Die Normalisierung darf nur Rauschen entfernen. Zieht sie zwei Händler zusammen,
        // entsteht daraus ein Fantasievertrag über den Median beider Beträge.
        assertNotEquals(CounterpartyNormalizer.normalize("REWE"),
                CounterpartyNormalizer.normalize("EDEKA Sued"));
        assertNotEquals(CounterpartyNormalizer.normalize("Amazon Prime"),
                CounterpartyNormalizer.normalize("Amazon Music"));
    }

    @Test
    void rechtsformenFallenWeg() {
        assertEquals("netflix", CounterpartyNormalizer.normalize("Netflix International B.V."));
        assertEquals("spotify", CounterpartyNormalizer.normalize("Spotify AB"));
        assertEquals("vodafone", CounterpartyNormalizer.normalize("Vodafone GmbH"));
    }

    @Test
    void datumUndUhrzeitFallenWeg() {
        assertEquals("edeka", CounterpartyNormalizer.normalize("EDEKA 01.08.2026 14:32"));
    }

    @Test
    void umlauteBleibenErhalten() {
        // "baeckerei" und "bäckerei" sind zwei verschiedene Schreibweisen derselben Quelle, aber
        // ein Zusammenfuehren waere geraten - lieber zwei Schluessel als ein falscher.
        assertEquals("bäckerei dreher", CounterpartyNormalizer.normalize("Bäckerei Dreher"));
    }

    @Test
    void nullUndUnbrauchbaresErgebenLeer() {
        assertEquals("", CounterpartyNormalizer.normalize(null));
        assertEquals("", CounterpartyNormalizer.normalize("   "));
        // Ein einzelnes Zeichen taugt nicht als Gruppierungsschluessel - daraus wuerde eine Regel,
        // die auf beinahe jede Buchung passt.
        assertEquals("", CounterpartyNormalizer.normalize("X"));
        assertEquals("", CounterpartyNormalizer.normalize("123456"));
    }
}
