package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.dto.RecipeImportPreviewDTO;
import com.Finn.everything_app.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versucht, ein Rezept aus einem Instagram-Beitrag zu lesen.
 *
 * <p><b>Das hier ist eine Abkuerzung, kein Verlass.</b> Instagram zeigt einem nicht angemeldeten
 * Abrufer meist eine Anmeldeseite statt der Bildunterschrift; ob ueberhaupt etwas kommt, haengt
 * am Ruf der IP-Adresse - von einem Rechner zu Hause klappt es manchmal, aus einem Rechenzentrum
 * so gut wie nie. Die frueher benutzte Abfrage {@code ?__a=1} ist tot, oEmbed verlangt ein
 * Facebook-Token und liefert Einbettungs-HTML <em>ohne</em> Text, und Beitraege und Reels
 * verhalten sich verschieden und aendern sich ohne Ankuendigung.
 *
 * <p>Deshalb ist der Fehlschlag mitgebaut und nicht bloss abgefangen: klappt es nicht, endet der
 * Import in einer Meldung, die den Nutzer in den Text-Weg schickt - und die App kann daran
 * anknuepfen, weil sie {@link #PASTE_CAPTION} als Kennung mitbekommt statt deutschen Prosatext
 * abgleichen zu muessen. Der eingefuegte Text bleibt der tragende Weg; der
 * {@link TextRecipeImporter} macht danach dieselbe Arbeit wie hier.
 */
@Component
@Slf4j
public class InstagramImporter {

    /** Kennung fuer die App: „zeig den Text-Weg und merk dir die Adresse". */
    public static final String PASTE_CAPTION = "INSTAGRAM_PASTE_CAPTION";

    private static final String HOST_SUFFIX = "instagram.com";

    /** Die Adressformen, unter denen ein Beitrag verlinkt wird. */
    private static final Pattern SHORTCODE = Pattern.compile(
            "/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)");

    /** {@code "text":"…"} innerhalb der eingebetteten Zustandsdaten. */
    private static final Pattern EMBEDDED_CAPTION = Pattern.compile(
            "\"(?:edge_media_to_caption\"\\s*:.{0,200}?\"text|caption)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
            Pattern.DOTALL);

    /** {@code 1.234 likes, 56 comments - koch on May 3, 2026: "…"} */
    private static final Pattern QUOTED_TAIL = Pattern.compile("[:\\-–]\\s*\"(.+)\"\\s*$", Pattern.DOTALL);

    /**
     * Kuerzer als das ist die Bildunterschrift abgeschnitten und kein Rezept.
     *
     * <p>Auf der Anmeldeseite steht in {@code og:description} nur die Vorschauzeile mit
     * Gefaellt-mir-Zahl und den ersten Zeichen. Die anzunehmen hiesse, aus einer Sperre eine
     * ueberzeugend aussehende, falsche Vorschau zu bauen.
     */
    private static final int MIN_CAPTION_CHARS = 200;

    private static final int MIN_INGREDIENTS = 3;

    private final RecipeWebFetcher fetcher;
    private final RecipeJsonLdParser jsonLdParser;
    private final TextRecipeImporter textRecipeImporter;
    private final boolean useCrawlerUserAgent;

    public InstagramImporter(RecipeWebFetcher fetcher,
                             RecipeJsonLdParser jsonLdParser,
                             TextRecipeImporter textRecipeImporter,
                             @Value("${app.recipe-import.instagram.crawler-ua:false}")
                             boolean useCrawlerUserAgent) {
        this.fetcher = fetcher;
        this.jsonLdParser = jsonLdParser;
        this.textRecipeImporter = textRecipeImporter;
        this.useCrawlerUserAgent = useCrawlerUserAgent;
    }

    /**
     * Die Kennung, mit der Instagram Suchmaschinen die {@code og}-Angaben herausgibt.
     *
     * <p>Sie erhoeht die Trefferquote deutlich - und sie behauptet gegenueber Instagram, etwas zu
     * sein, das man nicht ist. Deshalb standardmaessig aus und ueber
     * {@code app.recipe-import.instagram.crawler-ua=true} einzuschalten: das ist eine
     * Entscheidung, die jemand treffen soll, und keine, die stillschweigend eingebaut ist.
     */
    private static final String CRAWLER_UA = "facebookexternalhit/1.1";

    public static boolean handles(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.equals(HOST_SUFFIX) || host.endsWith("." + HOST_SUFFIX);
    }

    public RecipeImportPreviewDTO importFrom(URI uri) {
        URI target = normalize(uri);

        RecipeWebFetcher.FetchedPage page;
        try {
            page = fetcher.fetch(target, useCrawlerUserAgent ? CRAWLER_UA : null);
        } catch (BadRequestException e) {
            // Eine Sperre ist hier der Normalfall und keine Ueberraschung - also nicht die
            // allgemeine Fehlermeldung, sondern der Hinweis auf den Weg, der funktioniert.
            log.debug("Instagram-Abruf von {} fehlgeschlagen: {}", target, e.getMessage());
            throw pasteCaption();
        }

        String caption = findCaption(page.document());
        if (caption == null) {
            throw pasteCaption();
        }

        RecipeImportPreviewDTO preview;
        try {
            preview = textRecipeImporter.importFrom(caption, "Instagram", uri.toString());
        } catch (BadRequestException e) {
            throw pasteCaption();
        }

        if (preview.getRecipe().getIngredients().size() < MIN_INGREDIENTS
                || preview.getRecipe().getSteps().isEmpty()) {
            // Etwas gelesen, aber kein Rezept. Eine halbe Vorschau waere hier schlechter als
            // der ehrliche Hinweis: der Nutzer sieht die Bildunterschrift ja vor sich.
            throw pasteCaption();
        }

        List<String> warnings = new ArrayList<>(preview.getWarnings());
        warnings.add("Instagram gibt oft nur einen Ausschnitt der Bildunterschrift heraus - "
                + "bitte vergleichen und ergänzen.");

        String image = HtmlTextExtractor.metaContent(page.document(), "og:image");
        if (image != null && preview.getRecipe().getImageUrl() == null) {
            preview.getRecipe().setImageUrl(RecipeFieldReader.trimTo(image, 500));
            // Die Bildadressen von Instagram sind signiert und laufen nach Stunden bis Tagen ab.
            // Ohne den Hinweis steht im Kochbuch spaeter ein leerer Rahmen, und niemand weiss,
            // woran es lag.
            warnings.add("Das Bild von Instagram bleibt nur kurze Zeit erreichbar.");
        }

        return new RecipeImportPreviewDTO(preview.getRecipe(), warnings);
    }

    private BadRequestException pasteCaption() {
        return new BadRequestException(
                "Instagram gibt den Text nicht heraus. Kopier die Bildunterschrift in der App "
                        + "und füg sie hier ein - die Adresse merke ich mir.",
                PASTE_CAPTION);
    }

    /**
     * Bringt jede Beitragsadresse auf {@code https://www.instagram.com/p/<code>/}.
     *
     * <p>Reels, IGTV und Adressen mit vorangestelltem Nutzernamen zeigen auf denselben Beitrag;
     * die Abfrageparameter ({@code ?igsh=…}) sind Herkunftsmarken und gehoeren nicht mitgeschickt.
     * Kurzlinks ({@code /share/…}) tragen keinen Code - die laufen ueber die normale
     * Weiterleitungsverfolgung des {@link RecipeWebFetcher}, der Host bleibt ja erlaubt.
     */
    static URI normalize(URI uri) {
        Matcher matcher = SHORTCODE.matcher(uri.getPath() == null ? "" : uri.getPath());
        if (matcher.find()) {
            return URI.create("https://www.instagram.com/p/" + matcher.group(1) + "/");
        }
        return uri;
    }

    // ── Bildunterschrift suchen ───────────────────────────────────────────────────────────

    /** Drei Stellen, an denen der Text stehen kann - in der Reihenfolge ihrer Verlaesslichkeit. */
    private String findCaption(Document document) {
        String fromJsonLd = fromJsonLd(document);
        if (usable(fromJsonLd)) {
            return fromJsonLd;
        }

        String fromScript = fromEmbeddedJson(document);
        if (usable(fromScript)) {
            return fromScript;
        }

        String fromMeta = fromMetaDescription(document);
        if (usable(fromMeta)) {
            return fromMeta;
        }
        return null;
    }

    /**
     * Brauchbar ist ein Text, der mehr ist als die Vorschauzeile.
     *
     * <p>Ein Rezept hat Zeilen. Was einzeilig und kurz ist, ist die abgeschnittene Anzeige von
     * der Anmeldeseite.
     */
    private boolean usable(String caption) {
        if (caption == null || caption.isBlank()) {
            return false;
        }
        return caption.contains("\n") || caption.length() >= MIN_CAPTION_CHARS;
    }

    private String fromJsonLd(Document document) {
        for (JsonNode block : jsonLdParser.extractBlocks(document)) {
            String caption = firstTextField(block, "caption", "articleBody", "description");
            if (caption != null) {
                return caption;
            }
        }
        return null;
    }

    private String firstTextField(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode entry : node) {
                String value = firstTextField(entry, fields);
                if (value != null) return value;
            }
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    /** Der laengste Treffer aus den eingebetteten Zustandsdaten - der ist die Bildunterschrift. */
    private String fromEmbeddedJson(Document document) {
        String longest = null;
        for (Element script : document.select("script")) {
            Matcher matcher = EMBEDDED_CAPTION.matcher(script.data());
            while (matcher.find()) {
                String candidate = unescapeJson(matcher.group(1));
                if (longest == null || candidate.length() > longest.length()) {
                    longest = candidate;
                }
            }
        }
        return longest;
    }

    private String fromMetaDescription(Document document) {
        String description = HtmlTextExtractor.metaContent(
                document, "og:description", "description");
        if (description == null) {
            return null;
        }
        Matcher quoted = QUOTED_TAIL.matcher(description.trim());
        return quoted.find() ? quoted.group(1).trim() : description.trim();
    }

    /** {@code \n}, {@code \"} und {@code \\uXXXX} aus einem JSON-Zeichenkettenwert aufloesen. */
    static String unescapeJson(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i == raw.length() - 1) {
                out.append(c);
                continue;
            }
            char next = raw.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (i + 4 < raw.length()) {
                        try {
                            out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            out.append(next);
                        }
                    } else {
                        out.append(next);
                    }
                }
                default -> out.append(next);
            }
        }
        return out.toString();
    }
}
