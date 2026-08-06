package com.Finn.everything_app.service;

import java.util.Locale;
import java.util.Set;

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

    /**
     * Rechtsformen und Zusaetze, die keinen Unterscheidungswert tragen.
     *
     * <p>Wird <strong>tokenweise</strong> angewandt und nicht als Textersatz. Der Unterschied ist
     * nicht kosmetisch: ein {@code replace("ag", " ")} macht aus "rewe sagt danke" ein
     * "rewe s t danke", und ab da gruppiert nichts mehr zusammen, was zusammengehoert.
     *
     * <p>Mehrwortige Zusaetze stehen als einzelne Token darin ("sagt", "danke"), weil die
     * Zerlegung ohnehin vorher passiert.
     */
    private static final Set<String> NOISE_TOKENS = Set.of(
            "gmbh", "mbh", "ag", "kgaa", "kg", "ohg", "ek", "ug", "gbr", "se", "ev",
            "sarl", "bv", "nv", "sa", "spa", "plc", "ltd", "inc", "llc", "ab", "as", "oy", "aps",
            "co", "und", "deutschland", "germany", "europe", "european", "international", "eu",
            "sagt", "danke", "dank", "vielen", "ihr", "einkauf");

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
        // Satzzeichen raus, Umlaute bleiben - "baeckerei" und "bäckerei" sind verschiedene Quellen.
        value = value.replaceAll("[^\\p{L}\\p{N}\\s&+-]", " ");

        StringBuilder result = new StringBuilder();
        for (String token : value.split("\\s+")) {
            // Ein-Zeichen-Reste stammen aus Abkuerzungspunkten ("s.a.r.l." wird zu "s a r l") und
            // tragen fuer sich genommen nichts bei.
            if (token.length() < 2 || NOISE_TOKENS.contains(token) || token.matches("\\d+")) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(token);
        }

        String normalized = result.toString();
        // Ein Ein-Zeichen-Rest ist kein brauchbarer Schluessel.
        return normalized.length() < 2 ? "" : normalized;
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
