package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.dto.RecipeImportPreviewDTO;
import com.Finn.everything_app.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Liest ein Rezept von einer beliebigen Adresse.
 *
 * <p>Selbst macht die Klasse nichts ausser der Reihenfolge: pruefen, holen, und dann drei
 * Zerleger der Reihe nach fragen. Das Holen steht im {@link RecipeWebFetcher}, die Erlaubnis im
 * {@link SafeUrlValidator}, das Auseinanderfummeln in den Zerlegern - jedes Stueck fuer sich
 * pruefbar.
 *
 * <p><b>Die Kette und warum sie drei Stufen hat.</b>
 * <ol>
 *   <li>{@link RecipeJsonLdParser} - {@code schema.org/Recipe} als {@code ld+json}. Das liefert
 *       die grosse Mehrheit: die verbreiteten WordPress-Rezept-Erweiterungen, die grossen
 *       Kochseiten, die Zeitungen.</li>
 *   <li>{@link RecipeMicrodataParser} - dieselben Felder als {@code itemprop} im HTML. Aeltere
 *       Blogs, die ihre Vorlage seit Jahren nicht angefasst haben.</li>
 *   <li>Der sichtbare Seitentext durch den {@link TextRecipeImporter} - derselbe Zerleger, der
 *       eingefuegte Bildunterschriften liest. Fuer Seiten ganz ohne Auszeichnung.</li>
 * </ol>
 *
 * <p>Die dritte Stufe raet, und das wird auch so gesagt: sie wird nur angenommen, wenn wirklich
 * ein Rezept dabei herausgekommen ist, und traegt dann eine Warnung. Eine Vorschau aus dem
 * Seitenmenue waere schlimmer als eine ehrliche Fehlermeldung.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecipeUrlImporter {

    /** Weniger ist kein Rezept, sondern Seitentext, der zufaellig nach einem aussieht. */
    private static final int MIN_TEXT_INGREDIENTS = 3;
    private static final int MIN_TEXT_STEPS = 2;

    private static final String NO_RECIPE =
            "Auf dieser Seite steckt kein Rezept. Ist das die Rezeptseite oder eine Übersicht?";

    private final SafeUrlValidator validator;
    private final RecipeWebFetcher fetcher;
    private final RecipeJsonLdParser jsonLdParser;
    private final RecipeMicrodataParser microdataParser;
    private final TextRecipeImporter textRecipeImporter;
    private final InstagramImporter instagramImporter;

    public RecipeImportPreviewDTO importFrom(String url) {
        URI uri = validator.parse(url);

        if (InstagramImporter.handles(uri)) {
            return instagramImporter.importFrom(uri);
        }

        RecipeWebFetcher.FetchedPage page = fetcher.fetch(uri);
        String sourceUrl = page.finalUrl().toString();
        String sourceName = SourceNames.fromUri(page.finalUrl());

        return jsonLdParser.tryParse(page.document(), sourceUrl, sourceName)
                .or(() -> microdataParser.tryParse(page.document(), sourceUrl, sourceName))
                .or(() -> fromPageText(page.document(), sourceUrl, sourceName))
                .orElseThrow(() -> new BadRequestException(NO_RECIPE));
    }

    /**
     * Letzte Stufe: den sichtbaren Text lesen wie eine eingefuegte Bildunterschrift.
     *
     * <p>Die Schwelle ist der ganze Trick. Ungebremst findet die Heuristik auf jeder Seite
     * irgendetwas - Navigationspunkte sind kurze Zeilen, und kurze Zeilen sehen aus wie Zutaten.
     */
    private Optional<RecipeImportPreviewDTO> fromPageText(Document document, String sourceUrl,
                                                          String sourceName) {
        String text = HtmlTextExtractor.extract(document);
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        RecipeImportPreviewDTO preview;
        try {
            preview = textRecipeImporter.importFrom(text, sourceName, sourceUrl);
        } catch (BadRequestException e) {
            return Optional.empty();
        }

        if (preview.getRecipe().getIngredients().size() < MIN_TEXT_INGREDIENTS
                || preview.getRecipe().getSteps().size() < MIN_TEXT_STEPS) {
            return Optional.empty();
        }

        if (preview.getRecipe().getImageUrl() == null) {
            String image = HtmlTextExtractor.metaContent(document, "og:image", "twitter:image");
            preview.getRecipe().setImageUrl(RecipeFieldReader.trimTo(image, 500));
        }
        String title = HtmlTextExtractor.metaContent(document, "og:title");
        if (title != null && (preview.getRecipe().getName() == null
                || preview.getRecipe().getName().startsWith("Rezept von "))) {
            preview.getRecipe().setName(RecipeFieldReader.trimTo(title, 200));
        }

        List<String> warnings = new ArrayList<>();
        warnings.add("Diese Seite gibt ihr Rezept nicht maschinenlesbar heraus - was hier steht, "
                + "ist aus dem Seitentext geraten. Bitte prüfen.");
        warnings.addAll(preview.getWarnings());

        log.debug("Rezept von {} nur aus dem Seitentext gelesen", sourceUrl);
        return Optional.of(new RecipeImportPreviewDTO(preview.getRecipe(), warnings));
    }
}
