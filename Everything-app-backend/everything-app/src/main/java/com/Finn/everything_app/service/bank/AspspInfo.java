package com.Finn.everything_app.service.bank;

/**
 * Ein Institut aus der Bankenliste.
 *
 * <p>Es gibt keine ID - Enable Banking identifiziert ein Institut ueber {@code (name, country)}.
 * {@link #group} fasst Verbuende zusammen ("Volksbanken Raiffeisenbanken"), was die einzige
 * Moeglichkeit ist, die mehreren hundert regionalen Sparkassen in der Auswahl zu buendeln.
 *
 * @param redirectSupported ob der Ablauf ueber einen Browser-Redirect laeuft. Manche Verbuende
 *                          koennen nur DECOUPLED oder EMBEDDED - dort wuerde ein Redirect in einem
 *                          leeren Browserfenster enden, die Oberflaeche muss sie also aussortieren.
 * @param maxConsentDays    laengstmoegliche Gueltigkeit der Zustimmung in Tagen. Bei den meisten
 *                          Instituten 180, aber ablesen statt annehmen.
 */
public record AspspInfo(
        String name,
        String country,
        String logoUrl,
        String group,
        boolean beta,
        boolean redirectSupported,
        int maxConsentDays) {
}
