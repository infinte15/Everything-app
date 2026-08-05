package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Die Projekt-Verknuepfung war komplett tot: TaskMapper.toEntity hat den Fremdschluessel nie
 * gesetzt, und keine Task-Mutation hat den Projektfortschritt nachgerechnet.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock UserService userService;
    @Mock CalendarEventRepository calendarEventRepository;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectService projectService;

    @InjectMocks
    TaskService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        lenient().when(userService.findById(1L)).thenReturn(user);
        lenient().when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createTaskVerknuepftDasProjektUndRechnetNeu() {
        Project project = project(10L);
        when(projectRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(project));

        Task created = service.createTask(1L, taskWithProjectRef(10L));

        assertSame(project, created.getProject(), "ohne diese Zuordnung bleibt project_id NULL");
        verify(projectService).recalculateProjectStats(10L);
    }

    @Test
    void fremdesProjektImBodyWirdAbgelehnt() {
        when(projectRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createTask(1L, taskWithProjectRef(99L)));
    }

    @Test
    void updateTaskMitProjektwechselRechnetAltesUndNeuesProjektNeu() {
        Task existing = storedTask(5L, project(10L));
        Project neu = project(20L);
        when(projectRepository.findByIdAndUserId(20L, 1L)).thenReturn(Optional.of(neu));

        service.updateTask(5L, taskWithProjectRef(20L));

        assertSame(neu, existing.getProject());
        verify(projectService).recalculateProjectStats(10L);   // altes Projekt verliert eine Aufgabe
        verify(projectService).recalculateProjectStats(20L);
    }

    @Test
    void patchOhneProjectIdLaesstDieVerknuepfungStehen() {
        Project project = project(10L);
        Task existing = storedTask(5L, project);

        // So patcht CalendarEventService.creditBlock: ein nacktes Task-Objekt mit nur zwei Feldern.
        Task patch = new Task();
        patch.setCompletedMinutes(30);
        service.updateTask(5L, patch);

        assertSame(project, existing.getProject(),
                "null bedeutet unveraendert — sonst verliert jede Gutschrift die Projektzuordnung");
        verify(projectService).recalculateProjectStats(10L);
    }

    @Test
    void completeTaskAktualisiertDenProjektfortschritt() {
        storedTask(5L, project(10L));

        Task completed = service.completeTask(5L);

        assertEquals(TaskStatus.COMPLETED, completed.getStatus());
        verify(projectService).recalculateProjectStats(10L);
    }

    @Test
    void deleteTaskAktualisiertDenProjektfortschritt() {
        Task task = storedTask(5L, project(10L));
        when(calendarEventRepository.findByRelatedTaskId(5L)).thenReturn(List.of());

        service.deleteTask(5L);

        verify(taskRepository).deleteById(5L);
        // Nach dem Loeschen — die ID wird vorher gemerkt, sonst ist das Projekt nicht mehr lesbar.
        verify(projectService).recalculateProjectStats(10L);
        assertNotNull(task);
    }

    @Test
    void assignToProjectEntkoppeltMitNull() {
        Task existing = storedTask(5L, project(10L));

        service.assignToProject(5L, 1L, null);

        assertNull(existing.getProject(), "null muss hier 'keinem Projekt mehr' bedeuten");
        verify(projectService).recalculateProjectStats(10L);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Task storedTask(Long id, Project project) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("Aufgabe");
        t.setStatus(TaskStatus.TODO);
        t.setUser(user);
        t.setProject(project);
        when(taskRepository.findById(id)).thenReturn(Optional.of(t));
        return t;
    }

    /** So sieht das Ergebnis von TaskMapper.toEntity aus: ein Projekt-Stub mit nur der ID. */
    private Task taskWithProjectRef(Long projectId) {
        Task t = new Task();
        t.setTitle("Aufgabe");
        Project stub = new Project();
        stub.setId(projectId);
        t.setProject(stub);
        return t;
    }

    private Project project(Long id) {
        Project p = new Project();
        p.setId(id);
        p.setUser(user);
        return p;
    }
}
