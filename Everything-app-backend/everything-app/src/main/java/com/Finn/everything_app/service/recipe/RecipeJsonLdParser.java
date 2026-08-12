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
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uebersetzt einen {@code schema.org/Recipe}-Knoten in ein {@link RecipeDTO}.
 *
 * <p>Ohne Netzzugriff und ohne Datenbank - genau deshalb ist er vom {@link RecipeUrlImporter}
 * getrennt: das Interessante an einem Importer ist nicht das Herunterladen, sondern das
 * Auseinanderfummeln, und das soll gegen eine gespeicherte Datei pruefbar sein.
 *
 * <p><b>Was allgemein gilt.</b> Die folgenden Faelle sind nicht die Marotte einer Seite, sondern
 * der Zustand von {@code schema.org/Recipe} im Netz:
 *
 * <ul>
 *   <li>Das Rezept ist ein Knoten in {@code @graph}, nicht die Wurzel - und nicht zuverlaessig
 *       der erste. Gesucht wird ueber {@code @type}, das seinerseits ein String oder eine Liste
 *       sein kann.</li>
 *   <li>{@code image} ist eine Referenz {@code {"@id": "…#primaryimage"}} und muss gegen den
 *       {@code ImageObject}-Knoten im selben Graphen aufgeloest werden.</li>
 *   <li>{@code recipeInstructions} ist mal ein Textblock, mal eine Liste von {@code HowToStep},
 *       mal {@code HowToSection}/{@code ItemList} mit den Schritten eine Ebene tiefer - und der
 *       Schritttext steckt mal in {@code text}, mal nur in {@code name}.</li>
 *   <li>In diesem Schritttext steht bei den verbreiteten WordPress-Erweiterungen HTML.</li>
 *   <li>{@code recipeYield} und {@code recipeCategory} sind mal Text, mal Array.</li>
 *   <li>{@code cookTime} fehlt bei vielen Rezepten - dann bleibt nur {@code totalTime} minus
 *       {@code prepTime}. Und die Dauer ist mal ISO-8601, mal {@code "30 mins"}.</li>
 * </ul>
 *
 * <p><b>Was nur chefkoch macht</b> und deshalb an {@code sourceName} haengt: das angehaengte
 * {@code " von <Nutzername>"} im Titel, und {@code description} als Suchmaschinentext ("Über 2834
 * Bewertungen und für vorzüglich befunden"), waehrend der echte Beschreibungstext in
 * {@code abstract} steht. Beides allgemein anzuwenden waere schaedlich - {@code \s+von\s+\S+$}
 * macht aus "Kartoffelsalat von Oma" ein "Kartoffelsalat".
 *
 * <p>Nicht gelesen wird {@code aggregateRating}: das ist die Meinung von tausend Fremden, und
 * der Space zeigt die eigene.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecipeJsonLdParser {

    private final ObjectMapper objectMapper;
    private final IngredientParser ingredientParser;

    private Map<String, String> categoryMapping;

    /** " von zwergenmuomi" am Ende des Titels - nur bei chefkoch. */
    private static final Pattern AUTHOR_SUFFIX = Pattern.compile("\\s+von\\s+\\S+$");

    private static final String CHEFKOCH = "chefkoch.de";

    /** Kommentar- und CDATA-Verpackung um den Inhalt eines Script-Blocks. */
    private static final Pattern SCRIPT_WRAPPER = Pattern.compile(
            "^\\s*(?:<!--|//\\s*<!\\[CDATA\\[|/\\*\\s*<!\\[CDATA\\[\\s*\\*/)|"
                    + "(?://\\s*]]>|/\\*\\s*]]>\\s*\\*/|-->)\\s*$",
            Pattern.MULTILINE);

    // ── Einstieg ──────────────────────────────────────────────────────────────────────────

    /**
     * Sucht in einer Seite den ersten {@code ld+json}-Block, der wirklich ein Rezept enthaelt.
     *
     * <p>Leer statt Fehler: fuer die Kette im {@link RecipeUrlImporter} muss ein Fehlschlag hier
     * weiterfallen koennen - auf Microdata und zuletzt auf den Seitentext. Frueher gab diese
     * Stelle den ersten <em>lesbaren</em> Block zurueck, egal was darin stand; auf einer Seite,
     * deren einziges JSON-LD ein {@code Organization}-Block ist, haette das die ganze Kette
     * kurzgeschlossen.
     */
    public Optional<RecipeImportPreviewDTO> tryParse(Document document, String sourceUrl,
                                                     String sourceName) {
        for (JsonNode block : extractBlocks(document)) {
            List<JsonNode> graph = flattenGraph(block);
            Optional<JsonNode> recipe = findRecipe(graph);
            if (recipe.isPresent()) {
                return Optional.of(build(recipe.get(), graph, sourceUrl, sourceName));
            }
        }
        return Optional.empty();
    }

    /**
     * Alle lesbaren {@code ld+json}-Bloecke einer Seite.
     *
     * <p>Eine Seite traegt haeufig mehrere (Organisation, Brotkrumen, Rezept). Ein unlesbarer
     * Block ist kein Grund aufzuhoeren - der naechste kann der richtige sein.
     */
    List<JsonNode> extractBlocks(Document document) {
        List<JsonNode> blocks = new ArrayList<>();
        for (Element script : document.select("script[type=application/ld+json]")) {
            String json = SCRIPT_WRAPPER.matcher(script.data().trim()).replaceAll("").trim();
            if (json.isEmpty()) {
                continue;
            }
            try {
                blocks.add(objectMapper.readTree(json));
            } catch (Exception e) {
                log.debug("ld+json-Block nicht lesbar, weiter mit dem naechsten", e);
            }
        }
        return blocks;
    }

    /**
     * Der Rezeptknoten - ueber {@code @type} und nicht ueber eine Textsuche.
     *
     * <p>Eine Suche nach dem Wort "Recipe" im Rohtext trifft auch {@code "name": "Recipe of the
     * day"} in einer Brotkrumenliste.
     */
    Optional<JsonNode> findRecipe(List<JsonNode> graph) {
        return graph.stream().filter(node -> hasType(node, "Recipe")).findFirst();
    }

    /** Der alte Einstieg: wirft, wenn nichts zu finden ist. */
    public RecipeImportPreviewDTO parse(JsonNode root, String sourceUrl, String sourceName) {
        List<JsonNode> graph = flattenGraph(root);
        JsonNode recipeNode = findRecipe(graph)
                .orElseThrow(() -> new BadRequestException(
                        "Auf dieser Seite steckt kein Rezept. Ist das die Rezeptseite oder eine Übersicht?"));
        return build(recipeNode, graph, sourceUrl, sourceName);
    }

    // ── Zusammenbauen ─────────────────────────────────────────────────────────────────────

    private RecipeImportPreviewDTO build(JsonNode recipeNode, List<JsonNode> graph,
                                         String sourceUrl, String sourceName) {
        boolean chefkoch = CHEFKOCH.equalsIgnoreCase(sourceName);

        List<String> warnings = new ArrayList<>();
        RecipeDTO dto = new RecipeDTO();

        dto.setName(cleanName(text(recipeNode, "name"), chefkoch));
        if (dto.getName() == null || dto.getName().isBlank()) {
            dto.setName("Rezept von " + sourceName);
            warnings.add("Kein Titel gefunden.");
        }

        // Bei chefkoch ist description Suchmaschinentext und abstract der echte Text. Sonst
        // andersherum: description ist das, was jemand geschrieben hat.
        String description = chefkoch
                ? firstNonBlank(text(recipeNode, "abstract"), text(recipeNode, "description"))
                : firstNonBlank(text(recipeNode, "description"), text(recipeNode, "abstract"));
        dto.setDescription(RecipeFieldReader.trimTo(RecipeFieldReader.stripHtml(description), 2000));

        dto.setImageUrl(resolveImage(recipeNode.get("image"), graph));
        dto.setServings(RecipeFieldReader.readYield(firstText(recipeNode.get("recipeYield"))));

        int prep = RecipeFieldReader.minutes(text(recipeNode, "prepTime"));
        int cook = RecipeFieldReader.minutes(text(recipeNode, "cookTime"));
        int total = RecipeFieldReader.minutes(text(recipeNode, "totalTime"));
        if (cook == 0 && total > prep) {
            // Sehr haeufig: nur prepTime und totalTime sind gesetzt.
            cook = total - prep;
        }
        dto.setPrepTimeMinutes(prep);
        dto.setCookTimeMinutes(cook);
        if (prep == 0 && cook == 0) {
            warnings.add("Keine Zeitangabe gefunden - bitte selbst eintragen.");
        }

        String rawCategory = firstText(recipeNode.get("recipeCategory"));
        String category = mapCategory(rawCategory);
        dto.setCategory(category);
        if ("Sonstiges".equals(category) && rawCategory != null && !rawCategory.isBlank()) {
            warnings.add("Kategorie nicht erkannt - bitte auswählen.");
        }

        // Die Schwierigkeit steht selten im JSON-LD. Wo sie steht, wird sie gelesen; sonst eine
        // ehrliche Vorgabe, die im Sheet mit einem Tipp geaendert ist. "Mittel" und nicht
        // "Normal": kanonisch sind Einfach / Mittel / Aufwendig, und ein viertes Wort fuer
        // dieselbe Stufe teilt den Filter in zwei Haelften.
        String difficulty = RecipeFieldReader.mapDifficulty(
                firstNonBlank(text(recipeNode, "difficulty"), text(recipeNode, "recipeDifficulty")));
        dto.setDifficulty(difficulty != null ? difficulty : "Mittel");

        JsonNode ingredientNode = recipeNode.has("recipeIngredient")
                ? recipeNode.get("recipeIngredient")
                : recipeNode.get("ingredients");   // aeltere Seiten benutzen noch den alten Namen
        dto.setIngredients(readIngredients(ingredientNode));
        if (dto.getIngredients().isEmpty()) {
            warnings.add("Keine Zutaten gefunden - bitte selbst ergänzen.");
        }

        dto.setSteps(readInstructions(recipeNode.get("recipeInstructions")));
        if (dto.getSteps().isEmpty()) {
            warnings.add("Keine Zubereitung gefunden - bitte selbst ergänzen.");
        }

        readNutrition(recipeNode.get("nutrition"), dto);

        dto.setSourceUrl(RecipeFieldReader.trimTo(sourceUrl, 500));
        dto.setSourceName(RecipeFieldReader.trimTo(sourceName, 100));

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
            return RecipeFieldReader.trimTo(imageNode.asText(), 500);
        }

        String direct = firstText(imageNode, "contentUrl", "url");
        if (direct != null) {
            return RecipeFieldReader.trimTo(direct, 500);
        }

        JsonNode reference = imageNode.get("@id");
        if (reference != null && reference.isTextual()) {
            String id = reference.asText();
            for (JsonNode node : graph) {
                JsonNode nodeId = node.get("@id");
                if (nodeId != null && id.equals(nodeId.asText())) {
                    String url = firstText(node, "contentUrl", "url");
                    if (url != null) {
                        return RecipeFieldReader.trimTo(url, 500);
                    }
                }
            }
        }
        return null;
    }

    // ── Felder ────────────────────────────────────────────────────────────────────────────

    private List<RecipeIngredientDTO> readIngredients(JsonNode node) {
        List<RecipeIngredientDTO> result = new ArrayList<>();
        if (node == null) return result;

        for (JsonNode entry : node.isArray() ? node : objectMapper.createArrayNode().add(node)) {
            String raw = entry.isTextual() ? entry.asText() : text(entry, "name");
            String line = RecipeFieldReader.stripHtml(raw);
            if (line == null || line.isBlank()) continue;

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
     * Schritte, eine Ebene flach geklopft.
     *
     * <p>chefkoch verpackt sie in {@code HowToSection}; andere Seiten liefern {@code HowToStep}
     * direkt, einen einzigen Textblock, oder eine {@code ItemList} ohne sprechenden Typ. Deshalb
     * wird nicht auf einen Typnamen geprueft, sondern schlicht: hat der Eintrag ein
     * {@code itemListElement}, geht es eine Ebene tiefer.
     */
    private List<RecipeStepDTO> readInstructions(JsonNode node) {
        List<RecipeStepDTO> result = new ArrayList<>();
        collectSteps(node, result, 0);
        return result;
    }

    private void collectSteps(JsonNode node, List<RecipeStepDTO> result, int depth) {
        if (node == null || depth > 3) {
            return;
        }
        if (node.isTextual()) {
            for (String line : node.asText().split("\\R")) {
                addStep(result, line);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode entry : node) {
                collectSteps(entry, result, depth + 1);
            }
            return;
        }
        JsonNode nested = node.get("itemListElement");
        if (nested != null && !nested.isNull()) {
            collectSteps(nested, result, depth + 1);
            return;
        }
        // text ist das Uebliche; HowToStep-Knoten nur mit name kommen aber oft genug vor, dass
        // ein Rezept sonst ohne Zubereitung dasteht.
        addStep(result, firstText(node, "text", "name", "description"));
    }

    private void addStep(List<RecipeStepDTO> steps, String text) {
        if (text == null) return;
        String cleaned = RecipeFieldReader.stripLeadingNumber(
                RecipeFieldReader.stripHtml(text));
        if (cleaned != null && !cleaned.isEmpty()) {
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
        Matcher matcher = Pattern.compile("\\d+").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group()) : null;
    }

    String mapCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return "Sonstiges";
        }
        String mapped = loadCategoryMapping().get(rawCategory.trim().toLowerCase(Locale.GERMAN));
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

    private String cleanName(String name, boolean chefkoch) {
        String cleaned = RecipeFieldReader.stripHtml(name);
        if (cleaned == null) return null;
        if (chefkoch) {
            cleaned = AUTHOR_SUFFIX.matcher(cleaned.trim()).replaceFirst("");
        }
        return RecipeFieldReader.trimTo(cleaned, 200);
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (value.isArray()) return firstText(value);
        return value.isValueNode() ? value.asText() : null;
    }

    /** Erster brauchbarer Text aus einem Wert, der auch ein Array sein darf. */
    private String firstText(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isArray()) {
            for (JsonNode entry : node) {
                String value = firstText(entry);
                if (value != null) return value;
            }
            return null;
        }
        if (node.isValueNode()) {
            String value = node.asText();
            return value == null || value.isBlank() ? null : value;
        }
        return text(node, "name");
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
