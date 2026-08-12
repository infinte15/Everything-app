package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.RecipeCookLogDTO;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MealPlanService {

    private final MealPlanRepository mealPlanRepository;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final TaskService taskService;

    @Transactional
    public MealPlan createMealPlan(Long userId, MealPlan mealPlan, Long recipeId,
                                   boolean scheduleCooking) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        Recipe recipe = recipeRepository.findByIdAndUserId(recipeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Rezept nicht gefunden"));

        mealPlan.setUser(user);
        mealPlan.setRecipe(recipe);

        if (mealPlan.getPlannedServings() == null) {
            mealPlan.setPlannedServings(recipe.getServings());
        }

        if (scheduleCooking) {
            mealPlan.setCookingTaskId(createCookingTask(userId, mealPlan, recipe));
        }

        return mealPlanRepository.save(mealPlan);
    }

    /**
     * Legt die Kochzeit als Aufgabe an, damit der Scheduler sie in den Kalender bringt.
     *
     * <p>Nur auf ausdruecklichen Wunsch. Vorher tat das der Flutter-Provider bei jedem
     * Einplanen still und heimlich, mit einer erfundenen Faelligkeit um 13:00 bzw. 19:00 -
     * und liess die Aufgabe stehen, wenn man die Mahlzeit wieder aus dem Plan nahm. Jetzt
     * entsteht sie in derselben Transaktion wie die Mahlzeit und verschwindet mit ihr.
     */
    private Long createCookingTask(Long userId, MealPlan mealPlan, Recipe recipe) {
        int duration = recipe.getTotalTimeMinutes();
        if (duration <= 0) {
            return null;
        }

        Task task = new Task();
        task.setTitle("Kochen: " + recipe.getName());
        task.setDescription(mealPlan.getMealType().getDisplayName() + " am "
                + mealPlan.getDate());
        task.setEstimatedDurationMinutes(duration);
        task.setDeadline(mealPlan.getDate().atTime(deadlineHour(mealPlan.getMealType()), 0));
        task.setCategory("RECIPES");
        task.setSpaceType(SpaceType.RECIPES);
        task.setPriority(3);
        // Kochen laesst sich nicht in drei Haeppchen ueber den Tag verteilen.
        task.setSplittable(false);

        return taskService.createTask(userId, task).getId();
    }

    private int deadlineHour(MealType mealType) {
        return switch (mealType) {
            case FRUEHSTUECK -> 9;
            case MITTAGESSEN -> 13;
            case ABENDESSEN -> 19;
            case SNACK -> 16;
        };
    }

    /**
     * Lesend transaktional und mit angefasstem Rezept.
     *
     * <p>{@code MealPlanMapper} holt Name und Bild ueber {@code mealPlan.getRecipe()}, und die
     * Beziehung ist LAZY. Bei {@code spring.jpa.open-in-view=false} endet die Sitzung mit dem
     * Repository-Aufruf - ohne diese Klammer wird der Wochenplan zu einem 500.
     */
    @Transactional(readOnly = true)
    public List<MealPlan> getMealPlanForPeriod(Long userId, LocalDate start, LocalDate end) {
        return withRecipe(mealPlanRepository.findByUserIdAndDateBetween(userId, start, end));
    }

    @Transactional(readOnly = true)
    public List<MealPlan> getMealPlanForDate(Long userId, LocalDate date) {
        return withRecipe(mealPlanRepository.findByUserIdAndDate(userId, date));
    }

    @Transactional(readOnly = true)
    public MealPlan getMealPlan(Long userId, Long id) {
        MealPlan mealPlan = mealPlanRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Essensplan nicht gefunden"));
        touchRecipe(mealPlan);
        return mealPlan;
    }

    private static List<MealPlan> withRecipe(List<MealPlan> plans) {
        plans.forEach(MealPlanService::touchRecipe);
        return plans;
    }

    private static void touchRecipe(MealPlan plan) {
        if (plan.getRecipe() != null) {
            plan.getRecipe().getName();
        }
    }

    @Transactional
    public MealPlan updateMealPlan(Long userId, Long id, MealPlan updatedMealPlan) {
        MealPlan mealPlan = getMealPlan(userId, id);

        if (updatedMealPlan.getDate() != null) {
            mealPlan.setDate(updatedMealPlan.getDate());
        }
        if (updatedMealPlan.getMealType() != null) {
            mealPlan.setMealType(updatedMealPlan.getMealType());
        }
        if (updatedMealPlan.getPlannedServings() != null) {
            mealPlan.setPlannedServings(updatedMealPlan.getPlannedServings());
        }
        if (updatedMealPlan.getNotes() != null) {
            mealPlan.setNotes(updatedMealPlan.getNotes());
        }

        return mealPlanRepository.save(mealPlan);
    }

    /**
     * Hakt eine geplante Mahlzeit ab - und schreibt damit einen Eintrag ins Kochprotokoll.
     *
     * <p>Eine geplante Mahlzeit abzuhaken <em>ist</em> gekocht zu haben. Ohne diese Kopplung
     * muesste man dasselbe zweimal melden, und die Reihe "lange nicht gekocht" waere falsch,
     * sobald man ueber den Wochenplan statt ueber die Rezeptseite arbeitet.
     *
     * <p>{@code details} darf {@code null} sein - dann bleibt es beim blossen Abhaken mit den
     * geplanten Portionen, wie es immer war. Sind Angaben dabei, landen sie im Protokoll: der
     * Haken im Wochenplan kann damit dasselbe wie der Knopf auf der Rezeptseite, statt eine
     * aermere Kopie davon zu sein.
     *
     * <p>Was hier <b>nicht</b> passiert: {@code plannedServings} zu ueberschreiben. Der Aufbau
     * der Einkaufsliste liest auch abgehakte Mahlzeiten - eine nachtraeglich geaenderte
     * Planmenge veraenderte also spaetere Einkaeufe. "Ich habe fuer sechs gekocht" gehoert ins
     * Protokoll, nicht in die Planzeile.
     */
    @Transactional
    public MealPlan completeMealPlan(Long userId, Long id, RecipeCookLogDTO details) {
        MealPlan mealPlan = getMealPlan(userId, id);
        if (Boolean.TRUE.equals(mealPlan.getIsCompleted())) {
            return mealPlan;
        }

        mealPlan.setIsCompleted(true);
        mealPlan.setCompletedAt(LocalDateTime.now());

        RecipeCookLogDTO entry = new RecipeCookLogDTO();
        entry.setServings(details != null && details.getServings() != null
                ? details.getServings()
                : mealPlan.getPlannedServings());
        if (details != null) {
            entry.setRating(details.getRating());
            entry.setNote(details.getNote());
        }
        recipeService.logCooked(userId, mealPlan.getRecipe().getId(), entry);

        return mealPlanRepository.save(mealPlan);
    }

    @Transactional
    public void deleteMealPlan(Long userId, Long id) {
        MealPlan mealPlan = getMealPlan(userId, id);
        Long cookingTaskId = mealPlan.getCookingTaskId();

        mealPlanRepository.delete(mealPlan);

        if (cookingTaskId != null) {
            // Ueber den TaskService, damit dessen Aufraeumarbeit mitlaeuft: der Scheduler hat
            // aus der Aufgabe einen Kalendereintrag gemacht, und der haengt an einem
            // Fremdschluessel.
            taskService.deleteTask(cookingTaskId);
        }
    }

    // AUTOMATISCHE WOCHENPLANUNG

    /**
     * Fuellt sieben Tage mit Fruehstueck, Mittag- und Abendessen.
     *
     * <p>Die Auswahl laeuft ueber {@link Recipe#getSuitableFor()}, nicht mehr ueber die
     * Kategorie. Vorher stand dort {@code recipe.getCategory().equals("MITTAGESSEN")} - die
     * Kategorie beschreibt aber, *was* ein Gericht ist, nicht *wann* man es isst. Mit einem
     * echten Kategorienkatalog ("Pasta & Reis", "Auflauf & Ofen") hat dieser Vergleich nie
     * wieder getroffen, und die Planung lieferte stumm eine leere Liste.
     *
     * <p>Innerhalb eines Durchlaufs kommt kein Rezept zweimal vor, solange es Alternativen
     * gibt; die Reihenfolge kommt aus der Abfrage, also lange nicht Gekochtes zuerst. Ein Slot
     * ohne passendes Rezept wird uebersprungen - frueher brach die ganze Generierung mit
     * {@code RuntimeException("Keine Rezepte verfügbar")} ab, auch wenn nur das Fruehstueck fehlte.
     *
     * <p><b>Belegte Plaetze bleiben, wie sie sind.</b> Die Oberflaeche hat dafuer genau einen
     * Knopf ("Woche füllen"); ohne diese Pruefung legte ein zweiter Druck einundzwanzig weitere
     * Eintraege an, und der Wochenplan zeigte jede Mahlzeit doppelt. Aufgefuellt wird also nur,
     * was leer ist - deshalb heisst der Knopf auch nicht "Woche neu planen".
     */
    @Transactional
    public List<MealPlan> generateWeeklyPlan(Long userId, LocalDate startDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));

        MealType[] mealTypes = {MealType.FRUEHSTUECK, MealType.MITTAGESSEN, MealType.ABENDESSEN};

        Map<MealType, List<Recipe>> candidates = new EnumMap<>(MealType.class);
        for (MealType mealType : mealTypes) {
            candidates.put(mealType, recipeRepository.findSuitableFor(userId, mealType));
        }

        Set<String> occupied = mealPlanRepository
                .findByUserIdAndDateBetween(userId, startDate, startDate.plusDays(6)).stream()
                .map(existing -> existing.getDate() + "|" + existing.getMealType())
                .collect(Collectors.toSet());

        List<MealPlan> weeklyPlan = new ArrayList<>();
        Set<Long> alreadyPlanned = new HashSet<>();

        for (int day = 0; day < 7; day++) {
            LocalDate date = startDate.plusDays(day);

            for (MealType mealType : mealTypes) {
                if (occupied.contains(date + "|" + mealType)) {
                    continue;
                }

                List<Recipe> suitable = candidates.get(mealType);
                if (suitable.isEmpty()) {
                    continue;
                }

                Recipe chosen = suitable.stream()
                        .filter(r -> !alreadyPlanned.contains(r.getId()))
                        .findFirst()
                        // Weniger Rezepte als Slots: dann lieber ein zweites Mal dasselbe
                        // Gericht als eine Luecke im Plan.
                        .orElse(suitable.get(day % suitable.size()));
                alreadyPlanned.add(chosen.getId());

                MealPlan mealPlan = new MealPlan();
                mealPlan.setUser(user);
                mealPlan.setRecipe(chosen);
                mealPlan.setDate(date);
                mealPlan.setMealType(mealType);
                mealPlan.setPlannedServings(chosen.getServings());

                weeklyPlan.add(mealPlanRepository.save(mealPlan));
            }
        }

        return weeklyPlan;
    }
}
