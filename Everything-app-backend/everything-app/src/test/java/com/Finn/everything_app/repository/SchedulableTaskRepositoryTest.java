package com.Finn.everything_app.repository;

import com.Finn.everything_app.model.Task;
import com.Finn.everything_app.model.TaskStatus;
import com.Finn.everything_app.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Welche Aufgaben der Scheduler überhaupt zu sehen bekommt.
 *
 * <p>Die Regel steht vollständig in der WHERE-Klausel von {@code findSchedulableTasks} und ist
 * damit für einen Mockito-Test unsichtbar: dort liefert {@code taskService.getSchedulableTasks}
 * genau das, was der Test hineinlegt. Bis 31.08.2026 fehlte {@code IN_PROGRESS} in der Abfrage,
 * und die Folge war im Betrieb nicht zu übersehen und im Test nicht zu finden — wer eine Aufgabe auf
 * "in Arbeit" setzte, verlor jede geplante Zeit dafür, ohne eine Warnung zu bekommen: ohne Task
 * gibt es keinen Chunk, ohne Chunk kein {@code AtRiskItem}.
 */
@SpringBootTest
@Transactional
class SchedulableTaskRepositoryTest {

    @Autowired TaskRepository taskRepository;
    @Autowired UserRepository userRepository;

    private User nutzer;

    @BeforeEach
    void setUp() {
        nutzer = new User();
        nutzer.setUsername("planbar-nutzer-" + System.nanoTime());
        nutzer.setEmail(nutzer.getUsername() + "@test.local");
        nutzer.setPasswordHash("x");
        nutzer = userRepository.save(nutzer);
    }

    @Test
    void offeneUndAngefangeneAufgabenWerdenGeplant() {
        Task offen       = aufgabe("offen", TaskStatus.TODO);
        Task angefangen  = aufgabe("angefangen", TaskStatus.IN_PROGRESS);
        Task erledigt    = aufgabe("erledigt", TaskStatus.COMPLETED);
        Task abgebrochen = aufgabe("abgebrochen", TaskStatus.CANCELLED);

        Set<Long> planbar = taskRepository.findSchedulableTasks(nutzer.getId()).stream()
                .map(Task::getId).collect(Collectors.toSet());

        assertTrue(planbar.contains(offen.getId()));
        assertTrue(planbar.contains(angefangen.getId()),
                "angefangene Arbeit ist genau die, deren Restzeit verteidigt gehört");
        assertFalse(planbar.contains(erledigt.getId()));
        assertFalse(planbar.contains(abgebrochen.getId()));
    }

    @Test
    void fremdeAufgabenBleibenAussen() {
        Task eigene = aufgabe("eigene", TaskStatus.IN_PROGRESS);

        User anderer = new User();
        anderer.setUsername("fremd-" + System.nanoTime());
        anderer.setEmail(anderer.getUsername() + "@test.local");
        anderer.setPasswordHash("x");
        anderer = userRepository.save(anderer);
        Task fremde = new Task();
        fremde.setUser(anderer);
        fremde.setTitle("fremde");
        fremde.setStatus(TaskStatus.IN_PROGRESS);
        fremde.setPriority(3);
        fremde.setEstimatedDurationMinutes(60);
        fremde.setCreatedAt(LocalDateTime.now());
        taskRepository.save(fremde);

        List<Task> planbar = taskRepository.findSchedulableTasks(nutzer.getId());

        assertEquals(1, planbar.size());
        assertEquals(eigene.getId(), planbar.get(0).getId());
    }

    /**
     * Die Quelle der Schätzkorrektur liefert die JÜNGSTEN Zeilen, nicht irgendwelche.
     *
     * <p>Dieselbe Begründung wie oben für {@code findSchedulableTasks}: Fenster und Sortierung
     * stehen seit dem Umbau in der Abfrage statt in Java, und für einen Mockito-Test auf
     * {@code EstimateCalibrationService} sind sie damit unsichtbar — dort liefert das Mock genau
     * das, was der Test hineinlegt. Stünde die Reihenfolge falsch herum, lernte der Planer
     * dauerhaft aus den ÄLTESTEN Aufgaben und niemand würde es merken.
     */
    @Test
    void schaetzkorrekturSiehtNurDieJuengstenAufgaben() {
        Task alt  = abgeschlossen("alt",  60, LocalDateTime.now().minusDays(100));
        Task neu  = abgeschlossen("neu",  60, LocalDateTime.now().minusDays(1));
        Task mitte = abgeschlossen("mitte", 60, LocalDateTime.now().minusDays(50));

        List<Task> fenster = taskRepository.findRecentCompletedWithEstimate(
                nutzer.getId(), PageRequest.of(0, 2));

        assertEquals(List.of(neu.getId(), mitte.getId()),
                fenster.stream().map(Task::getId).toList(),
                "jüngste zuerst, und nach zwei Zeilen ist Schluss");
        assertFalse(fenster.stream().anyMatch(t -> t.getId().equals(alt.getId())));
    }

    /**
     * Bestandszeilen ohne Ursprungsschätzung dürfen das Fenster nicht auffüllen — sie sind keine
     * Stichprobe, und wer sie mitzählt, verschenkt genau so viele echte.
     */
    @Test
    void zeilenOhneUrsprungsschaetzungFuellenDasFensterNicht() {
        Task ohne = abgeschlossen("ohne", null, LocalDateTime.now().minusDays(1));
        Task mit  = abgeschlossen("mit",   60, LocalDateTime.now().minusDays(2));

        List<Task> fenster = taskRepository.findRecentCompletedWithEstimate(
                nutzer.getId(), PageRequest.of(0, 20));

        assertEquals(List.of(mit.getId()), fenster.stream().map(Task::getId).toList(),
                "die jüngere Zeile ohne Bezugspunkt gehört gar nicht erst geladen: " + ohne.getId());
    }

    private Task abgeschlossen(String titel, Integer ursprung, LocalDateTime fertigAm) {
        Task t = aufgabe(titel, TaskStatus.COMPLETED);
        t.setOriginalEstimateMinutes(ursprung);
        t.setCompletedAt(fertigAm);
        return taskRepository.save(t);
    }

    private Task aufgabe(String titel, TaskStatus status) {
        Task t = new Task();
        t.setUser(nutzer);
        t.setTitle(titel);
        t.setStatus(status);
        t.setPriority(3);
        t.setEstimatedDurationMinutes(60);
        t.setCreatedAt(LocalDateTime.now());
        return taskRepository.save(t);
    }
}
