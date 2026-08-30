package com.Finn.everything_app.service;

import com.Finn.everything_app.model.Habit;
import com.Finn.everything_app.model.HabitFrequency;
import com.Finn.everything_app.model.Routine;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.HabitRepository;
import com.Finn.everything_app.repository.RoutineRepository;
import com.Finn.everything_app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RoutineHabitServiceTest {

    @Autowired RoutineHabitService service;
    @Autowired HabitRepository habitRepository;
    @Autowired RoutineRepository routineRepository;
    @Autowired UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setUsername("rh-tester");
        u.setEmail("rh@test.local");
        u.setPasswordHash("x");
        user = userRepository.save(u);
    }

    private Routine routine(String name, Integer weekday) {
        Routine r = new Routine();
        r.setUser(user);
        r.setName(name);
        r.setPreferredWeekday(weekday);
        r.setOrderIndex(0);
        return routineRepository.save(r);
    }

    private Optional<Habit> habitOf(Routine r) {
        return habitRepository.findByRoutineId(r.getId());
    }

    @Test
    void einWochentagLegtEineGewohnheitAn() {
        Routine push = routine("Push", DayOfWeek.MONDAY.getValue());

        service.sync(push);

        Habit habit = habitOf(push).orElseThrow();
        assertEquals("Push", habit.getName());
        assertEquals(HabitFrequency.WEEKLY, habit.getFrequency());
        assertEquals(RoutineHabitService.CATEGORY, habit.getCategory());
        assertTrue(habit.getMonday());
        assertFalse(habit.getTuesday());
        assertNull(habit.getEndDate());
    }

    @Test
    void ohneWochentagEntstehtKeineGewohnheit() {
        Routine frei = routine("Freies Training", null);

        service.sync(frei);

        assertTrue(habitOf(frei).isEmpty());
    }

    @Test
    void einNeuerWochentagVerschiebtDieGewohnheitStattEineZweiteAnzulegen() {
        Routine push = routine("Push", DayOfWeek.MONDAY.getValue());
        service.sync(push);
        Long firstId = habitOf(push).orElseThrow().getId();

        push.setPreferredWeekday(DayOfWeek.THURSDAY.getValue());
        service.sync(push);

        Habit habit = habitOf(push).orElseThrow();
        assertEquals(firstId, habit.getId(), "dieselbe Gewohnheit, sonst beginnt die Streak neu");
        assertFalse(habit.getMonday());
        assertTrue(habit.getThursday());
    }

    @Test
    void derNameFolgtDerRoutine() {
        Routine r = routine("Push", DayOfWeek.MONDAY.getValue());
        service.sync(r);

        r.setName("Oberkörper A");
        service.sync(r);

        assertEquals("Oberkörper A", habitOf(r).orElseThrow().getName());
    }

    @Test
    void derWochentagWegzunehmenBeendetDieGewohnheitOhneSieZuLoeschen() {
        Routine push = routine("Push", DayOfWeek.MONDAY.getValue());
        service.sync(push);

        push.setPreferredWeekday(null);
        service.sync(push);

        // Die Historie ist der Grund fuer die Gewohnheit - sie zu loeschen naehme die
        // Abhakungen mit.
        Habit habit = habitOf(push).orElseThrow();
        assertEquals(LocalDate.now().minusDays(1), habit.getEndDate());
    }

    @Test
    void einWiederZugewiesenerTagWecktDieseGewohnheitWiederAuf() {
        Routine push = routine("Push", DayOfWeek.MONDAY.getValue());
        service.sync(push);
        Long id = habitOf(push).orElseThrow().getId();

        push.setPreferredWeekday(null);
        service.sync(push);
        push.setPreferredWeekday(DayOfWeek.FRIDAY.getValue());
        service.sync(push);

        Habit habit = habitOf(push).orElseThrow();
        assertEquals(id, habit.getId());
        assertNull(habit.getEndDate());
        assertTrue(habit.getFriday());
    }

    @Test
    void archivierenBeendetDieGewohnheitEbenfalls() {
        Routine push = routine("Push", DayOfWeek.MONDAY.getValue());
        service.sync(push);

        push.setIsArchived(true);
        service.sync(push);

        assertNotNull(habitOf(push).orElseThrow().getEndDate());
    }

    @Test
    void einTrainingHaktDenTagAb() {
        Routine push = routine("Push", DayOfWeek.MONDAY.getValue());
        service.sync(push);
        LocalDate day = LocalDate.now();

        service.markTrained(push, day);

        Habit habit = habitOf(push).orElseThrow();
        assertEquals(1, habit.getCurrentStreak());
    }

    @Test
    void zweimalDenselbenTagAbhakenZaehltEinmal() {
        Routine push = routine("Push", DayOfWeek.MONDAY.getValue());
        service.sync(push);
        LocalDate day = LocalDate.now();

        service.markTrained(push, day);
        service.markTrained(push, day);

        assertEquals(1, habitOf(push).orElseThrow().getCurrentStreak());
    }

    @Test
    void eineRoutineOhneGewohnheitAbzuhakenIstEinNoop() {
        Routine frei = routine("Freies Training", null);
        service.sync(frei);

        assertDoesNotThrow(() -> service.markTrained(frei, LocalDate.now()));
    }

    @Test
    void dasLoeschenDerRoutineNimmtDieGewohnheitMit() {
        Routine push = routine("Push", DayOfWeek.MONDAY.getValue());
        service.sync(push);

        service.remove(push.getId());

        assertTrue(habitOf(push).isEmpty());
    }
}
