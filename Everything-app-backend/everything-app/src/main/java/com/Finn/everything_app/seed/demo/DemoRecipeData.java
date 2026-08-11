package com.Finn.everything_app.seed.demo;

import com.Finn.everything_app.model.MealPlan;
import com.Finn.everything_app.model.MealType;
import com.Finn.everything_app.model.Recipe;
import com.Finn.everything_app.model.RecipeCookLog;
import com.Finn.everything_app.model.RecipeIngredient;
import com.Finn.everything_app.model.RecipeStep;
import com.Finn.everything_app.model.ShoppingItem;
import com.Finn.everything_app.model.ShoppingItemSource;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.MealPlanRepository;
import com.Finn.everything_app.repository.RecipeCookLogRepository;
import com.Finn.everything_app.repository.RecipeRepository;
import com.Finn.everything_app.repository.ShoppingItemRepository;
import com.Finn.everything_app.service.recipe.IngredientAisleClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Demo-Bestand des Rezept-Space: vierzehn vollständige Rezepte mit Zutaten, Schritten und
 * Nährwerten, ein Wochenplan über drei Wochen, Koch-Historie und eine halb abgehakte
 * Einkaufsliste.
 *
 * <p>Die Kategorien stammen aus {@code resources/data/recipe-categories.json} und sind exakt
 * geschrieben wie dort — eine erfundene Kategorie taucht im Filter des Frontends nie auf und
 * das Rezept wäre unauffindbar.
 */
@Component
@RequiredArgsConstructor
public class DemoRecipeData {

    private final RecipeRepository recipeRepository;
    private final MealPlanRepository mealPlanRepository;
    private final ShoppingItemRepository shoppingItemRepository;
    private final RecipeCookLogRepository cookLogRepository;
    private final IngredientAisleClassifier aisleClassifier;

    /** Zutat in der Kurzform "Menge | Einheit | Name | Anmerkung". */
    private record Ing(String amount, String unit, String name, String note) {
        Ing(String amount, String unit, String name) {
            this(amount, unit, name, null);
        }
    }

