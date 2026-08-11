package com.Finn.everything_app.service.recipe;

import com.Finn.everything_app.dto.RecipeDTO;
import com.Finn.everything_app.dto.RecipeImportPreviewDTO;
import com.Finn.everything_app.dto.RecipeIngredientDTO;
import com.Finn.everything_app.dto.RecipeStepDTO;
import com.Finn.everything_app.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uebersetzt einen {@code schema.org/Recipe}-Knoten in ein {@link RecipeDTO}.
 *
 * <p>Ohne Netzzugriff und ohne Datenbank - genau deshalb ist er vom {@link ChefkochImporter}
 * getrennt: das Interessante an einem Importer ist nicht das Herunterladen, sondern das
 * Auseinanderfummeln, und das soll gegen eine gespeicherte Datei pruefbar sein.
 *
 * <p>Die Sonderfaelle stammen aus echten chefkoch-Seiten, nicht aus der Spezifikation. Was ein
 * naiver Parser hier falsch macht:
 *
 * <ul>
 *   <li>Das Rezept ist ein Knoten in {@code @graph}, nicht die Wurzel - und nicht zuverlaessig
 *       der erste. Gesucht wird ueber {@code @type}.</li>
 *   <li>{@code image} ist eine Referenz {@code {"@id": "…#primaryimage"}} und muss gegen den
 *       {@code ImageObject}-Knoten im selben Graphen aufgeloest werden.</li>
 *   <li>{@code recipeInstructions} ist eine Liste von {@code HowToSection}, deren Schritte
 *       eine Ebene tiefer liegen.</li>
 *   <li>{@code recipeYield} ist ein Array: {@code ["4", "4 Portionen"]}.</li>
 *   <li>{@code cookTime} fehlt bei vielen Rezepten - dann bleibt nur {@code totalTime} minus
 *       {@code prepTime}.</li>
 *   <li>{@code name} traegt ein angehaengtes {@code " von <Nutzername>"}.</li>
 *   <li>{@code description} ist Suchmaschinentext ("Über 2834 Bewertungen und für vorzüglich
 *       befunden"). Der echte Beschreibungstext steht in {@code abstract}.</li>
 * </ul>
 *
 * <p>Nicht gelesen wird {@code aggregateRating}: das ist die Meinung von tausend Fremden, und
 * der Space zeigt die eigene. Ebenso wenig die Schwierigkeit - die steht nur im HTML hinter
 * einem Vue-Klassenhash, der sich mit jedem Deploy aendert.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecipeJsonLdParser {

    private final ObjectMapper objectMapper;
    private final IngredientParser ingredientParser;

    private Map<String, String> categoryMapping;

    /** " von zwergenmuomi" am Ende des Titels. */
    private static final Pattern AUTHOR_SUFFIX = Pattern.compile("\\s+von\\s+\\S+$");

    /** Fuehrende Nummerierung eines Schritts - die Nummer erzeugt die Oberflaeche selbst. */
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*\\d+[.)]\\s*");

    private static final Pattern FIRST_INTEGER = Pattern.compile("\\d+");

    public RecipeImportPreviewDTO parse(JsonNode root, String sourceUrl, String sourceName) {
        List<JsonNode> graph = flattenGraph(root);
        JsonNode recipeNode = graph.stream()
                .filter(node -> hasType(node, "Recipe"))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Auf dieser Seite steckt kein Rezept. Ist das die Rezeptseite oder eine Übersicht?"));

        List<String> warnings = new ArrayList<>();
        RecipeDTO dto = new RecipeDTO();

        dto.setName(cleanName(text(recipeNode, "name")));
        if (dto.getName() == null || dto.getName().isBlank()) {
            dto.setName("Rezept von " + sourceName);
            warnings.add("Kein Titel gefunden.");
        }

        // abstract vor description: description ist bei chefkoch Suchmaschinentext.
        String description = text(recipeNode, "abstract");
        if (description == null || description.isBlank()) {
            description = text(recipeNode, "description");
        }
        dto.setDescription(trimTo(description, 2000));

        dto.setImageUrl(resolveImage(recipeNode.get("image"), graph));
        dto.setServings(readYield(recipeNode.get("recipeYield")));

        int prep = minutes(text(recipeNode, "prepTime"));
        int cook = minutes(text(recipeNode, "cookTime"));
        int total = minutes(text(recipeNode, "totalTime"));
        if (cook == 0 && total > prep) {
            // Sehr haeufig: nur prepTime und totalTime sind gesetzt.
            cook = total - prep;
        }
        dto.setPrepTimeMinutes(prep);
        dto.setCookTimeMinutes(cook);
        if (prep == 0 && cook == 0) {
            warnings.add("Keine Zeitangabe gefunden - bitte selbst eintragen.");
        }

        dto.setCategory(mapCategory(text(recipeNode, "recipeCategory")));
        // Die Schwierigkeit steht nicht im JSON-LD. Raten waere schlechter als eine ehrliche
        // Vorgabe, die im Sheet mit einem Tipp geaendert ist. "Mittel" und nicht "Normal":
        // kanonisch sind Einfach / Mittel / Aufwendig, und ein viertes Wort fuer dieselbe Stufe
        // teilt den Filter in zwei Haelften.
        dto.setDifficulty("Mittel");

        dto.setIngredients(readIngredients(recipeNode.get("recipeIngredient")));
        if (dto.getIngredients().isEmpty()) {
            warnings.add("Keine Zutaten gefunden - bitte selbst ergänzen.");
        }

        dto.setSteps(readInstructions(recipeNode.get("recipeInstructions")));
        if (dto.getSteps().isEmpty()) {
            warnings.add("Keine Zubereitung gefunden - bitte selbst ergänzen.");
        }

        readNutrition(recipeNode.get("nutrition"), dto);

        dto.setSourceUrl(sourceUrl);
        dto.setSourceName(sourceName);

        long unparsed = dto.getIngredients().stream()
                .filter(i -> i.getAmount() == null && i.getUnit() == null)
                .count();
        if (unparsed > 0 && unparsed == dto.getIngredients().size() && unparsed > 2) {
            warnings.add("Bei den Zutaten war keine Menge erkennbar - bitte nachsehen.");
        }

        return new RecipeImportPreviewDTO(dto, warnings);
    }

    // ── Graph ─────────────────────────────────────────────────────────────────────────────

    /** Alle Knoten, egal ob die Seite {@code @graph} benutzt oder das Rezept direkt liefert. */
    private List<JsonNode> flattenGraph(JsonNode root) {
        List<JsonNode> nodes = new ArrayList<>();
        if (root == null) {
            return nodes;
        }
        if (root.isArray()) {
            root.forEach(node -> nodes.addAll(flattenGraph(node)));
            return nodes;
        }
        JsonNode graph = root.get("@graph");
        if (graph != null && graph.isArray()) {
            graph.forEach(nodes::add);
        } else {
            nodes.add(root);
        }
        return nodes;
    }

    /** {@code @type} kann ein String oder eine Liste sein. */
    private boolean hasType(JsonNode node, String type) {
        JsonNode typeNode = node.get("@type");
        if (typeNode == null) return false;
        if (typeNode.isArray()) {
            for (JsonNode entry : typeNode) {
                if (type.equals(entry.asText())) return true;
            }
            return false;
        }
        return type.equals(typeNode.asText());
    }

    /**
     * Loest das Bild auf - auch, wenn es nur als Referenz dasteht.
     *
     * <p>chefkoch liefert {@code {"@id": "…#primaryimage"}}. Wer das als URL nimmt, speichert
     * die Rezeptseite als Bildadresse und zeigt im Kochbuch nichts an.
     */
    private String resolveImage(JsonNode imageNode, List<JsonNode> graph) {
        if (imageNode == null || imageNode.isNull()) {
            return null;
        }
        if (imageNode.isArray()) {
            for (JsonNode entry : imageNode) {
                String resolved = resolveImage(entry, graph);
                if (resolved != null) return resolved;
            }
            return null;
        }
        if (imageNode.isTextual()) {
            return trimTo(imageNode.asText(), 500);
        }

        String direct = firstText(imageNode, "contentUrl", "url");
        if (direct != null) {
            return trimTo(direct, 500);
        }

        JsonNode reference = imageNode.get("@id");
        if (reference != null && reference.isTextual()) {
            String id = reference.asText();
            for (JsonNode node : graph) {
                JsonNode nodeId = node.get("@id");
                if (nodeId != null && id.equals(nodeId.asText())) {
                    String url = firstText(node, "contentUrl", "url");
                    if (url != null) {
                        return trimTo(url, 500);
                    }
                }
            }
        }
        return null;
    }

    // ── Felder ────────────────────────────────────────────────────────────────────────────

    private Integer readYield(JsonNode yieldNode) {
        if (yieldNode == null) return 4;
        String raw;
        if (yieldNode.isArray()) {
            raw = yieldNode.isEmpty() ? null : yieldNode.get(0).asText();
        } else {
            raw = yieldNode.asText();
        }
        if (raw == null) return 4;
        Matcher matcher = FIRST_INTEGER.matcher(raw);
        if (matcher.find()) {
            int value = Integer.parseInt(matcher.group());
            return value > 0 && value <= 100 ? value : 4;
        }
        return 4;
    }

    /** ISO-8601-Dauer ("PT25M", "PT1H0M") in Minuten. Unlesbares gibt 0. */
    private int minutes(String isoDuration) {
        if (isoDuration == null || isoDuration.isBlank()) return 0;
        try {
            return (int) Duration.parse(isoDuration.trim()).toMinutes();
        } catch (Exception e) {
            return 0;
        }
    }

    private List<RecipeIngredientDTO> readIngredients(JsonNode node) {
        List<RecipeIngredientDTO> result = new ArrayList<>();
        if (node == null) return result;

        for (JsonNode entry : node.isArray() ? node : objectMapper.createArrayNode().add(node)) {
            String line = entry.isTextual() ? entry.asText() : text(entry, "name");
            if (line == null || line.isBlank()) continue;

            ParsedIngredient parsed = ingredientParser.parse(line);
            result.add(new RecipeIngredientDTO(
                    null,
                    parsed.amount(),
                    parsed.unit(),
                    trimTo(parsed.name(), 200),
                    trimTo(parsed.note(), 200),
                    trimTo(parsed.rawText(), 300),
                    null));
        }
        return result;
    }

    /**
     * Schritte, eine Ebene flach geklopft.
     *
     * <p>chefkoch verpackt sie in {@code HowToSection}; andere Seiten liefern
     * {@code HowToStep} direkt oder einen einzigen Textblock.
     */
    private List<RecipeStepDTO> readInstructions(JsonNode node) {
        List<RecipeStepDTO> result = new ArrayList<>();
        if (node == null) return result;

        if (node.isTextual()) {
            for (String line : node.asText().split("\\R")) {
                addStep(result, line);
            }
            return result;
        }

        if (node.isArray()) {
            for (JsonNode entry : node) {
                if (entry.isTextual()) {
                    addStep(result, entry.asText());
                } else if (hasType(entry, "HowToSection")) {
                    JsonNode items = entry.get("itemListElement");
                    if (items != null && items.isArray()) {
                        for (JsonNode item : items) {
                            addStep(result, item.isTextual() ? item.asText() : text(item, "text"));
                        }
                    }
                } else {
                    addStep(result, text(entry, "text"));
                }
            }
        }
        return result;
    }

    private void addStep(List<RecipeStepDTO> steps, String text) {
        if (text == null) return;
        // Fuehrende "1." entfernen: die Nummer setzt die Oberflaeche, sonst steht dort
        // spaeter "1. 1. Mehl sieben".
        String cleaned = LEADING_NUMBER.matcher(text.trim()).replaceFirst("").trim();
        if (!cleaned.isEmpty()) {
            steps.add(new RecipeStepDTO(null, cleaned));
        }
    }

    private void readNutrition(JsonNode nutrition, RecipeDTO dto) {
        if (nutrition == null) return;
        dto.setCalories(firstNumber(text(nutrition, "calories")));
        Integer protein = firstNumber(text(nutrition, "proteinContent"));
        Integer carbs = firstNumber(text(nutrition, "carbohydrateContent"));
        Integer fat = firstNumber(text(nutrition, "fatContent"));
        if (protein != null) dto.setProtein(protein.doubleValue());
        if (carbs != null) dto.setCarbs(carbs.doubleValue());
        if (fat != null) dto.setFat(fat.doubleValue());
    }

    private Integer firstNumber(String text) {
        if (text == null) return null;
        Matcher matcher = FIRST_INTEGER.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group()) : null;
    }

    String mapCategory(String chefkochCategory) {
        if (chefkochCategory == null || chefkochCategory.isBlank()) {
            return "Sonstiges";
        }
        String mapped = loadCategoryMapping().get(chefkochCategory.trim().toLowerCase(Locale.GERMAN));
        return mapped != null ? mapped : "Sonstiges";
    }

    private synchronized Map<String, String> loadCategoryMapping() {
        if (categoryMapping != null) {
            return categoryMapping;
        }
        Map<String, String> mapping = new HashMap<>();
        try (InputStream in = new ClassPathResource("data/recipe-category-mapping.json").getInputStream()) {
            JsonNode node = objectMapper.readTree(in).get("mapping");
            if (node != null) {
                for (Map.Entry<String, JsonNode> field : node.properties()) {
                    mapping.put(field.getKey().toLowerCase(Locale.GERMAN), field.getValue().asText());
                }
            }
        } catch (IOException e) {
            // Eine fehlende Zuordnungsdatei darf keinen Import verhindern - dann landet eben
            // alles unter "Sonstiges", und das ist im Sheet mit einem Tipp korrigiert.
            log.warn("Kategorie-Zuordnung nicht lesbar, es wird alles zu 'Sonstiges'", e);
        }
        categoryMapping = mapping;
        return categoryMapping;
    }

    // ── Kleinkram ─────────────────────────────────────────────────────────────────────────

    private String cleanName(String name) {
        if (name == null) return null;
        return AUTHOR_SUFFIX.matcher(name.trim()).replaceFirst("").trim();
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String trimTo(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
