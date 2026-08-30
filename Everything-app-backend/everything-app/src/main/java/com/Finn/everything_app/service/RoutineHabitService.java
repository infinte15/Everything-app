package com.Finn.everything_app.service;

import com.Finn.everything_app.model.Habit;
import com.Finn.everything_app.model.HabitFrequency;
import com.Finn.everything_app.model.Routine;
import com.Finn.everything_app.repository.HabitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Haelt zu jeder Routine mit Wunsch-Wochentag eine Gewohnheit im Habit-Space.
 *
 * <p><b>Warum.</b> "Montags Push, mittwochs Pull, freitags Beine" ist fachlich eine Gewohnheit.
 * Bisher lebte diese Zusage nur im Gym-Space: der Habit-Space kannte sie nicht, und die Streak,
 * die die App fuer Lesen oder Laufen fuehrt, gab es fuers Training nicht. Die Zuordnung eines
 * Wochentags legt deshalb eine Gewohnheit an, das Beenden eines Trainings hakt sie ab.
 *
 * <p><b>Was diese Gewohnheit nicht tut.</b> Sie belegt keine Zeit. Der Smart Scheduler laesst
 * Gewohnheiten mit Routine bewusst aus - den Termin legt der Workout-Platzhalter derselben
 * Routine, und beides zu planen ergaebe zwei Eintraege fuer ein Training. Sie ist also reine
 * Nachhaltung, kein zweiter Planungsgegenstand.
 *
 * <p><b>Warum nicht geloescht wird.</b> Nimmt man einer Routine den Wochentag wieder, endet die
 * Gewohnheit ({@code endDate}), statt zu verschwinden. Ein Loeschen naehme die
 * {@code HabitCompletion}s mit - also genau die Historie, wegen der es die Gewohnheit gibt.
 * Wird der Tag spaeter erneut gesetzt, laeuft dieselbe Gewohnheit mit ihrer Streak weiter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoutineHabitService {

    /** Ohne eigene Schaetzung an der Routine: dieselbe Annahme, die der Planer trifft. */
    static final int DEFAULT_DURATION_MIN = 45;

    static final String CATEGORY = "Sport";

    private final HabitRepository habitRepository;
    private final HabitService habitService;

    /**
     * Bringt die Gewohnheit einer Routine auf deren aktuellen Stand.
     *
     * <p>Aufzurufen, wann immer sich Name, Wunsch-Wochentag, Dauer oder Archiv-Status aendern.
     * Idempotent: gleicher Stand, kein Schreibzugriff.
     */
    @Transactional
    public void sync(Routine routine) {
        if (routine == null || routine.getId() == null) {
            return;
        }
        Optional<Habit> existing = habitRepository.findByRoutineId(routine.getId());
        boolean wanted = routine.getPreferredWeekday() != null
                && !Boolean.TRUE.equals(routine.getIsArchived());

        if (!wanted) {
            existing.ifPresent(this::retire);
            return;
        }

        Habit habit = existing.orElseGet(() -> newHabitFor(routine));
        apply(habit, routine);
        habitRepository.save(habit);
    }

    /**
     * Hakt die Gewohnheit der Routine fuer diesen Tag ab.
     *
     * <p>Ein Training zaehlt an dem Tag, an dem es beendet wurde - nicht an dem, fuer den es
     * geplant war. Wer sein Montagstraining am Dienstag nachholt, hat am Dienstag trainiert.
     */
    @Transactional
    public void markTrained(Routine routine, LocalDate date) {
        if (routine == null || routine.getId() == null || date == null) {
            return;
        }
        habitRepository.findByRoutineId(routine.getId()).ifPresent(habit -> {
            // Eine beendete Gewohnheit wieder abzuhaken waere still irrefuehrend: sie steht
            // im Habit-Space als abgeschlossen da und bekaeme trotzdem neue Haken.
            if (habit.getEndDate() != null && habit.getEndDate().isBefore(date)) {
                return;
            }
            habitService.markHabitComplete(habit.getId(), date);
        });
    }

    /** Loescht die Gewohnheit - nur beim Loeschen der Routine selbst. */
    @Transactional
    public void remove(Long routineId) {
        if (routineId == null) {
            return;
        }
        habitRepository.findByRoutineId(routineId).ifPresent(habitRepository::delete);
    }

    private Habit newHabitFor(Routine routine) {
        Habit habit = new Habit();
        habit.setUser(routine.getUser());
        habit.setRoutine(routine);
        habit.setFrequency(HabitFrequency.WEEKLY);
        habit.setTimesPerWeek(1);
        habit.setCategory(CATEGORY);
        habit.setStartDate(LocalDate.now());
        return habit;
    }

    private void apply(Habit habit, Routine routine) {
        habit.setName(routine.getName());
        habit.setDescription("Trainingseinheit aus dem Gym-Bereich. "
                + "Wochentag und Name folgen der Routine.");
        habit.setColor(routine.getColorHex());
        habit.setDurationMinutes(routine.getEstimatedDurationMinutes() != null
                ? routine.getEstimatedDurationMinutes()
                : DEFAULT_DURATION_MIN);
        // Ein erneut zugewiesener Wochentag weckt die Gewohnheit wieder auf.
        habit.setEndDate(null);
        if (habit.getStartDate() == null) {
            habit.setStartDate(LocalDate.now());
        }
        setWeekday(habit, routine.getPreferredWeekday());
    }

    /** Genau ein Wochentag - die Routine kennt nur einen. */
    private void setWeekday(Habit habit, Integer isoWeekday) {
        DayOfWeek day = isoWeekday == null ? null : DayOfWeek.of(isoWeekday);
        habit.setMonday(day == DayOfWeek.MONDAY);
        habit.setTuesday(day == DayOfWeek.TUESDAY);
        habit.setWednesday(day == DayOfWeek.WEDNESDAY);
        habit.setThursday(day == DayOfWeek.THURSDAY);
        habit.setFriday(day == DayOfWeek.FRIDAY);
        habit.setSaturday(day == DayOfWeek.SATURDAY);
        habit.setSunday(day == DayOfWeek.SUNDAY);
    }

    /**
     * Beendet die Gewohnheit, ohne sie zu loeschen.
     *
     * <p>Gestern statt heute: bis einschliesslich gestern hat sie gezaehlt, ab heute steht sie
     * nicht mehr an. Mit {@code endDate = heute} waere sie heute noch faellig, obwohl der
     * Wochentag gerade weggenommen wurde.
     */
    private void retire(Habit habit) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (habit.getEndDate() != null && !habit.getEndDate().isAfter(yesterday)) {
            return;
        }
        habit.setEndDate(yesterday);
        habitRepository.save(habit);
    }
}
