package com.Finn.everything_app.service.recipe;

import org.jsoup.Jsoup;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die kleinen Umrechnungen, die beide Zerleger brauchen.
 *
 * <p>{@link RecipeJsonLdParser} liest sie aus JSON, {@link RecipeMicrodataParser} aus
 * Attributen - dieselben Angaben in derselben schlechten Form. Hier stehen sie einmal, statt in
 * beiden Klassen leicht verschieden.
 */
final class RecipeFieldReader {

    private RecipeFieldReader() {}

    private static final Pattern FIRST_INTEGER = Pattern.compile("\\d+");

    /** Fuehrende Nummerierung eines Schritts - die Nummer setzt die Oberflaeche selbst. */
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*\\d+[.)]\\s*");

    private static final Pattern LOOSE_HOURS = Pattern.compile(
            "(\\d{1,2})\\s*(?:h\\b|hr\\b|hrs\\b|hour|hours|std\\b|stunde|stunden)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LOOSE_MINUTES = Pattern.compile(
            "(\\d{1,3})\\s*(?:m\\b|min\\b|mins\\b|minute|minutes|minuten)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Dauer in Minuten. Unlesbares gibt 0.
     *
     * <p>Erst ISO-8601 ({@code PT25M}) - so steht es in der Spezifikation und so liefern es die
     * grossen Seiten. Danach der Notnagel fuer von Hand geschriebenes JSON-LD, das
     * {@code "30 mins"} oder {@code "1 hour 15 minutes"} hineinschreibt. Beides kommt oft genug
     * vor, dass eine 0 dort eine verschenkte Angabe waere.
     */
    static int minutes(String duration) {
        if (duration == null || duration.isBlank()) {
            return 0;
        }
        String value = duration.trim();
        try {
            return (int) Duration.parse(value).toMinutes();
        } catch (Exception ignored) {
            // Kein ISO-Format - weiter unten von Hand.
        }

        int total = 0;
        Matcher hours = LOOSE_HOURS.matcher(value);
        if (hours.find()) {
            total += Integer.parseInt(hours.group(1)) * 60;
        }
        Matcher minutes = LOOSE_MINUTES.matcher(value);
        if (minutes.find()) {
            total += Integer.parseInt(minutes.group(1));
        }
        if (total == 0) {
            // Eine nackte Zahl ("30") ist im Zweifel eine Minutenangabe.
            Matcher bare = FIRST_INTEGER.matcher(value);
            if (bare.find() && value.length() <= 3) {
                total = Integer.parseInt(bare.group());
            }
        }
        return total > 0 && total <= 60 * 48 ? total : 0;
    }

    /** Portionen aus {@code "4"}, {@code "4 Portionen"}, {@code "serves 6"}. Sonst 4. */
    static Integer readYield(String raw) {
        if (raw == null || raw.isBlank()) {
            return 4;
        }
        Matcher matcher = FIRST_INTEGER.matcher(raw);
        if (matcher.find()) {
            int value = Integer.parseInt(matcher.group());
            return value > 0 && value <= 100 ? value : 4;
        }
        return 4;
    }

    static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /** Ohne fuehrende "1." - sonst steht spaeter "1. 1. Mehl sieben". */
    static String stripLeadingNumber(String text) {
        if (text == null) {
            return null;
        }
        return LEADING_NUMBER.matcher(text.trim()).replaceFirst("").trim();
    }

    /**
     * Nimmt Auszeichnung aus einem Text heraus.
     *
     * <p>Der haeufigste Mangel an fremdem JSON-LD: die verbreiteten WordPress-Rezept-Erweiterungen
     * schreiben {@code <p>Den Ofen vorheizen…</p>} mitsamt Verweisen in {@code recipeInstructions}.
     * Ohne das hier stuenden die spitzen Klammern woertlich in der Zubereitung.
     */
    static String stripHtml(String value) {
        if (value == null) {
            return null;
        }
        if (value.indexOf('<') < 0 && value.indexOf('&') < 0) {
            return value;
        }
        // Das geschuetzte Leerzeichen kommt aus &nbsp; und sieht in der App aus wie ein
        // Zeichen, das sich nicht loeschen laesst.
        return Jsoup.parseBodyFragment(value).text().replace('\u00a0', ' ').trim();
    }

    /** Schwierigkeit fremder Seiten auf die drei eigenen Stufen abbilden. */
    static String mapDifficulty(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.GERMAN);
        if (value.contains("einfach") || value.contains("leicht")
                || value.contains("easy") || value.contains("simple")) {
            return "Einfach";
        }
        if (value.contains("aufwendig") || value.contains("schwer") || value.contains("schwierig")
                || value.contains("hard") || value.contains("difficult")) {
            return "Aufwendig";
        }
        if (value.contains("mittel") || value.contains("normal") || value.contains("medium")) {
            return "Mittel";
        }
        return null;
    }
}
