package com.Finn.everything_app.service.recipe;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

/**
 * Macht aus einer Seite den Text, den ein Mensch dort sieht - mit Zeilen.
 *
 * <p>Die Zeilen sind der ganze Punkt. {@code Element#text()} klebt alles zu einem Absatz
 * zusammen, und der {@link TextRecipeImporter} lebt davon, dass "200 g Mehl" eine eigene Zeile
 * ist. Ohne Zeilenstruktur erkennt er nichts, und die letzte Stufe der Kette waere wertlos.
 *
 * <p>Was hier herauskommt, ist bewusst grob. Es ist der Notnagel fuer Seiten ohne jede
 * maschinenlesbare Auszeichnung, und der {@link RecipeUrlImporter} nimmt das Ergebnis nur an,
 * wenn wirklich ein Rezept dabei herausgekommen ist.
 */
final class HtmlTextExtractor {

    private HtmlTextExtractor() {}

    /** Was auf jeder Seite steht und nie zum Rezept gehoert. */
    private static final String NOISE =
            "script, style, noscript, svg, template, iframe, form, nav, header, footer, aside, "
                    + "button, select, textarea";

    /** Nach diesen Elementen faengt eine neue Zeile an. */
    private static final String BLOCKS =
            "p, div, li, br, tr, h1, h2, h3, h4, h5, h6, section, article, blockquote, dd, dt, "
                    + "figcaption, td";

    /** Mehr als das ist keine Rezeptseite mehr - und deckt sich mit dem Limit des Text-Imports. */
    static final int MAX_CHARS = 20_000;

    static String extract(Document document) {
        Document working = document.clone();
        working.select(NOISE).remove();

        StringBuilder out = new StringBuilder();
        working.body().traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode textNode) {
                    String text = textNode.text().replace('\u00a0', ' ');
                    if (!text.isBlank()) {
                        out.append(text);
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element && element.is(BLOCKS)) {
                    out.append('\n');
                }
            }
        });

        return collapse(out.toString());
    }

    /**
     * Leerraum aufraeumen.
     *
     * <p>Die Zeilen kommen mit fuehrenden Leerzeichen aus der Einrueckung des HTML, und zwischen
     * zwei Absaetzen stehen schnell fuenf Leerzeilen. Beides stoert die Abschnittserkennung im
     * {@link TextRecipeImporter}, die auf Ueberschriften und Aufzaehlungen achtet.
     */
    private static String collapse(String raw) {
        StringBuilder out = new StringBuilder();
        int blankRun = 0;
        for (String line : raw.split("\n")) {
            String cleaned = line.replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
            if (cleaned.isEmpty()) {
                blankRun++;
                if (blankRun > 1) {
                    continue;
                }
            } else {
                blankRun = 0;
            }
            out.append(cleaned).append('\n');
            if (out.length() > MAX_CHARS) {
                break;
            }
        }
        String result = out.toString().trim();
        return result.length() <= MAX_CHARS ? result : result.substring(0, MAX_CHARS);
    }

    /** {@code og:image}, {@code og:title} - der Rest, wenn im Text nichts davon stand. */
    static String metaContent(Document document, String... properties) {
        for (String property : properties) {
            Element meta = document.selectFirst("meta[property=" + property + "]");
            if (meta == null) {
                meta = document.selectFirst("meta[name=" + property + "]");
            }
            if (meta != null) {
                String content = meta.attr("content").trim();
                if (!content.isEmpty()) {
                    return content;
                }
            }
        }
        return null;
    }
}
