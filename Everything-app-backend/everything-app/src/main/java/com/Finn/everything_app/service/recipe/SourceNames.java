package com.Finn.everything_app.service.recipe;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;

/**
 * Der Name, unter dem eine Quelle am Rezept steht.
 *
 * <p>Frueher stand hier die Zeichenkette {@code "chefkoch.de"} fest im Importer. Jetzt kommt sie
 * aus der Adresse.
 *
 * <p>Abgeschnitten wird nur {@code www.} und {@code m.} - nicht jede Unterdomaene.
 * {@code cooking.nytimes.com} sagt einem Menschen mehr als {@code nytimes.com}, und es sauber auf
 * die registrierbare Domaene zu kuerzen braeuchte eine Public-Suffix-Liste, sonst wird aus
 * {@code bbc.co.uk} ein {@code co.uk}.
 */
final class SourceNames {

    private SourceNames() {}

    static String fromUri(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return "Web";
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        try {
            // Zurueck in die lesbare Form: "xn--bcher-kva.example" ist kein Name, den man an
            // ein Rezept schreibt.
            host = IDN.toUnicode(host);
        } catch (IllegalArgumentException ignored) {
            // Dann eben in Punycode - besser als gar keine Quelle.
        }
        if (host.startsWith("www.")) {
            host = host.substring(4);
        } else if (host.startsWith("m.")) {
            host = host.substring(2);
        }
        String trimmed = RecipeFieldReader.trimTo(host, 100);
        return trimmed == null ? "Web" : trimmed;
    }
}
