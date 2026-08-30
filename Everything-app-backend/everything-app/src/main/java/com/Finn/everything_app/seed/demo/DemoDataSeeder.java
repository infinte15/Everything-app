package com.Finn.everything_app.seed.demo;

import com.Finn.everything_app.model.User;
import com.Finn.everything_app.model.UserPreferences;
import com.Finn.everything_app.repository.TaskRepository;
import com.Finn.everything_app.repository.UserRepository;
import com.Finn.everything_app.service.SmartSchedulerService;
import com.Finn.everything_app.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Legt einen vollständig gefüllten Demo-Datenbestand für einen einzelnen Nutzer an — alle
 * Spaces plus Kalender, so dicht, dass sich die App vorführen lässt, ohne vorher eine Stunde
 * lang von Hand Daten einzutippen.
 *
 * <p><b>Standardmäßig aus.</b> Einschalten über {@code app.demo-seed.enabled=true}:
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.demo-seed.enabled=true
 * </pre>
 *
 * <p>Weitere Schalter:
 * <ul>
 *   <li>{@code app.demo-seed.username} (Vorgabe {@code dev_tester}) — der Nutzer, dem die Daten
 *       gehören. Existiert er nicht, wird er mit dem Passwort {@code devpassword123} angelegt,
 *       damit {@code POST /api/auth/dev-login} direkt darauf passt.</li>
 *   <li>{@code app.demo-seed.reset=true} — löscht vorher <em>alle</em> Daten dieses Nutzers.
 *       Ohne den Schalter läuft der Seeder nicht los, sobald schon Daten da sind; sonst stünde
 *       nach dem zweiten Start alles doppelt im Kalender.</li>
 * </ul>
 *
 * <p>Alle Datumsangaben sind relativ zu {@code LocalDate.now()} aufgebaut: die Vergangenheit
 * liefert Verlauf und Statistiken, die Zukunft gibt dem Scheduler etwas zu planen. Ein Bestand
 * von gestern sieht deshalb morgen genauso frisch aus — bis auf das Wandern des Fensters, wofür
 * {@code reset} da ist.
 *
 * <p>Am Ende läuft einmal der echte Smart Scheduler. Erst dadurch bekommt der Kalender seine
 * Vorlesungen, eingeplanten Aufgaben, Gewohnheiten und Workouts; ohne diesen Lauf stünden nur
 * die festen Termine drin und der interessanteste Teil der App bliebe unsichtbar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(100) // nach dem Übungs-Katalog (@Order(0)) - die Routinen greifen auf ihn zu
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final UserService userService;
    private final TaskRepository taskRepository;

    private final DemoDataReset reset;
    private final DemoPlannerData plannerData;
    private final DemoStudyData studyData;
    private final DemoSportsData sportsData;
    private final DemoRecipeData recipeData;
    private final DemoFinanceData financeData;

    private final SmartSchedulerService scheduler;

    @Value("${app.demo-seed.username:dev_tester}")
    private String username;

    @Value("${app.demo-seed.reset:false}")
    private boolean resetFirst;

    @Override
    public void run(ApplicationArguments args) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            user = userService.registerUser(username, username + "@demo.local", "devpassword123");
            log.info("Demo-Nutzer '{}' neu angelegt (Passwort devpassword123)", username);
        }

        if (resetFirst) {
            reset.wipe(user.getId());
        } else if (taskRepository.findByUserId(user.getId()).size() > 0) {
            log.info("Demo-Daten für '{}' sind bereits vorhanden - übersprungen. "
                    + "Zum Neuaufbau mit --app.demo-seed.reset=true starten.", username);
            return;
        }

        LocalDate today = LocalDate.now();
        seedAll(user, today);

        runScheduler(user.getId(), today);
    }

    /**
     * Jeder Space committet für sich (die {@code seed}-Methoden sind einzeln transaktional).
     * Scheitert einer, fliegt die Ausnahme bis hier durch und der Start bricht sichtbar ab —
     * besser als eine halb gefüllte Datenbank, die beim nächsten Start als "schon vorhanden"
     * durchgeht. Aufräumen lässt sich das mit {@code --app.demo-seed.reset=true}.
     */
    private void seedAll(User user, LocalDate today) {
        preferences(user.getId());

        studyData.seed(user, today);
        sportsData.seed(user, today);
        recipeData.seed(user, today);
        financeData.seed(user, today);
        plannerData.seed(user, today);

        log.info("Demo-Daten für '{}' angelegt", user.getUsername());
    }

    /** Ein Tagesrhythmus, in dem der Solver sichtbar etwas zu verteilen hat. */
    private void preferences(Long userId) {
        UserPreferences prefs = userService.getOrCreatePreferences(userId);
        prefs.setWorkdayStart(LocalTime.of(8, 0));
        prefs.setWorkdayEnd(LocalTime.of(22, 0));
        prefs.setPeakProductivityTime(com.Finn.everything_app.model.ProductivityPeakTime.MORNING);
        prefs.setBreakDurationMinutes(15);
        prefs.setBufferMinutes(10);
        prefs.setMaxTaskMinutesPerDay(360);
        prefs.setDefaultMinChunkMinutes(30);
        prefs.setDefaultMaxChunkMinutes(120);
        prefs.setAutoScheduleEnabled(true);
        prefs.setNotificationsEnabled(true);
        prefs.setReminderMinutesBefore(15);
        prefs.setDarkMode(true);
        userService.updatePreferences(userId, prefs);
    }

    /**
     * Der Solver-Lauf steht bewusst außerhalb der Seed-Transaktion: er liest den Bestand neu
     * und würde sonst gegen noch nicht sichtbare Daten planen.
     */
    private void runScheduler(Long userId, LocalDate today) {
        try {
            var result = scheduler.generateOptimalSchedule(userId, today, scheduler.defaultHorizonEnd(today));
            log.info("Demo-Kalender geplant: {} Aufgaben-Blöcke, {} Gewohnheits-Blöcke ({})",
                    result.getScheduledTasks().size(),
                    result.getScheduledHabits().size(),
                    result.getSolverStatus());
        } catch (Exception e) {
            // Ohne OR-Tools-Bibliothek stehen trotzdem alle Daten - nur der Kalender bleibt leer.
            log.warn("Demo-Daten stehen, aber die Planung schlug fehl: {}", e.getMessage());
        }
    }
}