    @Transactional
    public void seed(User user, LocalDate today) {
        List<Recipe> all = new ArrayList<>();

        Recipe bolognese = recipe(user, "Bolognese wie bei Oma", "Pasta & Reis", 20, 150, 4,
                "Vier Stunden wären besser, zweieinhalb reichen. Die Milch am Ende ist kein Tippfehler — "
                        + "sie macht die Sauce samtig und nimmt der Tomate die Säure.",
                "Mittel", 640, 34.0, 62.0, 24.0, "italienisch,meal-prep,klassiker",
                true, (short) 5, 11, today.minusDays(6),
                Set.of(MealType.MITTAGESSEN, MealType.ABENDESSEN),
                List.of(
                        new Ing("500", "g", "Rinderhackfleisch"),
                        new Ing("100", "g", "Pancetta", "gewürfelt"),
                        new Ing("1", "", "Zwiebel", "fein gewürfelt"),
                        new Ing("2", "", "Möhren", "fein gewürfelt"),
                        new Ing("2", "Stangen", "Sellerie", "fein gewürfelt"),
                        new Ing("2", "Zehen", "Knoblauch"),
                        new Ing("200", "ml", "Rotwein", "trocken"),
                        new Ing("800", "g", "Tomaten", "aus der Dose, geschält"),
                        new Ing("2", "EL", "Tomatenmark"),
                        new Ing("200", "ml", "Milch"),
                        new Ing("500", "g", "Tagliatelle"),
                        new Ing("60", "g", "Parmesan", "frisch gerieben"),
                        new Ing(null, null, "Salz, Pfeffer, Muskat")),
                List.of(
                        "Pancetta in einem schweren Topf ohne Öl auslassen, bis das Fett austritt.",
                        "Zwiebel, Möhre und Sellerie zugeben und bei mittlerer Hitze 10 Minuten weich schmoren — nicht bräunen.",
                        "Knoblauch und Tomatenmark einrühren, zwei Minuten mitrösten.",
                        "Hackfleisch zugeben, kräftig anbraten, bis keine Flüssigkeit mehr im Topf steht.",
                        "Mit Rotwein ablöschen und vollständig einkochen lassen.",
                        "Tomaten zugeben, zerdrücken, salzen und bei kleinster Hitze 2 Stunden offen köcheln lassen.",
                        "Milch zugeben und weitere 30 Minuten ziehen lassen. Mit Salz, Pfeffer und Muskat abschmecken.",
                        "Tagliatelle bissfest kochen, in der Sauce schwenken und mit Parmesan servieren."));
        all.add(bolognese);

        Recipe linsen = recipe(user, "Rote-Linsen-Dal", "Suppe & Eintopf", 10, 30, 3,
                "Der Standard für Abende, an denen der Kühlschrank leer ist — alles kommt aus dem Vorrat.",
                "Einfach", 420, 21.0, 58.0, 12.0, "vegan,vorratskammer,schnell",
                true, (short) 4, 23, today.minusDays(3),
                Set.of(MealType.MITTAGESSEN, MealType.ABENDESSEN),
                List.of(
                        new Ing("250", "g", "Rote Linsen"),
                        new Ing("1", "", "Zwiebel"),
                        new Ing("3", "cm", "Ingwer"),
                        new Ing("2", "Zehen", "Knoblauch"),
                        new Ing("2", "TL", "Currypulver"),
                        new Ing("1", "TL", "Kreuzkümmel", "gemahlen"),
                        new Ing("1", "Dose", "Kokosmilch"),
                        new Ing("400", "ml", "Gemüsebrühe"),
                        new Ing("1", "", "Limette"),
                        new Ing("1", "Bund", "Koriander")),
                List.of(
                        "Zwiebel, Ingwer und Knoblauch fein hacken und in Öl glasig dünsten.",
                        "Gewürze zugeben und 30 Sekunden mitrösten, bis es duftet.",
                        "Linsen, Kokosmilch und Brühe zugeben, aufkochen und 20 Minuten bei kleiner Hitze köcheln.",
                        "Mit Limettensaft und Salz abschmecken, mit Koriander bestreuen."));
        all.add(linsen);

        Recipe ofen = recipe(user, "Ofengemüse mit Feta und Honig", "Auflauf & Ofen", 15, 40, 2,
                "Ein Blech, ein Handgriff, keine Aufmerksamkeit nötig — läuft nebenbei, während man lernt.",
                "Einfach", 480, 18.0, 42.0, 26.0, "vegetarisch,ein-blech,lernabend",
                false, (short) 4, 7, today.minusDays(9),
                Set.of(MealType.ABENDESSEN),
                List.of(
                        new Ing("500", "g", "Kartoffeln", "geviertelt"),
                        new Ing("2", "", "Zucchini"),
                        new Ing("2", "", "Paprika"),
                        new Ing("1", "", "Rote Zwiebel"),
                        new Ing("200", "g", "Feta"),
                        new Ing("3", "EL", "Olivenöl"),
                        new Ing("1", "EL", "Honig"),
                        new Ing("2", "Zweige", "Rosmarin")),
                List.of(
                        "Backofen auf 200 °C Umluft vorheizen.",
                        "Gemüse in mundgerechte Stücke schneiden, mit Öl, Salz und Rosmarin auf einem Blech mischen.",
                        "25 Minuten backen, dann Feta darüber bröseln und mit Honig beträufeln.",
                        "Weitere 15 Minuten backen, bis der Feta Farbe genommen hat."));
        all.add(ofen);

        Recipe bowl = recipe(user, "Meal-Prep-Bowl mit Falafel", "Salat & Bowl", 25, 25, 4,
                "Sonntags einmal gemacht, hält vier Tage im Kühlschrank. Die Sauce getrennt aufbewahren.",
                "Mittel", 590, 24.0, 68.0, 22.0, "meal-prep,vegetarisch,mittag",
                true, (short) 5, 14, today.minusDays(1),
                Set.of(MealType.MITTAGESSEN),
                List.of(
                        new Ing("250", "g", "Kichererbsen", "getrocknet, über Nacht eingeweicht"),
                        new Ing("1", "Bund", "Petersilie"),
                        new Ing("300", "g", "Bulgur"),
                        new Ing("2", "", "Tomaten"),
                        new Ing("1", "", "Gurke"),
                        new Ing("200", "g", "Joghurt"),
                        new Ing("2", "EL", "Tahin"),
                        new Ing("1", "TL", "Backpulver"),
                        new Ing("2", "TL", "Kreuzkümmel")),
                List.of(
                        "Kichererbsen abgießen und mit Petersilie, Zwiebel und Gewürzen im Mixer zu grobem Teig verarbeiten.",
                        "Backpulver unterrühren, 30 Minuten kalt stellen, dann Bällchen formen.",
                        "Falafel in Öl goldbraun ausbacken oder bei 200 °C 20 Minuten backen.",
                        "Bulgur nach Packungsangabe quellen lassen, Gemüse würfeln.",
                        "Joghurt mit Tahin, Zitrone und Salz zur Sauce verrühren.",
                        "In vier Dosen schichten: Bulgur, Gemüse, Falafel. Sauce separat."));
        all.add(bowl);

        Recipe porridge = recipe(user, "Overnight Oats mit Beeren", "Frühstück", 5, 0, 1,
                "Abends 5 Minuten, morgens 0 — die einzige Art, wie ich vor einer 8:15-Vorlesung frühstücke.",
                "Einfach", 380, 14.0, 52.0, 11.0, "frühstück,vorbereiten,schnell",
                true, (short) 4, 31, today.minusDays(1),
                Set.of(MealType.FRUEHSTUECK),
                List.of(
                        new Ing("60", "g", "Haferflocken"),
                        new Ing("150", "ml", "Milch"),
                        new Ing("100", "g", "Joghurt"),
                        new Ing("1", "EL", "Chiasamen"),
                        new Ing("100", "g", "Beeren", "auch tiefgekühlt"),
                        new Ing("1", "TL", "Honig")),
                List.of(
                        "Haferflocken, Milch, Joghurt und Chiasamen in einem Glas verrühren.",
                        "Über Nacht in den Kühlschrank stellen.",
                        "Morgens Beeren und Honig daraufgeben."));
        all.add(porridge);

        Recipe curry = recipe(user, "Thai-Curry mit Hähnchen", "Hauptgericht", 20, 25, 3,
                "Wenn die Paste gut ist, wird das Curry gut. Beim Rest kann man sparen.",
                "Mittel", 610, 38.0, 44.0, 30.0, "asiatisch,schnell,scharf",
                true, (short) 5, 9, today.minusDays(12),
                Set.of(MealType.ABENDESSEN),
                List.of(
                        new Ing("500", "g", "Hähnchenbrust"),
                        new Ing("2", "EL", "Rote Currypaste"),
                        new Ing("400", "ml", "Kokosmilch"),
                        new Ing("200", "g", "Brokkoli"),
                        new Ing("1", "", "Paprika"),
                        new Ing("2", "EL", "Fischsauce"),
                        new Ing("1", "TL", "Rohrzucker"),
                        new Ing("250", "g", "Jasminreis"),
                        new Ing("1", "Bund", "Thai-Basilikum")),
                List.of(
                        "Reis aufsetzen.",
                        "Currypaste in wenig Öl anbraten, bis sie duftet.",
                        "Hähnchen in Streifen zugeben und anbraten.",
                        "Mit Kokosmilch aufgießen, Gemüse zugeben, 10 Minuten köcheln.",
                        "Mit Fischsauce, Zucker und Limette abschmecken, Basilikum unterheben."));
        all.add(curry);

        Recipe risotto = recipe(user, "Pilzrisotto", "Pasta & Reis", 15, 35, 2,
                "Nur mit heißer Brühe aufgießen — kalte Brühe stoppt die Stärke und das Risotto wird mehlig.",
                "Mittel", 520, 16.0, 70.0, 18.0, "italienisch,vegetarisch,wochenende",
                false, (short) 4, 5, today.minusDays(20),
                Set.of(MealType.ABENDESSEN),
                List.of(
                        new Ing("250", "g", "Risottoreis"),
                        new Ing("400", "g", "Champignons"),
                        new Ing("1", "", "Schalotte"),
                        new Ing("100", "ml", "Weißwein"),
                        new Ing("1", "l", "Gemüsebrühe", "heiß"),
                        new Ing("60", "g", "Parmesan"),
                        new Ing("40", "g", "Butter", "eiskalt")),
                List.of(
                        "Pilze in einer trockenen Pfanne scharf anbraten, salzen, beiseitestellen.",
                        "Schalotte in Butter glasig dünsten, Reis zugeben und glasig rühren.",
                        "Mit Weißwein ablöschen, einkochen lassen.",
                        "Kellenweise heiße Brühe zugeben und rühren, bis der Reis in 18 Minuten gar ist.",
                        "Vom Herd nehmen, kalte Butter und Parmesan einrühren, 2 Minuten ruhen lassen."));
        all.add(risotto);

        Recipe shakshuka = recipe(user, "Shakshuka", "Frühstück", 10, 25, 2,
                "Funktioniert zu jeder Tageszeit. Brot ist keine Beilage, sondern Werkzeug.",
                "Einfach", 410, 22.0, 26.0, 24.0, "vegetarisch,brunch,eintopf",
                true, (short) 5, 8, today.minusDays(15),
                Set.of(MealType.FRUEHSTUECK, MealType.ABENDESSEN),
                List.of(
                        new Ing("4", "", "Eier"),
                        new Ing("800", "g", "Tomaten", "aus der Dose"),
                        new Ing("1", "", "Zwiebel"),
                        new Ing("2", "", "Paprika"),
                        new Ing("1", "TL", "Kreuzkümmel"),
                        new Ing("1", "TL", "Paprikapulver", "geräuchert"),
                        new Ing("100", "g", "Feta"),
                        new Ing("1", "", "Fladenbrot")),
                List.of(
                        "Zwiebel und Paprika in einer Pfanne weich dünsten.",
                        "Gewürze zugeben, kurz mitrösten, Tomaten einrühren und 15 Minuten einkochen.",
                        "Vier Mulden formen, je ein Ei hineingeben, Deckel drauf, 6-8 Minuten stocken lassen.",
                        "Feta darüber bröseln, mit Fladenbrot servieren."));
        all.add(shakshuka);

        Recipe suppe = recipe(user, "Kürbissuppe mit Ingwer", "Suppe & Eintopf", 15, 30, 4,
                "Hokkaido muss nicht geschält werden — das spart die halbe Arbeit.",
                "Einfach", 290, 6.0, 32.0, 14.0, "vegan,herbst,einfrieren",
                false, (short) 4, 6, today.minusDays(30),
                Set.of(MealType.MITTAGESSEN, MealType.ABENDESSEN),
                List.of(
                        new Ing("1", "", "Hokkaido-Kürbis"),
                        new Ing("2", "", "Kartoffeln"),
                        new Ing("1", "", "Zwiebel"),
                        new Ing("3", "cm", "Ingwer"),
                        new Ing("800", "ml", "Gemüsebrühe"),
                        new Ing("100", "ml", "Kokosmilch"),
                        new Ing("2", "EL", "Kürbiskernöl")),
                List.of(
                        "Kürbis entkernen und mit Schale grob würfeln, Kartoffeln schälen und würfeln.",
                        "Zwiebel und Ingwer andünsten, Kürbis und Kartoffeln zugeben.",
                        "Mit Brühe aufgießen und 25 Minuten weich kochen.",
                        "Fein pürieren, Kokosmilch einrühren, mit Kürbiskernöl servieren."));
        all.add(suppe);

        Recipe pfanne = recipe(user, "Gnocchi-Pfanne mit Spinat", "Hauptgericht", 10, 15, 2,
                "Von Kühlschrank auf Teller in 25 Minuten. Das Rezept für Tage nach dem Training.",
                "Einfach", 560, 20.0, 66.0, 22.0, "schnell,feierabend,vegetarisch",
                true, (short) 4, 19, today.minusDays(4),
                Set.of(MealType.ABENDESSEN),
                List.of(
                        new Ing("500", "g", "Gnocchi", "aus dem Kühlregal"),
                        new Ing("200", "g", "Blattspinat", "tiefgekühlt"),
                        new Ing("200", "g", "Kirschtomaten"),
                        new Ing("150", "g", "Sahne"),
                        new Ing("2", "Zehen", "Knoblauch"),
                        new Ing("50", "g", "Parmesan"),
                        new Ing(null, null, "Muskat")),
                List.of(
                        "Gnocchi in der Pfanne in Öl goldbraun anbraten und herausnehmen.",
                        "Knoblauch und Tomaten in derselben Pfanne anbraten, bis die Tomaten aufplatzen.",
                        "Spinat und Sahne zugeben, einkochen lassen, mit Muskat und Salz würzen.",
                        "Gnocchi zurück in die Pfanne, Parmesan darüber."));
        all.add(pfanne);

        Recipe brot = recipe(user, "No-Knead-Brot", "Backen", 15, 45, 8,
                "18 Stunden Wartezeit, 15 Minuten Arbeit. Der Topf muss vorgeheizt werden, sonst geht es nicht auf.",
                "Mittel", 210, 7.0, 42.0, 1.0, "backen,wochenende,vorbereiten",
                true, (short) 5, 4, today.minusDays(24),
                Set.of(MealType.FRUEHSTUECK, MealType.SNACK),
                List.of(
                        new Ing("500", "g", "Weizenmehl", "Type 550"),
                        new Ing("400", "ml", "Wasser", "lauwarm"),
                        new Ing("2", "g", "Trockenhefe"),
                        new Ing("10", "g", "Salz")),
                List.of(
                        "Alle Zutaten kurz verrühren — kein Kneten. Abgedeckt 18 Stunden bei Raumtemperatur gehen lassen.",
                        "Teig auf bemehlter Fläche zweimal falten, 30 Minuten ruhen lassen.",
                        "Gusseisernen Topf mit Deckel bei 250 °C 30 Minuten vorheizen.",
                        "Teig einlegen, 30 Minuten mit Deckel backen, 15 Minuten ohne.",
                        "Vollständig auskühlen lassen, bevor angeschnitten wird."));
        all.add(brot);

        Recipe pancakes = recipe(user, "Protein-Pancakes", "Frühstück", 10, 10, 1,
                "Nach dem Training. Quark statt Mehl hält den Kohlenhydratanteil unten.",
                "Einfach", 450, 42.0, 34.0, 12.0, "sport,protein,frühstück",
                false, (short) 3, 13, today.minusDays(2),
                Set.of(MealType.FRUEHSTUECK, MealType.SNACK),
                List.of(
                        new Ing("2", "", "Eier"),
                        new Ing("150", "g", "Magerquark"),
                        new Ing("40", "g", "Haferflocken", "fein gemahlen"),
                        new Ing("1", "TL", "Backpulver"),
                        new Ing("1", "", "Banane")),
                List.of(
                        "Alle Zutaten zu einem glatten Teig mixen.",
                        "In einer beschichteten Pfanne bei mittlerer Hitze portionsweise ausbacken.",
                        "Wenden, sobald die Oberfläche Blasen wirft."));
        all.add(pancakes);

        Recipe tiramisu = recipe(user, "Tiramisu ohne rohes Ei", "Dessert", 25, 0, 6,
                "Muss mindestens sechs Stunden durchziehen. Über Nacht ist besser.",
                "Mittel", 430, 9.0, 38.0, 26.0, "dessert,gäste,vorbereiten",
                true, (short) 5, 3, today.minusDays(38),
                Set.of(MealType.SNACK),
                List.of(
                        new Ing("500", "g", "Mascarpone"),
                        new Ing("250", "g", "Sahne"),
                        new Ing("80", "g", "Zucker"),
                        new Ing("300", "ml", "Espresso", "abgekühlt"),
                        new Ing("200", "g", "Löffelbiskuits"),
                        new Ing("3", "EL", "Amaretto"),
                        new Ing("2", "EL", "Kakaopulver")),
                List.of(
                        "Sahne steif schlagen. Mascarpone mit Zucker glatt rühren, Sahne unterheben.",
                        "Espresso mit Amaretto mischen.",
                        "Biskuits kurz eintauchen und in eine Form schichten, Creme darauf. Wiederholen.",
                        "Mindestens 6 Stunden kalt stellen, vor dem Servieren mit Kakao bestäuben."));
        all.add(tiramisu);

        Recipe salat = recipe(user, "Krautsalat als Beilage", "Beilage & Sauce", 15, 0, 4,
                "Der Salat wird besser, je länger er steht — am zweiten Tag ist er am besten.",
                "Einfach", 140, 3.0, 12.0, 9.0, "beilage,vorbereiten,vegan",
                false, (short) 3, 2, today.minusDays(45),
                Set.of(MealType.MITTAGESSEN, MealType.ABENDESSEN),
                List.of(
                        new Ing("500", "g", "Weißkohl", "fein gehobelt"),
                        new Ing("1", "", "Möhre"),
                        new Ing("3", "EL", "Apfelessig"),
                        new Ing("2", "EL", "Olivenöl"),
                        new Ing("1", "TL", "Kümmel"),
                        new Ing("1", "TL", "Zucker")),
                List.of(
                        "Kohl hobeln, salzen und 10 Minuten mit den Händen kräftig durchkneten.",
                        "Möhre raspeln, mit Essig, Öl, Zucker und Kümmel unterheben.",
                        "Mindestens eine Stunde ziehen lassen."));
        all.add(salat);

        seedCookLogs(user, all, today);
        seedMealPlans(user, today, bolognese, linsen, ofen, bowl, porridge, curry,
                risotto, shakshuka, pfanne, pancakes, suppe);
        seedShoppingList(user);
    }

