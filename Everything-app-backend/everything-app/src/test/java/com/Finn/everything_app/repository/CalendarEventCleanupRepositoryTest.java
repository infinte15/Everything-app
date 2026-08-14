package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.CalendarEvent;
import com.Finn.everything_app.model.EventType;
import com.Finn.everything_app.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Das Aufräumen der generierten Blöcke steckt seit der Umstellung auf eine Massenlöschung
 * vollständig in der WHERE-Klausel.
 *
 * Vorher stand dieselbe Regel als Java-Filter im Service und ließ sich mit Mockito prüfen. Jetzt
 * kann ein Mock-Test nur noch belegen, DASS gelöscht wird — nicht WAS. Und genau das "was" ist
 * hier die ganze Fachlichkeit: erledigte Blöcke sind Protokoll, übersprungene sind der einzige
 * Beleg für ein bereits verbrauchtes Wochenpensum, gepinnte gehören dem Nutzer. Verschwindet eine
 * dieser Gruppen still, fällt es erst im Betrieb auf. Deshalb läuft dieser Test gegen eine echte
 * Datenbank.
 */
@SpringBootTest
@Transactional
class CalendarEventCleanupRepositoryTest {

    @Autowired CalendarEventRepository calendarEventRepository;
    @Autowired UserRepository userRepository;

    private static final List<EventType> GENERIERT =
            List.of(EventType.TASK, EventType.HABIT, EventType.WORKOUT, EventType.PROJECT);

    private User nutzer;
    private LocalDateTime von;
    private LocalDateTime bis;

    @BeforeEach
    void setUp() {
        nutzer = new User();
        nutzer.setUsername("aufraeum-nutzer-" + System.nanoTime());
        nutzer.setEmail(nutzer.getUsername() + "@test.local");
        nutzer.setPasswordHash("x");
        nutzer = userRepository.save(nutzer);

        von = LocalDateTime.now().withNano(0).plusDays(1).withHour(0).withMinute(0).withSecond(0);
        bis = von.plusDays(7);
    }

    @Test
    void loeschtNurDieOffenenGeneriertenBloecke() {
        CalendarEvent offen        = block("offen", EventType.TASK, false, null, null);
        CalendarEvent erledigt     = block("erledigt", EventType.TASK, false, von.plusHours(2), null);
        CalendarEvent uebersprungen= block("übersprungen", EventType.HABIT, false, null, von.plusHours(3));
        CalendarEvent gepinnt      = block("gepinnt", EventType.WORKOUT, true, null, null);
        CalendarEvent fremderTyp   = block("Vorlesung", EventType.CLASS, false, null, null);

        int geloescht = calendarEventRepository.deleteGeneratedEvents(nutzer.getId(), GENERIERT, von, bis);

        assertEquals(1, geloescht, "nur der offene Block darf weggeräumt werden");
        Set<Long> uebrig = uebrigeIds();
        assertFalse(uebrig.contains(offen.getId()));
        assertTrue(uebrig.contains(erledigt.getId()),
                "erledigte Blöcke sind Protokoll und bleiben stehen");
        assertTrue(uebrig.contains(uebersprungen.getId()),
                "würde der übersprungene Block verschwinden, käme sofort Ersatz");
        assertTrue(uebrig.contains(gepinnt.getId()),
                "gepinnte Blöcke gehören dem Nutzer, nicht dem Solver");
        assertTrue(uebrig.contains(fremderTyp.getId()),
                "Vorlesungen laufen über ihren eigenen Abgleich");
    }

    @Test
    void bleibtAufDenZeitraumBeschraenkt() {
        CalendarEvent davor  = block("davor", EventType.TASK, false, null, null);
        davor.setStartTime(von.minusDays(2));
        davor.setEndTime(von.minusDays(2).plusHours(1));
        calendarEventRepository.save(davor);

        CalendarEvent danach = block("danach", EventType.TASK, false, null, null);
        danach.setStartTime(bis.plusDays(2));
        danach.setEndTime(bis.plusDays(2).plusHours(1));
        calendarEventRepository.save(danach);

        block("drin", EventType.TASK, false, null, null);

        int geloescht = calendarEventRepository.deleteGeneratedEvents(nutzer.getId(), GENERIERT, von, bis);

        assertEquals(1, geloescht);
        Set<Long> uebrig = uebrigeIds();
        assertTrue(uebrig.contains(davor.getId()), "vor dem Fenster wird nicht angefasst");
        assertTrue(uebrig.contains(danach.getId()), "hinter dem Fenster ebenso wenig");
    }

    @Test
    void fremdeNutzerBleibenUnberuehrt() {
        User anderer = new User();
        anderer.setUsername("fremder-" + System.nanoTime());
        anderer.setEmail(anderer.getUsername() + "@test.local");
        anderer.setPasswordHash("x");
        anderer = userRepository.save(anderer);

        CalendarEvent fremd = new CalendarEvent();
        fremd.setUser(anderer);
        fremd.setTitle("fremd");
        fremd.setEventType(EventType.TASK);
        fremd.setIsFixed(false);
        fremd.setStartTime(von.plusHours(1));
        fremd.setEndTime(von.plusHours(2));
        fremd = calendarEventRepository.save(fremd);

        block("eigen", EventType.TASK, false, null, null);

        int geloescht = calendarEventRepository.deleteGeneratedEvents(nutzer.getId(), GENERIERT, von, bis);

        assertEquals(1, geloescht);
        assertTrue(calendarEventRepository.findById(fremd.getId()).isPresent(),
                "die Löschung darf nie über den eigenen Bestand hinausgreifen");
    }

    @Test
    void vorlesungenWerdenUnabhaengigWeggeraeumt() {
        CalendarEvent vorlesung = block("Analysis II", EventType.CLASS, false, null, null);
        CalendarEvent aufgabe   = block("Aufgabe", EventType.TASK, false, null, null);

        int geloescht = calendarEventRepository.deleteGeneratedEventsOfType(
                nutzer.getId(), EventType.CLASS, von, bis);

        assertEquals(1, geloescht);
        Set<Long> uebrig = uebrigeIds();
        assertFalse(uebrig.contains(vorlesung.getId()));
        assertTrue(uebrig.contains(aufgabe.getId()),
                "der Vorlesungsabgleich darf keine geplanten Blöcke mitreißen");
    }

    private Set<Long> uebrigeIds() {
        return calendarEventRepository.findAll().stream()
                .map(CalendarEvent::getId)
                .collect(Collectors.toSet());
    }

    private CalendarEvent block(String titel, EventType typ, boolean gepinnt,
                                LocalDateTime erledigtAm, LocalDateTime uebersprungenAm) {
        CalendarEvent e = new CalendarEvent();
        e.setUser(nutzer);
        e.setTitle(titel);
        e.setEventType(typ);
        e.setIsFixed(gepinnt);
        e.setStartTime(von.plusHours(1));
        e.setEndTime(von.plusHours(2));
        e.setCompletedAt(erledigtAm);
        e.setSkippedAt(uebersprungenAm);
        return calendarEventRepository.save(e);
    }
}
