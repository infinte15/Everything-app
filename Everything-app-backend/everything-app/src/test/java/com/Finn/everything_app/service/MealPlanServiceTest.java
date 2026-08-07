package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MealPlanServiceTest {

    @Mock MealPlanRepository mealPlanRepository;
    @Mock UserRepository userRepository;
    @Mock RecipeRepository recipeRepository;
    @Mock RecipeService recipeService;
    @Mock TaskService taskService;

    @InjectMocks
    MealPlanService service;

    private User user() {
        User user = new User();
        user.setId(1L);
        return user;
    }

    private Recipe recipe(long id, String name, MealType... suitableFor) {
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName(name);
        recipe.setServings(4);
        recipe.setPrepTimeMinutes(10);
        recipe.setCookTimeMinutes(20);
        recipe.setCategory("Hauptgericht");
        recipe.getSuitableFor().addAll(List.of(suitableFor));
        recipe.replaceIngredients(new ArrayList<>());
        return recipe;
    }

    // ── Wochenplanung ─────────────────────────────────────────────────────────────────────

    // Frueher brach die ganze Generierung mit RuntimeException("Keine Rezepte verfügbar") ab,
    // auch wenn nur das Fruehstueck fehlte.
    @Test
    void fehlendeFruehstuecksrezepteBrechenDenPlanNichtAb() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(recipeRepository.findSuitableFor(1L, MealType.FRUEHSTUECK)).thenReturn(List.of());
        when(recipeRepository.findSuitableFor(1L, MealType.MITTAGESSEN))
                .thenReturn(List.of(recipe(1L, "Lasagne", MealType.MITTAGESSEN)));
        when(recipeRepository.findSuitableFor(1L, MealType.ABENDESSEN))
                .thenReturn(List.of(recipe(2L, "Suppe", MealType.ABENDESSEN)));
        when(mealPlanRepository.save(any(MealPlan.class))).thenAnswer(i -> i.getArgument(0));

        List<MealPlan> plan = service.generateWeeklyPlan(1L, LocalDate.now());

        // 7 Tage x 2 belegbare Mahlzeiten, das Fruehstueck faellt aus.
        assertEquals(14, plan.size());
        assertTrue(plan.stream().noneMatch(p -> p.getMealType() == MealType.FRUEHSTUECK));
    }

    @Test
    void ohneJedesPassendeRezeptBleibtDerPlanLeerStattZuWerfen() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(recipeRepository.findSuitableFor(eq(1L), any())).thenReturn(List.of());

        assertTrue(service.generateWeeklyPlan(1L, LocalDate.now()).isEmpty());
    }

    @Test
    void solangeEsAlternativenGibtKommtKeinRezeptZweimalVor() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(recipeRepository.findSuitableFor(1L, MealType.FRUEHSTUECK)).thenReturn(List.of());
        when(recipeRepository.findSuitableFor(1L, MealType.ABENDESSEN)).thenReturn(List.of());
        when(recipeRepository.findSuitableFor(1L, MealType.MITTAGESSEN)).thenReturn(List.of(
                recipe(1L, "A", MealType.MITTAGESSEN),
                recipe(2L, "B", MealType.MITTAGESSEN),
                recipe(3L, "C", MealType.MITTAGESSEN)));
        when(mealPlanRepository.save(any(MealPlan.class))).thenAnswer(i -> i.getArgument(0));

        List<MealPlan> plan = service.generateWeeklyPlan(1L, LocalDate.now());

        assertEquals(3, plan.stream().map(p -> p.getRecipe().getId()).distinct().count());
    }

    // ── Kochzeit im Kalender ──────────────────────────────────────────────────────────────

    @Test
    void ohneSchalterEntstehtKeineAufgabe() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(recipeRepository.findByIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(recipe(1L, "Lasagne", MealType.ABENDESSEN)));
        when(mealPlanRepository.save(any(MealPlan.class))).thenAnswer(i -> i.getArgument(0));

        MealPlan plan = new MealPlan();
        plan.setDate(LocalDate.now());
        plan.setMealType(MealType.ABENDESSEN);

        MealPlan created = service.createMealPlan(1L, plan, 1L, false);

        assertNull(created.getCookingTaskId());
        verify(taskService, never()).createTask(any(), any());
    }

    @Test
    void mitSchalterEntstehtEineAufgabeMitDerKochdauer() {
        Task saved = new Task();
        saved.setId(42L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(recipeRepository.findByIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(recipe(1L, "Lasagne", MealType.ABENDESSEN)));
        when(taskService.createTask(eq(1L), any(Task.class))).thenReturn(saved);
        when(mealPlanRepository.save(any(MealPlan.class))).thenAnswer(i -> i.getArgument(0));

        MealPlan plan = new MealPlan();
        plan.setDate(LocalDate.of(2026, 8, 10));
        plan.setMealType(MealType.ABENDESSEN);

        MealPlan created = service.createMealPlan(1L, plan, 1L, true);

        assertEquals(42L, created.getCookingTaskId());

        ArgumentCaptor<Task> task = ArgumentCaptor.forClass(Task.class);
        verify(taskService).createTask(eq(1L), task.capture());
        assertEquals("Kochen: Lasagne", task.getValue().getTitle());
        assertEquals(30, task.getValue().getEstimatedDurationMinutes());
        assertEquals(SpaceType.RECIPES, task.getValue().getSpaceType());
        // Kochen laesst sich nicht in drei Haeppchen ueber den Tag verteilen.
        assertFalse(task.getValue().getSplittable());
        assertEquals(19, task.getValue().getDeadline().getHour());
    }

    // Vorher blieb die Aufgabe stehen, wenn man die Mahlzeit wieder aus dem Plan nahm - und
    // der Scheduler hat sie weiter in den Kalender gelegt.
    @Test
    void mitDerMahlzeitVerschwindetAuchDieKochzeit() {
        MealPlan plan = new MealPlan();
        plan.setId(5L);
        plan.setCookingTaskId(42L);
        when(mealPlanRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(plan));

        service.deleteMealPlan(1L, 5L);

        verify(mealPlanRepository).delete(plan);
        verify(taskService).deleteTask(42L);
    }

    @Test
    void ohneKochzeitWirdKeineFremdeAufgabeGeloescht() {
        MealPlan plan = new MealPlan();
        plan.setId(5L);
        when(mealPlanRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(plan));

        service.deleteMealPlan(1L, 5L);

        verify(taskService, never()).deleteTask(any());
    }

    // Eine geplante Mahlzeit abzuhaken IST gekocht zu haben - sonst muesste man dasselbe
    // zweimal melden, und "lange nicht gekocht" waere falsch.
    @Test
    void abhakenSchreibtEinenEintragInsKochprotokoll() {
        Recipe recipe = recipe(1L, "Lasagne", MealType.ABENDESSEN);
        MealPlan plan = new MealPlan();
        plan.setId(5L);
        plan.setRecipe(recipe);
        plan.setPlannedServings(6);
        plan.setIsCompleted(false);
        when(mealPlanRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(plan));
        when(mealPlanRepository.save(any(MealPlan.class))).thenAnswer(i -> i.getArgument(0));

        MealPlan completed = service.completeMealPlan(1L, 5L);

        assertTrue(completed.getIsCompleted());
        assertNotNull(completed.getCompletedAt());
        verify(recipeService).logCooked(eq(1L), eq(1L), argThat(e -> e.getServings() == 6));
    }

    // Zweimal abhaken darf nicht zweimal ins Protokoll wandern.
    @Test
    void zweimalAbhakenZaehltNurEinmal() {
        MealPlan plan = new MealPlan();
        plan.setId(5L);
        plan.setIsCompleted(true);
        when(mealPlanRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(plan));

        service.completeMealPlan(1L, 5L);

        verify(recipeService, never()).logCooked(any(), any(), any());
        verify(mealPlanRepository, never()).save(any());
    }

    @Test
    void fremderWochenplanEintragIstNichtGefunden() {
        when(mealPlanRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

        assertThrows(com.Finn.everything_app.exception.ResourceNotFoundException.class,
                () -> service.deleteMealPlan(1L, 5L));
    }
}