    // ------------------------------------------------------------------ Rezepte

    private Recipe recipe(User user, String name, String category, int prep, int cook, int servings,
                          String description, String difficulty, int calories, double protein,
                          double carbs, double fat, String tags, boolean favorite, Short rating,
                          int cookCount, LocalDate lastCooked, Set<MealType> mealTypes,
                          List<Ing> ingredients, List<String> steps) {
        Recipe recipe = new Recipe();
        recipe.setUser(user);
        recipe.setName(name);
        recipe.setCategory(category);
        recipe.setDescription(description);
        recipe.setPrepTimeMinutes(prep);
        recipe.setCookTimeMinutes(cook);
        recipe.setServings(servings);
        recipe.setDifficulty(difficulty);
        recipe.setCalories(calories);
        recipe.setProtein(protein);
        recipe.setCarbs(carbs);
        recipe.setFat(fat);
        recipe.setTags(tags);
        recipe.setIsFavorite(favorite);
        recipe.setRating(rating);
        recipe.setCookCount(cookCount);
        recipe.setLastCookedAt(lastCooked.atTime(19, 0));
        recipe.setSuitableFor(new LinkedHashSet<>(mealTypes));

        List<RecipeIngredient> ingredientList = new ArrayList<>();
        for (Ing ing : ingredients) {
            RecipeIngredient entry = new RecipeIngredient();
            entry.setAmount(ing.amount() != null ? new BigDecimal(ing.amount()) : null);
            entry.setUnit(ing.unit() != null && !ing.unit().isBlank() ? ing.unit() : null);
            entry.setName(ing.name());
            entry.setNote(ing.note());
            entry.setRawText(rawText(ing));
            ingredientList.add(entry);
        }
        recipe.replaceIngredients(ingredientList);

        List<RecipeStep> stepList = new ArrayList<>();
        for (String text : steps) {
            RecipeStep step = new RecipeStep();
            step.setText(text);
            stepList.add(step);
        }
        recipe.replaceSteps(stepList);

        return recipeRepository.save(recipe);
    }

