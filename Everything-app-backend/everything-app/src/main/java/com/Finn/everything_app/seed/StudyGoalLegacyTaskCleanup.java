package com.Finn.everything_app.seed;

import com.Finn.everything_app.model.Task;
import com.Finn.everything_app.repository.CalendarEventRepository;
import com.Finn.everything_app.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Räumt die Aufgaben der alten, rein clientseitigen Lernziel-Brücke weg.
 *
 * <p>Bis zur Persistierung der Lernziele lebten diese nur im Arbeitsspeicher des Clients,
 * erzeugten dabei aber echte Tasks mit {@code category = "study-goal:<Fach>:<Woche>"}. Nach
 * jedem Neustart war das Ziel weg, der Task blieb — und {@code findSchedulableTasks} füttert
 * ihn seither bei jedem Lauf weiter in den Solver. Zuordnen kann diese Zeilen niemand mehr.
 *
 * <p>Gelöscht statt abgeschlossen: sie stammen nicht vom Nutzer, sondern sind Artefakte der
 * Brücke. Abschließen hinterließe sie dauerhaft sichtbar in der Aufgabenliste.
 *
 * <p>Idempotent — nach dem ersten Lauf liefert die Abfrage nichts mehr. Die neue Brücke
 * schreibt {@code category = "Lernziel"}, der Präfix kann also nicht wieder entstehen.
 *
 * <p>Abschaltbar mit {@code app.study-goal-cleanup.enabled=false} (so läuft er in Tests nicht).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.study-goal-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class StudyGoalLegacyTaskCleanup implements ApplicationRunner {

    private static final String LEGACY_PREFIX = "study-goal:";

    private final TaskRepository taskRepository;
    private final CalendarEventRepository calendarEventRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Task> orphans = taskRepository.findByCategoryStartingWith(LEGACY_PREFIX);
        if (orphans.isEmpty()) return;

        for (Task task : orphans) {
            // Zuerst die Kalendereinträge: sie hängen per Fremdschlüssel am Task, sonst
            // scheitert das Löschen und der Aufräumer bricht bei jedem Start erneut ab.
            calendarEventRepository.deleteAll(calendarEventRepository.findByRelatedTaskId(task.getId()));
        }
        taskRepository.deleteAll(orphans);

        log.info("{} verwaiste Lernziel-Aufgaben der alten Brücke entfernt", orphans.size());
    }
}
