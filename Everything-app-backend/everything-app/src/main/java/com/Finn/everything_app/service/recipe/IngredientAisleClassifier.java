package com.Finn.everything_app.service.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ordnet eine Zutat einem Ladenregal zu.
 *
 * <p>Der Sinn ist die Reihenfolge im Laden: wer nach Regalen sortiert einkauft, laeuft einmal
 * durch statt fuenfmal hin und her.
 *
 * <p>Der Vorgaenger war eine fuenfzweigige if-Kette in {@code MealPlanService}, die
 * "fleisch", "milch", "tomate" und "apfel" kannte. Bei zwanzig Positionen landeten siebzehn
 * unter "Sonstiges".
 *
 * <p>Es gewinnt das <em>laengste</em> passende Stichwort, nicht das erste. Sonst kommt
 * "Kokosmilch" wegen "milch" ins Kuehlregal - und wer davor steht, findet sie dort nicht.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IngredientAisleClassifier {

    public static final String FALLBACK = "Sonstiges";

    private final ObjectMapper objectMapper;

    /** Stichwort (klein) auf Regal, nach Stichwortlaenge absteigend. */
    private List<Map.Entry<String, String>> keywords;

    public String classify(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return FALLBACK;
        }
        String haystack = ingredientName.toLowerCase(Locale.GERMAN);
        for (Map.Entry<String, String> entry : load()) {
            if (haystack.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return FALLBACK;
    }

    /** Regale in Anzeigereihenfolge, "Sonstiges" immer zuletzt. */
    public List<String> aisleOrder() {
        List<String> order = new ArrayList<>();
        for (Map.Entry<String, String> entry : load()) {
            if (!order.contains(entry.getValue())) {
                order.add(entry.getValue());
            }
        }
        order.add(FALLBACK);
        return order;
    }

    private synchronized List<Map.Entry<String, String>> load() {
        if (keywords != null) {
            return keywords;
        }
        Map<String, String> collected = new LinkedHashMap<>();
        try (InputStream in = new ClassPathResource("data/ingredient-aisles.json").getInputStream()) {
            JsonNode aisles = objectMapper.readTree(in).get("aisles");
            if (aisles != null) {
                for (Map.Entry<String, JsonNode> aisle : aisles.properties()) {
                    for (JsonNode keyword : aisle.getValue()) {
                        collected.put(keyword.asText().toLowerCase(Locale.GERMAN), aisle.getKey());
                    }
                }
            }
        } catch (IOException e) {
            // Ohne Regale ist die Liste unsortiert, aber vollstaendig - das ist kein Grund,
            // den Einkauf scheitern zu lassen.
            log.warn("Regal-Zuordnung nicht lesbar, alles landet unter '{}'", FALLBACK, e);
        }

        List<Map.Entry<String, String>> sorted = new ArrayList<>(collected.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        keywords = sorted;
        return keywords;
    }
}