    /** Die Originalzeile, wie sie beim Import entstanden wäre — die Detailansicht zeigt sie an. */
    private String rawText(Ing ing) {
        StringBuilder text = new StringBuilder();
        if (ing.amount() != null) text.append(ing.amount()).append(' ');
        if (ing.unit() != null && !ing.unit().isBlank()) text.append(ing.unit()).append(' ');
        text.append(ing.name());
        if (ing.note() != null) text.append(" (").append(ing.note()).append(')');
        return text.toString();
    }

    // ------------------------------------------------------------- Koch-Historie

    private void seedCookLogs(User user, List<Recipe> recipes, LocalDate today) {
        // Nur die letzten Einträge je Rezept, verteilt über acht Wochen - genug für die
        // "zuletzt gekocht"-Liste und die Häufigkeitsstatistik.
        int offset = 1;
        for (Recipe recipe : recipes) {
            int entries = Math.min(4, Math.max(1, recipe.getCookCount() / 4));
            for (int i = 0; i < entries; i++) {
                RecipeCookLog log = new RecipeCookLog();
                log.setRecipe(recipe);
                log.setUser(user);
                log.setCookedAt(today.minusDays((long) offset + i * 17L).atTime(18, 45));
                log.setServings(recipe.getServings());
                log.setRating(recipe.getRating());
                cookLogRepository.save(log);
            }
            offset += 2;
        }
    }

