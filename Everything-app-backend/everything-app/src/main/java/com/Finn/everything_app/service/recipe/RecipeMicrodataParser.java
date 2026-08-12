package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.dto.RecipeDTO;
import com.Finn.everything_app.dto.RecipeImportPreviewDTO;
import com.Finn.everything_app.dto.RecipeIngredientDTO;
import com.Finn.everything_app.dto.RecipeStepDTO;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Liest ein Rezept aus Microdata - {@code itemtype="…/Recipe"} mit {@code itemprop}-Attributen.
 *
 * <p>Dieselben Felder wie im JSON-LD, nur ueber die Seite verstreut statt in einem Block. Aeltere
 * Blogs und Seiten, die ihre Vorlage seit Jahren nicht angefasst haben, liefern nur das - fuer
 * die ist diese Klasse die zweite Stufe der Kette im {@link RecipeUrlImporter}.
 *
 * <p>RDFa ({@code property="schema:recipeIngredient"}) wird bewusst nicht gelesen: verschwindend
 * selten, und ein dritter Dialekt fuer dieselbe Sache kostet mehr, als er einbringt.
 *
 * <p>Die Umrechnungen teilt er sich mit {@link RecipeJsonLdParser} ueber
 * {@link RecipeFieldReader} - Zeiten, Portionen, Auszeichnung. Was hier eigen ist, ist nur das
 * Herausholen des Werts aus einem Element: der steht mal im Text, mal in {@code content}, mal in
 * {@code datetime} oder {@code src}.
 */
@Component
@RequiredArgsConstructor
public class RecipeMicrodataParser {

    private static final String ROOT_SELECTOR = "[itemtype~=(?i)schema\\.org/Recipe]";

    private final IngredientParser ingredientParser;
    private final RecipeJsonLdParser jsonLdParser;

    public Optional<RecipeImportPreviewDTO> tryParse(Document document, String sourceUrl,
                                                     String sourceName) {
        Element root = document.selectFirst(ROOT_SELECTOR);
        if (root == null) {
            return Optional.empty();
        }

        List<String> warnings = new ArrayList<>();
        RecipeDTO dto = new RecipeDTO();

        dto.setName(RecipeFieldReader.trimTo(value(root, "name"), 200));
        if (dto.getName() == null) {
            // Ohne itemprop=name traegt fast immer die Ueberschrift den Titel.
            Element heading = document.selectFirst("h1");
            dto.setName(RecipeFieldReader.trimTo(heading == null ? null : heading.text(), 200));
        }
        if (dto.getName() == null) {
            dto.setName("Rezept von " + sourceName);
            warnings.add("Kein Titel gefunden.");
        }

        dto.setDescription(RecipeFieldReader.trimTo(value(root, "description"), 2000));
        dto.setImageUrl(RecipeFieldReader.trimTo(value(root, "image"), 500));
        dto.setServings(RecipeFieldReader.readYield(value(root, "recipeYield")));

        int prep = RecipeFieldReader.minutes(value(root, "prepTime"));
        int cook = RecipeFieldReader.minutes(value(root, "cookTime"));
        int total = RecipeFieldReader.minutes(value(root, "totalTime"));
        if (cook == 0 && total > prep) {
            cook = total - prep;
        }
        dto.setPrepTimeMinutes(prep);
        dto.setCookTimeMinutes(cook);
        if (prep == 0 && cook == 0) {
            warnings.add("Keine Zeitangabe gefunden - bitte selbst eintragen.");
        }

        String rawCategory = value(root, "recipeCategory");
        String category = jsonLdParser.mapCategory(rawCategory);
        dto.setCategory(category);
        if ("Sonstiges".equals(category) && rawCategory != null && !rawCategory.isBlank()) {
            warnings.add("Kategorie nicht erkannt - bitte auswählen.");
        }

        String difficulty = RecipeFieldReader.mapDifficulty(value(root, "difficulty"));
        dto.setDifficulty(difficulty != null ? difficulty : "Mittel");

        dto.setIngredients(readIngredients(root));
        if (dto.getIngredients().isEmpty()) {
            warnings.add("Keine Zutaten gefunden - bitte selbst ergänzen.");
        }

        dto.setSteps(readSteps(root));
        if (dto.getSteps().isEmpty()) {
            warnings.add("Keine Zubereitung gefunden - bitte selbst ergänzen.");
        }

        readNutrition(root, dto);

        dto.setSourceUrl(RecipeFieldReader.trimTo(sourceUrl, 500));
        dto.setSourceName(RecipeFieldReader.trimTo(sourceName, 100));

        // Ein Wurzelelement ohne Zutaten und ohne Schritte ist keine halbe Vorschau wert - dann
        // faellt die Kette weiter auf den Seitentext.
        if (dto.getIngredients().isEmpty() && dto.getSteps().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RecipeImportPreviewDTO(dto, warnings));
    }

