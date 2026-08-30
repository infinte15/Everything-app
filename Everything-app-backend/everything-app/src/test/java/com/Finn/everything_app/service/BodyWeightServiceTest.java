package com.Finn.everything_app.service;

import com.Finn.everything_app.dto.BodyWeightEntryDTO;
import com.Finn.everything_app.dto.BodyWeightSeriesDTO;
import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.model.UserPreferences;
import com.Finn.everything_app.repository.BodyWeightEntryRepository;
import com.Finn.everything_app.repository.UserPreferencesRepository;
import com.Finn.everything_app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BodyWeightServiceTest {

    @Autowired BodyWeightService service;
    @Autowired BodyWeightEntryRepository repository;
    @Autowired UserRepository userRepository;
    @Autowired UserPreferencesRepository preferencesRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("bw-tester");
        user.setEmail("bw@test.local");
        user.setPasswordHash("x");
        user = userRepository.save(user);
        userId = user.getId();

        UserPreferences prefs = new UserPreferences();
        prefs.setUser(user);
        preferencesRepository.save(prefs);
    }

    private BodyWeightEntryDTO entry(LocalDate date, double kg) {
        return new BodyWeightEntryDTO(null, date, kg, null);
    }

    @Test
    void logsAnEntryAndReadsItBack() {
        service.log(userId, entry(LocalDate.now(), 78.4));

        BodyWeightSeriesDTO series = service.getSeries(userId, null);

        assertEquals(1, series.getEntries().size());
        assertEquals(78.4, series.getLatest().getWeightKg());
        assertNull(series.getPrevious(), "beim ersten Wert gibt es nichts zu vergleichen");
    }

    /**
     * Ein zweites Wiegen am selben Tag ersetzt den Wert. Zwei Punkte am selben Datum waeren
     * keine Kurve mehr, sondern eine Senkrechte.
     */
    @Test
    void aSecondWeighInOnTheSameDayReplacesTheValue() {
        LocalDate today = LocalDate.now();
        service.log(userId, entry(today, 78.4));
        service.log(userId, entry(today, 77.9));

        assertEquals(1, repository.findByUserIdOrderByDateAsc(userId).size());
        assertEquals(77.9, service.getSeries(userId, null).getLatest().getWeightKg());
    }

    @Test
    void previousIsTheValueBeforeTheLatest() {
        service.log(userId, entry(LocalDate.now().minusDays(7), 80.0));
        service.log(userId, entry(LocalDate.now(), 78.5));

        BodyWeightSeriesDTO series = service.getSeries(userId, null);

        assertEquals(78.5, series.getLatest().getWeightKg());
        assertEquals(80.0, series.getPrevious().getWeightKg());
    }

    /**
     * Der Zeitraum beschneidet nur die Kurve, nicht die grosse Zahl daneben. Sonst stuende auf
     * dem Startbildschirm "noch nichts gewogen", obwohl es einen Wert gibt - nur einen aelteren.
     */
    @Test
    void theWindowTrimsTheCurveButNotTheHeadlineNumber() {
        service.log(userId, entry(LocalDate.now().minusDays(90), 82.0));
        service.log(userId, entry(LocalDate.now().minusDays(80), 81.0));

        BodyWeightSeriesDTO series = service.getSeries(userId, LocalDate.now().minusDays(30));

        assertTrue(series.getEntries().isEmpty(), "im Fenster liegt nichts");
        assertEquals(81.0, series.getLatest().getWeightKg());
        assertEquals(82.0, series.getPrevious().getWeightKg());
    }

    @Test
    void rejectsValuesThatAreClearlyATypo() {
        assertThrows(BadRequestException.class, () -> service.log(userId, entry(LocalDate.now(), 7.8)));
        assertThrows(BadRequestException.class, () -> service.log(userId, entry(LocalDate.now(), 784.0)));
    }

    @Test
    void rejectsAWeighInFromTheFuture() {
        assertThrows(BadRequestException.class,
                () -> service.log(userId, entry(LocalDate.now().plusDays(1), 78.0)));
    }

    @Test
    void carriesTheTargetWeightAlong() {
        service.setTarget(userId, 75.0);

        assertEquals(75.0, service.getSeries(userId, null).getTargetWeightKg());
    }

    /** Ein Ziel muss sich auch wieder entfernen lassen - deshalb der eigene Endpunkt. */
    @Test
    void aTargetCanBeClearedAgain() {
        service.setTarget(userId, 75.0);
        service.setTarget(userId, null);

        assertNull(service.getSeries(userId, null).getTargetWeightKg());
    }

    @Test
    void rejectsAnImplausibleTarget() {
        assertThrows(BadRequestException.class, () -> service.setTarget(userId, 5.0));
    }

    @Test
    void entriesComeBackOldestFirstSoTheCurveIsDrawable() {
        service.log(userId, entry(LocalDate.now(), 78.0));
        service.log(userId, entry(LocalDate.now().minusDays(14), 80.0));
        service.log(userId, entry(LocalDate.now().minusDays(7), 79.0));

        var dates = service.getSeries(userId, null).getEntries().stream()
                .map(BodyWeightEntryDTO::getDate).toList();

        assertEquals(dates.stream().sorted().toList(), dates);
    }
}
