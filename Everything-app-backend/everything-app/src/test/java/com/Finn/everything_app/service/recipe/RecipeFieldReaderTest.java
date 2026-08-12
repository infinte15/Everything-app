package com.Finn.everything_app.service.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeFieldReaderTest {

    @Test
    void liestIsoDauern() {
        assertEquals(25, RecipeFieldReader.minutes("PT25M"));
        assertEquals(60, RecipeFieldReader.minutes("PT1H0M"));
        assertEquals(75, RecipeFieldReader.minutes("PT1H15M"));
        assertEquals(0, RecipeFieldReader.minutes(null));
        assertEquals(0, RecipeFieldReader.minutes(""));
    }

    // Von Hand geschriebenes JSON-LD haelt sich nicht an ISO-8601 - und eine 0 waere dort eine
    // verschenkte Angabe.
    @Test
    void liestAuchAusgeschriebeneDauern() {
        assertEquals(30, RecipeFieldReader.minutes("30 mins"));
        assertEquals(75, RecipeFieldReader.minutes("1 hour 15 minutes"));
        assertEquals(90, RecipeFieldReader.minutes("1 Stunde 30 Minuten"));
        assertEquals(20, RecipeFieldReader.minutes("20"));
        assertEquals(0, RecipeFieldReader.minutes("nach Gefühl"));
    }

    @Test
    void liestPortionen() {
        assertEquals(4, RecipeFieldReader.readYield("4"));
        assertEquals(6, RecipeFieldReader.readYield("6 servings"));
        assertEquals(2, RecipeFieldReader.readYield("2 Portionen"));
        // Ohne Angabe und bei Unsinn die uebliche Vorgabe statt einer 0.
        assertEquals(4, RecipeFieldReader.readYield(null));
        assertEquals(4, RecipeFieldReader.readYield("nach Belieben"));
        assertEquals(4, RecipeFieldReader.readYield("500"));
    }

    // Der haeufigste Mangel an fremdem JSON-LD - ohne das stuenden die spitzen Klammern
    // woertlich in der Zubereitung.
    @Test
    void nimmtAuszeichnungAusSchritten() {
        assertEquals("Den Ofen vorheizen.",
                RecipeFieldReader.stripHtml("<p>Den Ofen <b>vorheizen</b>.</p>"));
        assertEquals("Öl & Salz", RecipeFieldReader.stripHtml("&Ouml;l &amp; Salz"));
        // Ohne spitze Klammern gar nicht erst durch den Parser.
        assertEquals("400 g Mehl", RecipeFieldReader.stripHtml("400 g Mehl"));
        assertNull(RecipeFieldReader.stripHtml(null));
    }

    @Test
    void entferntFuehrendeNummern() {
        assertEquals("Mehl sieben", RecipeFieldReader.stripLeadingNumber("1. Mehl sieben"));
        assertEquals("Mehl sieben", RecipeFieldReader.stripLeadingNumber("2) Mehl sieben"));
        // Eine Menge am Zeilenanfang ist keine Nummerierung.
        assertEquals("400 g Mehl", RecipeFieldReader.stripLeadingNumber("400 g Mehl"));
    }

    @Test
    void bildetSchwierigkeitenAufDasEigeneVokabularAb() {
        assertEquals("Einfach", RecipeFieldReader.mapDifficulty("easy"));
        assertEquals("Einfach", RecipeFieldReader.mapDifficulty("Simpel und leicht"));
        assertEquals("Aufwendig", RecipeFieldReader.mapDifficulty("difficult"));
        assertEquals("Mittel", RecipeFieldReader.mapDifficulty("medium"));
        // Unbekanntes wird nicht geraten - der Aufrufer setzt dann seine Vorgabe.
        assertNull(RecipeFieldReader.mapDifficulty("kniffelig"));
        assertNull(RecipeFieldReader.mapDifficulty(null));
    }
}