    private List<RecipeIngredientDTO> readIngredients(Element root) {
        List<RecipeIngredientDTO> result = new ArrayList<>();
        // "ingredients" ist der alte Name aus schema.org 1.x und steht auf genau den Seiten,
        // die auch noch Microdata benutzen.
        for (Element element : select(root, "recipeIngredient", "ingredients")) {
            String line = RecipeFieldReader.stripHtml(textOf(element));
            if (line == null || line.isBlank()) {
                continue;
            }
            ParsedIngredient parsed = ingredientParser.parse(line);
            result.add(new RecipeIngredientDTO(
                    null,
                    parsed.amount(),
                    parsed.unit(),
                    RecipeFieldReader.trimTo(parsed.name(), 200),
                    RecipeFieldReader.trimTo(parsed.note(), 200),
                    RecipeFieldReader.trimTo(parsed.rawText(), 300),
                    null));
        }
        return result;
    }

    /**
     * Zubereitungsschritte.
     *
     * <p>Zwei Bauformen: entweder je ein {@code itemprop="recipeInstructions"} pro Schritt, oder
     * ein einziges Element, das die ganze Anleitung als Liste oder Absaetze enthaelt. Im zweiten
     * Fall wird eine Ebene tiefer gegangen, sonst stuende die komplette Zubereitung als ein
     * Schritt da.
     */
    private List<RecipeStepDTO> readSteps(Element root) {
        List<RecipeStepDTO> result = new ArrayList<>();
        List<Element> holders = select(root, "recipeInstructions", "instructions");

        if (holders.size() == 1) {
            Element holder = holders.get(0);
            List<Element> parts = holder.select("[itemprop=itemListElement], li, p");
            if (!parts.isEmpty()) {
                for (Element part : parts) {
                    addStep(result, part.text());
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
            for (String line : textOf(holder).split("\\R")) {
                addStep(result, line);
            }
            return result;
        }

        for (Element holder : holders) {
            addStep(result, textOf(holder));
        }
        return result;
    }

    private void addStep(List<RecipeStepDTO> steps, String text) {
        String cleaned = RecipeFieldReader.stripLeadingNumber(RecipeFieldReader.stripHtml(text));
        if (cleaned != null && !cleaned.isBlank()) {
            steps.add(new RecipeStepDTO(null, cleaned));
        }
    }

    private void readNutrition(Element root, RecipeDTO dto) {
        Integer calories = firstNumber(value(root, "calories"));
        if (calories != null) dto.setCalories(calories);
        Integer protein = firstNumber(value(root, "proteinContent"));
        if (protein != null) dto.setProtein(protein.doubleValue());
        Integer carbs = firstNumber(value(root, "carbohydrateContent"));
        if (carbs != null) dto.setCarbs(carbs.doubleValue());
        Integer fat = firstNumber(value(root, "fatContent"));
        if (fat != null) dto.setFat(fat.doubleValue());
    }

    private Integer firstNumber(String text) {
        if (text == null) return null;
        var matcher = java.util.regex.Pattern.compile("\\d+").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group()) : null;
    }

    // ── Werte aus Elementen ───────────────────────────────────────────────────────────────

    private List<Element> select(Element root, String... props) {
        List<Element> found = new ArrayList<>();
        for (String prop : props) {
            found.addAll(root.select("[itemprop=" + prop + "]"));
            if (!found.isEmpty()) {
                return found;
            }
        }
        return found;
    }

    private String value(Element root, String prop) {
        List<Element> found = select(root, prop);
        return found.isEmpty() ? null : textOf(found.get(0));
    }

    /**
     * Der Wert eines ausgezeichneten Elements.
     *
     * <p>Die Reihenfolge ist nicht beliebig: {@code <meta itemprop="prepTime" content="PT20M">}
     * hat gar keinen Text, {@code <time datetime="PT20M">20 Minuten</time>} hat Text, der sich
     * nicht als Dauer lesen laesst, und {@code <img itemprop="image">} traegt die Adresse in
     * {@code src}. Erst zuletzt der sichtbare Text.
     */
    private String textOf(Element element) {
        String content = element.attr("content");
        if (!content.isBlank()) {
            return content.trim();
        }
        String datetime = element.attr("datetime");
        if (!datetime.isBlank()) {
            return datetime.trim();
        }
        // Nur bei Elementen, die von sich aus eine Adresse tragen. Ein <a itemprop=
        // "recipeIngredient" href="/zutat/mehl">200 g Mehl</a> kommt vor, und dort ist der Text
        // die Zutat und nicht die Verweisadresse.
        if (element.is("img, link, source, video, audio, embed")) {
            // absUrl loest relative Adressen gegen die Seite auf - "/bilder/1.jpg" allein
            // waere im Kochbuch ein leerer Rahmen.
            String url = element.hasAttr("src") ? element.absUrl("src") : element.absUrl("href");
            if (!url.isBlank()) {
                return url;
            }
        }
        return element.text().trim();
    }
}