    // --------------------------------------------------------------- Wochenplan

    /**
     * Drei Wochen: die vergangene abgehakt, die laufende halb offen, die kommende geplant.
     * So zeigt der Wochenplan in jeder Ansicht etwas anderes.
     */
    private void seedMealPlans(User user, LocalDate today, Recipe bolognese, Recipe linsen,
                               Recipe ofen, Recipe bowl, Recipe porridge, Recipe curry,
                               Recipe risotto, Recipe shakshuka, Recipe pfanne,
                               Recipe pancakes, Recipe suppe) {
        LocalDate monday = DemoDates.monday(today);
        Recipe[] dinners = {bolognese, pfanne, curry, ofen, linsen, risotto, shakshuka};
        Recipe[] lunches = {bowl, bowl, bowl, suppe, bowl, bolognese, curry};
        Recipe[] breakfasts = {porridge, porridge, pancakes, porridge, shakshuka, pancakes, porridge};

        // Nur die laufende und die kommende Woche: drei Wochen a 21 Mahlzeiten waren mehr
        // Wochenplan, als sich im Rezept-Space ueberblicken laesst.
        for (int weekOffset = 0; weekOffset <= 1; weekOffset++) {
            LocalDate weekStart = monday.plusWeeks(weekOffset);
            for (int day = 0; day < 7; day++) {
                LocalDate date = weekStart.plusDays(day);

                plan(user, breakfasts[day], date, MealType.FRUEHSTUECK, 1, today, null);
                plan(user, lunches[day], date, MealType.MITTAGESSEN, 1, today,
                        day == 0 ? "Reste vom Sonntag mitnehmen" : null);
                plan(user, dinners[day], date, MealType.ABENDESSEN,
                        day >= 5 ? 2 : 1, today,
                        day == 5 ? "Lena kommt vorbei" : null);
            }
        }
    }

