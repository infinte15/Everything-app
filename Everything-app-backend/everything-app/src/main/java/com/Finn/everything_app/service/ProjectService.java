package com.Finn.everything_app.service;

import com.Finn.everything_app.event.ScheduleChangedEvent;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectService {

    /** Grenzen fuer die Wochenplanung. Ohne Deckel vervielfacht ein Request das Solver-Modell. */
    private static final int MAX_WEEKLY_SESSIONS      = 14;
    private static final int MIN_SESSION_MINUTES      = 15;
    private static final int MAX_SESSION_MINUTES      = 480;
    private static final int DEFAULT_WEEKLY_SESSIONS  = 1;
    private static final int DEFAULT_SESSION_MINUTES  = 60;

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Project createProject(Long userId, Project project) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        project.setUser(user);
        project.setStatus(project.getStatus() != null ? project.getStatus() : ProjectStatus.PLANNING);
        project.setCompletionPercentage(0);
        project.setTasksTotal(0);
        project.setTasksCompleted(0);
        // Der Mapper reicht null durch ("nicht angegeben") — die Defaults vergibt deshalb hier
        // das Anlegen, nicht das Entity-Feld.
        project.setWeeklySessionCount(project.getWeeklySessionCount() != null
                ? clampSessionCount(project.getWeeklySessionCount())
                : DEFAULT_WEEKLY_SESSIONS);
        project.setSessionDurationMinutes(project.getSessionDurationMinutes() != null
                ? clampSessionDuration(project.getSessionDurationMinutes())
                : DEFAULT_SESSION_MINUTES);

        Project saved = projectRepository.save(project);
        // Ein frisches Projekt mit Wochenpensum will sofort Bloecke im Kalender.
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return saved;
    }

    public List<Project> getUserProjects(Long userId) {
        return projectRepository.findByUserId(userId);
    }

    /** Besitzgeprueft — die Variante ohne userId ist nur fuer Aufrufer, die den Besitz schon kennen. */
    public Project getProjectById(Long id, Long userId) {
        return projectRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt", "id", id));
    }

    public Project getProjectById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt", "id", id));
    }

    public List<Project> getProjectsByStatus(Long userId, ProjectStatus status) {
        return projectRepository.findByUserIdAndStatus(userId, status);
    }

    public List<Task> getProjectTasks(Long projectId, Long userId) {
        getProjectById(projectId, userId);   // 404 statt leerer Liste bei fremdem Projekt
        return taskRepository.findByProjectIdAndUserIdOrderByStatusAscDeadlineAsc(projectId, userId);
    }

    @Transactional
    public Project updateProject(Long id, Long userId, Project updatedProject) {
        Project project = getProjectById(id, userId);

        // Vorher-Zustand der planungsrelevanten Felder: eine reine Umbenennung darf keinen
        // CP-SAT-Lauf ausloesen (gleiche Ueberlegung wie timesChanged in CalendarEventService).
        Object before = schedulingFingerprint(project);

        if (updatedProject.getName() != null) {
            project.setName(updatedProject.getName());
        }
        if (updatedProject.getDescription() != null) {
            project.setDescription(updatedProject.getDescription());
        }
        if (updatedProject.getStartDate() != null) {
            project.setStartDate(updatedProject.getStartDate());
        }
        if (updatedProject.getTargetEndDate() != null) {
            project.setTargetEndDate(updatedProject.getTargetEndDate());
        }
        if (updatedProject.getActualEndDate() != null) {
            project.setActualEndDate(updatedProject.getActualEndDate());
        }
        if (updatedProject.getStatus() != null) {
            project.setStatus(updatedProject.getStatus());
        }
        // completionPercentage kommt bewusst NICHT mehr vom Client: der Wert wird aus den
        // verknuepften Aufgaben abgeleitet (updateProjectStatistics).
        if (updatedProject.getWeeklySessionCount() != null) {
            project.setWeeklySessionCount(clampSessionCount(updatedProject.getWeeklySessionCount()));
        }
        if (updatedProject.getSessionDurationMinutes() != null) {
            project.setSessionDurationMinutes(clampSessionDuration(updatedProject.getSessionDurationMinutes()));
        }

        Project saved = projectRepository.save(project);
        if (!Objects.equals(before, schedulingFingerprint(saved))) {
            eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        }
        return saved;
    }

    @Transactional
    public void deleteProject(Long id, Long userId) {
        Project project = getProjectById(id, userId);
        // Erst die Projektbloecke: sonst verletzt das Delete related_project_id (500 statt 204).
        calendarEventRepository.deleteAll(calendarEventRepository.findByRelatedProjectId(id));
        // Aufgaben ueberleben ihr Projekt — nur die Zuordnung faellt weg.
        taskRepository.detachFromProject(id);
        projectRepository.delete(project);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
    }

    /**
     * Rechnet Fortschritt und Status aus den verknuepften Aufgaben neu. Wird von jeder
     * Task-Mutation aufgerufen — vorher war die Methode toter Code und der Fortschritt blieb
     * dauerhaft bei 0 %.
     */
    @Transactional
    public void updateProjectStatistics(Long projectId) {
        Project project = getProjectById(projectId);

        List<Task> tasks = taskRepository.findByProjectId(projectId);

        int total = tasks.size();
        int completed = (int) tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .count();

        project.setTasksTotal(total);
        project.setTasksCompleted(completed);

        // Ohne Aufgaben ist der Fortschritt 0 und nicht "der letzte bekannte Wert" — sonst bleibt
        // nach dem Loeschen der letzten Aufgabe eine alte 100 stehen.
        int percentage = total > 0 ? (int) ((completed * 100.0) / total) : 0;
        project.setCompletionPercentage(percentage);

        ProjectStatus statusBefore = project.getStatus();
        if (total > 0 && percentage == 100 && statusBefore != ProjectStatus.COMPLETED
                && statusBefore != ProjectStatus.CANCELLED) {
            project.setStatus(ProjectStatus.COMPLETED);
            project.setActualEndDate(LocalDate.now());
        } else if (percentage < 100 && statusBefore == ProjectStatus.COMPLETED) {
            // Umkehrbar: wird eine Aufgabe nachgetragen oder wieder geoeffnet, ist das Projekt
            // nicht mehr fertig — sonst bekaeme es nie wieder Projektzeit im Kalender.
            project.setStatus(ProjectStatus.ACTIVE);
            project.setActualEndDate(null);
        } else if (percentage > 0 && percentage < 100 && statusBefore == ProjectStatus.PLANNING) {
            project.setStatus(ProjectStatus.ACTIVE);
        }

        Project saved = projectRepository.save(project);

        // Nur der Statuswechsel ist planungsrelevant. Eine reine Prozentaenderung feuert bei jedem
        // Abhaken — und TaskService publiziert dort ohnehin schon.
        if (saved.getStatus() != statusBefore) {
            eventPublisher.publishEvent(new ScheduleChangedEvent(this, saved.getUser().getId()));
        }
    }


    @Transactional
    public void recalculateProjectStats(Long projectId) {
        if (projectId != null) {
            updateProjectStatistics(projectId);
        }
    }

    /** Felder, die bestimmen OB und WIE VIEL Projektzeit der Scheduler platziert. */
    private String schedulingFingerprint(Project p) {
        return p.getStatus() + "|" + p.getWeeklySessionCount() + "|" + p.getSessionDurationMinutes()
                + "|" + p.getStartDate() + "|" + p.getTargetEndDate() + "|" + p.getActualEndDate();
    }

    /** 0 ist der legitime Opt-out ("keine Projektzeit einplanen"). */
    private Integer clampSessionCount(Integer value) {
        if (value == null) return null;
        return Math.max(0, Math.min(MAX_WEEKLY_SESSIONS, value));
    }

    private Integer clampSessionDuration(Integer value) {
        if (value == null) return null;
        return Math.max(MIN_SESSION_MINUTES, Math.min(MAX_SESSION_MINUTES, value));
    }
}
