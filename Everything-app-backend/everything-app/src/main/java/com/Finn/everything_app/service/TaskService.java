package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.Project;
import com.Finn.everything_app.model.Task;
import com.Finn.everything_app.model.TaskStatus;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.CalendarEventRepository;
import com.Finn.everything_app.repository.ProjectRepository;
import com.Finn.everything_app.repository.TaskRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.Finn.everything_app.event.ScheduleChangedEvent;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final UserService userService;
    private final CalendarEventRepository calendarEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;

    public List<Task> getAllUserTasks(Long userId) {
        return taskRepository.findByUserId(userId);
    }

    public List<Task> getTasksByStatus(Long userId, TaskStatus status) {
        return taskRepository.findByUserIdAndStatus(userId, status);
    }

    public List<Task> getUnscheduledTasks(Long userId) {
        return taskRepository.findTasksForAutoScheduling(userId);
    }

    /**
     * Quelle für den Scheduler. Anders als {@link #getUnscheduledTasks} werden Tasks mit einem
     * gepinnten Termin NICHT komplett ausgeschlossen: seit dem Chunking soll das Pinnen eines
     * einzelnen Blocks die übrigen Blöcke weiterhin beweglich lassen. Die bereits gepinnte Zeit
     * wird im Scheduler von der Restdauer abgezogen.
     */
    public List<Task> getSchedulableTasks(Long userId) {
        return taskRepository.findSchedulableTasks(userId);
    }

    public Task getTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task nicht gefunden"));
    }

    @Transactional
    public Task createTask(Long userId, Task task) {
        User user = userService.findById(userId);

        task.setUser(user);
        task.setCreatedAt(LocalDateTime.now());
        task.setStatus(TaskStatus.TODO);

        if (task.getTitle() == null || task.getTitle().trim().isEmpty()) {
            throw new RuntimeException("Task muss einen Titel haben");
        }

        if (task.getPriority() == null) {
            task.setPriority(3); // Standard
        }

        if (task.getEstimatedDurationMinutes() == null) {
            task.setEstimatedDurationMinutes(60); // Standard
        }

        task.setProject(resolveProject(userId, task.getProject()));

        Task savedTask = taskRepository.save(task);
        recalcProject(savedTask.getProject());
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return savedTask;
    }

    /**
     * Loest den vom Mapper gesetzten Id-Stub in ein echtes Projekt auf — besitzgeprueft, damit
     * eine fremde projectId im Request-Body keine Aufgabe ueber Nutzergrenzen hinweg verknuepfen
     * kann. {@code null} bedeutet "keine Zuordnung".
     */
    private Project resolveProject(Long userId, Project stub) {
        if (stub == null || stub.getId() == null) return null;
        return projectRepository.findByIdAndUserId(stub.getId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Projekt", "id", stub.getId()));
    }

    private void recalcProject(Project project) {
        if (project != null && project.getId() != null) {
            projectService.recalculateProjectStats(project.getId());
        }
    }

    @Transactional
    public Task updateTask(Long taskId, Task updatedTask) {
        Task existing = getTaskById(taskId);
        Long ownerId = existing.getUser().getId();
        // Vor dem Patchen merken: wandert die Aufgabe in ein anderes Projekt, muessen BEIDE
        // Fortschrittszaehler neu gerechnet werden.
        Long oldProjectId = existing.getProject() != null ? existing.getProject().getId() : null;

        if (updatedTask.getTitle() != null) {
            existing.setTitle(updatedTask.getTitle());
        }
        if (updatedTask.getDescription() != null) {
            existing.setDescription(updatedTask.getDescription());
        }
        if (updatedTask.getPriority() != null) {
            existing.setPriority(updatedTask.getPriority());
        }
        if (updatedTask.getDeadline() != null) {
            existing.setDeadline(updatedTask.getDeadline());
        }
        if (updatedTask.getEstimatedDurationMinutes() != null) {
            existing.setEstimatedDurationMinutes(updatedTask.getEstimatedDurationMinutes());
        }
        if (updatedTask.getStatus() != null) {
            existing.setStatus(updatedTask.getStatus());
        }
        if (updatedTask.getCategory() != null) {
            existing.setCategory(updatedTask.getCategory());
        }
        if (updatedTask.getMinChunkMinutes() != null) {
            existing.setMinChunkMinutes(updatedTask.getMinChunkMinutes());
        }
        if (updatedTask.getMaxChunkMinutes() != null) {
            existing.setMaxChunkMinutes(updatedTask.getMaxChunkMinutes());
        }
        if (updatedTask.getSplittable() != null) {
            existing.setSplittable(updatedTask.getSplittable());
        }
        if (updatedTask.getMaxChunksPerDay() != null) {
            existing.setMaxChunksPerDay(updatedTask.getMaxChunksPerDay());
        }
        if (updatedTask.getCompletedMinutes() != null) {
            existing.setCompletedMinutes(updatedTask.getCompletedMinutes());
        }
        if (updatedTask.getNotBefore() != null) {
            existing.setNotBefore(updatedTask.getNotBefore());
        }
        // "null = unveraendert" gilt auch hier: CalendarEventService.creditBlock patcht mit einem
        // nackten new Task(), das nur Minuten und Status traegt — die Projektzuordnung darf dabei
        // nicht verloren gehen. Zum Entkoppeln gibt es assignToProject(..., null).
        if (updatedTask.getProject() != null && updatedTask.getProject().getId() != null) {
            existing.setProject(resolveProject(ownerId, updatedTask.getProject()));
        }

        existing.setUpdatedAt(LocalDateTime.now());

        Task savedTask = taskRepository.save(existing);
        recalcProjects(oldProjectId, savedTask.getProject());
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, ownerId));
        return savedTask;
    }

    /** Rechnet altes und neues Projekt neu — beim Wechsel verlieren beide Seiten eine Aufgabe. */
    private void recalcProjects(Long oldProjectId, Project newProject) {
        Long newProjectId = newProject != null ? newProject.getId() : null;
        if (oldProjectId != null && !oldProjectId.equals(newProjectId)) {
            projectService.recalculateProjectStats(oldProjectId);
        }
        if (newProjectId != null) {
            projectService.recalculateProjectStats(newProjectId);
        }
    }

    /**
     * Ordnet eine Aufgabe einem Projekt zu oder entkoppelt sie ({@code projectId == null}).
     * Eigener Endpunkt, weil die Patch-Konvention von {@link #updateTask} "leeren" nicht
     * ausdruecken kann — dort bedeutet null "unveraendert".
     */
    @Transactional
    public Task assignToProject(Long taskId, Long userId, Long projectId) {
        Task task = getTaskById(taskId);
        Long oldProjectId = task.getProject() != null ? task.getProject().getId() : null;

        Project target = null;
        if (projectId != null) {
            target = projectRepository.findByIdAndUserId(projectId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Projekt", "id", projectId));
        }
        task.setProject(target);
        task.setUpdatedAt(LocalDateTime.now());

        Task saved = taskRepository.save(task);
        recalcProjects(oldProjectId, saved.getProject());
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return saved;
    }

    @Transactional
    public Task completeTask(Long taskId) {
        Task task = getTaskById(taskId);
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        Task savedTask = taskRepository.save(task);
        // Der wichtigste Treiber des Projektfortschritts.
        recalcProject(savedTask.getProject());
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, task.getUser().getId()));
        return savedTask;
    }

    @Transactional
    public void deleteTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task nicht gefunden"));
        Long userId = task.getUser().getId();
        Long projectId = task.getProject() != null ? task.getProject().getId() : null;
        // Any CalendarEvent generated from this task (scheduler-placed or manually created)
        // must go first — otherwise deleting the task violates the related_task_id foreign
        // key and the whole delete 500s, leaving the task (and its calendar event) in place.
        calendarEventRepository.deleteAll(calendarEventRepository.findByRelatedTaskId(taskId));
        taskRepository.deleteById(taskId);
        if (projectId != null) {
            projectService.recalculateProjectStats(projectId);
        }
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
    }

    @Transactional
    public void scheduleTask(Long taskId, LocalDateTime startTime, LocalDateTime endTime) {
        Task task = getTaskById(taskId);
        task.setScheduledStartTime(startTime);
        task.setScheduledEndTime(endTime);
        taskRepository.save(task);
    }

    /**
     * Löscht die geplanten Zeiten eines Tasks. Wird aufgerufen, wenn der Scheduler ihn in diesem
     * Lauf nicht mehr unterbringen konnte — sonst bleiben Geisterwerte aus einem früheren Lauf
     * stehen und die Task-Liste zeigt eine Zeit an, zu der nichts im Kalender steht.
     */
    @Transactional
    public void clearSchedule(Long taskId) {
        Task task = getTaskById(taskId);
        task.setScheduledStartTime(null);
        task.setScheduledEndTime(null);
        taskRepository.save(task);
    }

    public List<Task> getTasksForScheduling(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return taskRepository.findTasksForScheduling(userId, TaskStatus.TODO, startDate, endDate);
    }
}