    private void plan(User user, Recipe recipe, LocalDate date, MealType type,
                      int servings, LocalDate today, String notes) {
        MealPlan plan = new MealPlan();
        plan.setUser(user);
        plan.setRecipe(recipe);
        plan.setDate(date);
        plan.setMealType(type);
        plan.setPlannedServings(servings);
        plan.setNotes(notes);

        boolean past = date.isBefore(today);
        plan.setIsCompleted(past);
        if (past) {
            plan.setCompletedAt(date.atTime(type == MealType.FRUEHSTUECK ? 8 : 19, 30));
        }
        mealPlanRepository.save(plan);
    }

    // ------------------------------------------------------------ Einkaufsliste

    private void seedShoppingList(User user) {
        // Die Regal-Kategorie kommt vom selben Klassifikator, den auch der Import benutzt -
        // von Hand gesetzte Kategorien wichen sonst irgendwann von den generierten ab.
        item(user, "Rinderhackfleisch", "500", "g", ShoppingItemSource.MEAL_PLAN, false, 0);
        item(user, "Pancetta", "100", "g", ShoppingItemSource.MEAL_PLAN, false, 1);
        item(user, "Tomaten", "2", "Dosen", ShoppingItemSource.MEAL_PLAN, false, 2);
        item(user, "Tagliatelle", "500", "g", ShoppingItemSource.MEAL_PLAN, true, 3);
        item(user, "Parmesan", "60", "g", ShoppingItemSource.MEAL_PLAN, true, 4);
        item(user, "Kokosmilch", "2", "Dosen", ShoppingItemSource.MEAL_PLAN, false, 5);
        item(user, "Rote Linsen", "250", "g", ShoppingItemSource.MEAL_PLAN, true, 6);
        item(user, "Gnocchi", "500", "g", ShoppingItemSource.MEAL_PLAN, false, 7);
        item(user, "Blattspinat", "200", "g", ShoppingItemSource.MEAL_PLAN, false, 8);
        item(user, "Kirschtomaten", "200", "g", ShoppingItemSource.MEAL_PLAN, false, 9);
        item(user, "Haferflocken", "1", "kg", ShoppingItemSource.MANUAL, true, 10);
        item(user, "Magerquark", "500", "g", ShoppingItemSource.MANUAL, false, 11);
        item(user, "Eier", "10", "Stück", ShoppingItemSource.MANUAL, false, 12);
        item(user, "Milch", "2", "l", ShoppingItemSource.MANUAL, true, 13);
        item(user, "Kaffeebohnen", "1", "kg", ShoppingItemSource.MANUAL, false, 14);
        item(user, "Spülmaschinentabs", "1", "Packung", ShoppingItemSource.MANUAL, false, 15);
        item(user, "Zahnpasta", null, null, ShoppingItemSource.MANUAL, false, 16);
        item(user, "Bananen", "6", "Stück", ShoppingItemSource.MANUAL, true, 17);
        item(user, "Olivenöl", "1", "Flasche", ShoppingItemSource.MANUAL, false, 18);
        item(user, "Klopapier", "1", "Packung", ShoppingItemSource.MANUAL, false, 19);
    }

    private void item(User user, String name, String amount, String unit,
                      ShoppingItemSource source, boolean checked, int order) {
        ShoppingItem item = new ShoppingItem();
        item.setUser(user);
        item.setName(name);
        item.setAmount(amount != null ? new BigDecimal(amount) : null);
        item.setUnit(unit);
        item.setCategory(aisleClassifier.classify(name));
        item.setSource(source);
        item.setIsChecked(checked);
        item.setSortOrder(order);
        if (checked) {
            item.setCheckedAt(java.time.LocalDateTime.now().minusHours(order + 1L));
        }
        shoppingItemRepository.save(item);
    }
}
