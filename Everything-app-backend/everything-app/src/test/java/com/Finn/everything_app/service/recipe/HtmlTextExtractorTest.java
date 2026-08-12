package com.Finn.everything_app.service.recipe;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlTextExtractorTest {

    private String extract(String html) {
        return HtmlTextExtractor.extract(Jsoup.parse(html));
    }

    // Der ganze Punkt: ohne Zeilen erkennt der TextRecipeImporter nichts. Element#text() klebt
    // "400 g Mehl 1 Prise Salz" zu einer Zeile zusammen.
    @Test
    void machtAusListenpunktenEigeneZeilen() {
        String text = extract("<ul><li>400 g Mehl</li><li>1 Prise Salz</li></ul>");

        assertEquals("400 g Mehl\n1 Prise Salz", text);
    }

    @Test
    void trenntAbsaetzeUndUeberschriften() {
        String text = extract("<h1>Pfannkuchen</h1><p>Zutaten:</p><p>3 Eier</p>");

        assertEquals("Pfannkuchen\nZutaten:\n3 Eier", text);
    }

    @Test
    void wirftSkripteUndNavigationWeg() {
        String text = extract("""
                <html><body>
                <nav>Startseite Kontakt</nav>
                <script>var x = "400 g Zucker";</script>
                <style>.a{color:red}</style>
                <p>200 g Mehl</p>
                <footer>Impressum</footer>
                </body></html>
                """);

        assertEquals("200 g Mehl", text);
    }

    @Test
    void loestZeichenverweiseAuf() {
        assertEquals("50 g Öl & Salz", extract("<p>50 g &Ouml;l &amp; Salz</p>"));
    }

    // Das geschuetzte Leerzeichen aus &nbsp; sieht in der App aus wie ein Zeichen, das sich
    // nicht loeschen laesst - und der Zutatenzerleger trennt daran nicht.
    @Test
    void ersetztGeschuetzteLeerzeichen() {
        String text = extract("<p>400&nbsp;g Mehl</p>");

        assertEquals("400 g Mehl", text);
        assertFalse(text.contains(" "));
    }

    @Test
    void faesstMehrereLeerzeilenZusammen() {
        String text = extract("<p>a</p><div></div><div></div><div></div><p>b</p>");

        assertFalse(text.contains("\n\n\n"), text);
    }

    @Test
    void deckeltSehrLangeSeiten() {
        String lang = "<p>" + "Zutat ".repeat(20_000) + "</p>";

        assertTrue(extract(lang).length() <= HtmlTextExtractor.MAX_CHARS);
    }

    @Test
    void liestOffenGraphAngaben() {
        Document document = Jsoup.parse("""
                <html><head>
                <meta property="og:image" content="https://example.com/bild.jpg">
                <meta name="description" content="Ein Rezept">
                </head></html>
                """);

        assertEquals("https://example.com/bild.jpg",
                HtmlTextExtractor.metaContent(document, "og:image"));
        // name= als Rueckfall, wenn property= fehlt.
        assertEquals("Ein Rezept", HtmlTextExtractor.metaContent(document, "description"));
        assertNull(HtmlTextExtractor.metaContent(document, "og:title"));
    }
}
