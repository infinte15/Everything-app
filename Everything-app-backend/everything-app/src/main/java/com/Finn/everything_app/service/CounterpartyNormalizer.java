package com.Finn.everything_app.service;

import java.util.Locale;

/**
 * Bringt den Namen einer Gegenpartei auf eine vergleichbare Form.
 *
 * <p>Wird an zwei Stellen gebraucht, die zusammenpassen muessen: der Kategorisierer legt daraus
 * gelernte Regeln an, und die Vertragserkennung gruppiert danach. Zwei getrennte Implementierungen
 * wuerden auseinanderlaufen, und man merkte es erst daran, dass eine gelernte Regel auf den eigenen
 * Vertrag nicht mehr passt.
 *
 * <p>Entfernt wird, was zwischen zwei Buchungen derselben Quelle wechselt: Rechtsformen,
 * Filial- und Terminalnummern, Datumsfragmente, Kartennummern, Referenznummern. Was uebrig bleibt,
 * ist der Teil, an dem ein Mensch die Gegenpartei wiedererkennt.
 */
public final class CounterpartyNormalizer {

    private CounterpartyNormalizer() {
    }

    /** Rechtsformen und Zusaetze, die keinen Unterscheidungswert tragen. */
    private static final String[] NOISE_WORDS = {
            "gmbh & co. kg", "gmbh und co kg", "gmbh & co kg",
            "gmbh", "ag", "kgaa", "e.k.", "ohg", "kg", "mbh",
            "s.a.r.l.", "sarl", "b.v.", "n.v.", "s.a.", "s.p.a.", "plc", "ltd", "inc",
            "se & co", "se", "ug", "gbr", "e.v.", "ev",
            "deutschland", "germany", "europe", "international",
            "sagt danke", "danke", "vielen dank",
    };

    /**
     * Vergleichsform: klein, ohne Rechtsform, ohne Ziffernbloecke, ohne Mehrfach-Leerzeichen.
     *
     * <p>Beispiel: {@code "REWE SAGT DANKE. 12345//KONSTANZ/DE"} ergibt {@code "rewe"},
     * {@code "Netflix International B.V."} ergibt {@code "netflix"}.
     *
     * @return normalisierter Name, nie {@code null}; leer, wenn nichts Verwertbares uebrig bleibt
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }

        String value = raw.toLowerCase(Locale.ROOT).trim();

        // Alles ab dem ersten Trennzeichen typischer Verwendungszweck-Anhaenge abschneiden.
        int cut = indexOfAny(value, "//", " / ", "|", ";");
        if (cut > 2) {
            value = value.substring(0, cut);
        }

        // Ziffernfolgen ab drei Stellen: Filial-, Terminal-, Karten-, Referenznummern und Daten.
        value = value.replaceAll("\\d{3,}", " ");
        // Datumsfragmente wie 01.08. oder 2026-08-01
        value = value.replaceAll("\\b\\d{1,2}[./-]\\d{1,2}([./-]\\d{2,4})?\\b", " ");
        // Uhrzeiten
        value = value.replaceAll("\\b\\d{1,2}:\\d{2}\\b", " ");

        for (String noise : NOISE_WORDS) {
            value = value.replace(noise, " ");
        }

        // Satzzeichen raus, Umlaute bleiben - "baeckerei" und "bäckerei" sind verschiedene Quellen.
        value = value.replaceAll("[^\\p{L}\\p{N}\\s&+-]", " ");
        value = value.replaceAll("\\s+", " ").trim();

        // Ein Ein-Zeichen-Rest ist kein brauchbarer Schluessel.
        return value.length() < 2 ? "" : value;
    }

    private static int indexOfAny(String value, String... needles) {
        int best = -1;
        for (String needle : needles) {
            int index = value.indexOf(needle);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }
}
