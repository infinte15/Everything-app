package com.Finn.everything_app.service;

import com.Finn.everything_app.event.ScheduleChangedEvent;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Der Projektfortschritt wird ausschliesslich aus den verknuepften Aufgaben abgeleitet —
 * updateProjectStatistics war vorher toter Code, deshalb blieb er dauerhaft bei 0 %.
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @Mock TaskRepository taskRepository;
    @Mock CalendarEventRepository calendarEventRepository;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ProjectService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        lenient().when(projectRepository.save(any(Project.class)))
                 .thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------
    // Fortschritt aus Aufgaben
    // ------------------------------------------------------------------

    @Test
    void recalcRechnetProzentAusTasks() {
        Project project = storedProject(10L, ProjectStatus.ACTIVE);
        when(taskRepository.findByProjectId(10L))
                .thenReturn(List.of(task(TaskStatus.COMPLETED), task(TaskStatus.TODO),
                                    task(TaskStatus.TODO), task(TaskStatus.COMPLETED)));

        service.recalculateProjectStats(10L);

        assertEquals(4, project.getTasksTotal());
        assertEquals(2, project.getTasksCompleted());
        assertEquals(50, project.getCompletionPercentage());
    }

    @Test
    void ohneTasksFaelltDerFortschrittAufNull() {
        Project project = storedProject(10L, ProjectStatus.ACTIVE);
        project.setCompletionPercentage(100);
        project.setTasksTotal(3);
        project.setTasksCompleted(3);
        when(taskRepository.findByProjectId(10L)).thenReturn(List.of());

        service.recalculateProjectStats(10L);

        assertEquals(0, project.getCompletionPercentage(),
                "nach dem Loeschen der letzten Aufgabe darf keine alte 100 stehenbleiben");
        assertEquals(0, project.getTasksTotal());
    }

    @Test
    void hundertProzentSetztStatusUndActualEndDate() {
        Project project = storedProject(10L, ProjectStatus.ACTIVE);
        when(taskRepository.findByProjectId(10L)).thenReturn(List.of(task(TaskStatus.COMPLETED)));

        service.recalculateProjectStats(10L);

        assertEquals(ProjectStatus.COMPLETED, project.getStatus());
        assertEquals(LocalDate.now(), project.getActualEndDate());
        // Der Status entscheidet, ob das Projekt noch Projektzeit bekommt — also neu planen.
        verify(eventPublisher).publishEvent(any(ScheduleChangedEvent.class));
    }

    @Test
    void neueAufgabeHebtDenAbschlussWiederAuf() {
        Project project = storedProject(10L, ProjectStatus.COMPLETED);
        project.setActualEndDate(LocalDate.now().minusDays(3));
        when(taskRepository.findByProjectId(10L))
                .thenReturn(List.of(task(TaskStatus.COMPLETED), task(TaskStatus.TODO)));

        service.recalculateProjectStats(10L);

        assertEquals(ProjectStatus.ACTIVE, project.getStatus(),
                "sonst bekaeme das Projekt nie wieder Projektzeit im Kalender");
        assertNull(project.getActualEndDate());
    }

    @Test
    void reineProzentaenderungLoestKeineNeuplanungAus() {
        storedProject(10L, ProjectStatus.ACTIVE);
        when(taskRepository.findByProjectId(10L))
                .thenReturn(List.of(task(TaskStatus.COMPLETED), task(TaskStatus.TODO)));

        service.recalculateProjectStats(10L);

        // 50 % aendert den Status nicht — TaskService publiziert beim Abhaken ohnehin schon.
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ------------------------------------------------------------------
    // Besitz, Loeschen, Neuplanung
    // ------------------------------------------------------------------

    @Test
    void fremdesProjektWirftResourceNotFound() {
        when(projectRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getProjectById(99L, 1L));
    }

    @Test
    void deleteEntkoppeltTasksUndLoeschtProjektbloecke() {
        Project project = ownedProject(10L, ProjectStatus.ACTIVE);
        when(projectRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(project));
        CalendarEvent block = new CalendarEvent();
        when(calendarEventRepository.findByRelatedProjectId(10L)).thenReturn(List.of(block));

        service.deleteProject(10L, 1L);

        // Reihenfolge zaehlt: erst die Bloecke, sonst verletzt das Delete related_project_id.
        InOrderHelper.verifyDeleteOrder(calendarEventRepository, taskRepository, projectRepository, project, block);
        verify(eventPublisher).publishEvent(any(ScheduleChangedEvent.class));
    }

    @Test
    void umbenennenLoestKeineNeuplanungAus() {
        Project project = ownedProject(10L, ProjectStatus.ACTIVE);
        when(projectRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(project));

        Project patch = patch();
        patch.setName("Neuer Name");
        service.updateProject(10L, 1L, patch);

        assertEquals("Neuer Name", project.getName());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void geaenderteSessionAnzahlLoestNeuplanungAus() {
        Project project = ownedProject(10L, ProjectStatus.ACTIVE);
        when(projectRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(project));

        Project patch = patch();
        patch.setWeeklySessionCount(4);
        service.updateProject(10L, 1L, patch);

        assertEquals(4, project.getWeeklySessionCount());
        verify(eventPublisher).publishEvent(any(ScheduleChangedEvent.class));
    }

    @Test
    void absurdesWochenpensumWirdGedeckelt() {
        Project project = ownedProject(10L, ProjectStatus.ACTIVE);
        when(projectRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(project));

        Project patch = patch();
        patch.setWeeklySessionCount(999);
        patch.setSessionDurationMinutes(5000);
        service.updateProject(10L, 1L, patch);

        assertEquals(14, project.getWeeklySessionCount(), "sonst explodiert das Solver-Modell");
        assertEquals(480, project.getSessionDurationMinutes());
    }

    @Test
    void completionPercentageVomClientWirdIgnoriert() {
        Project project = ownedProject(10L, ProjectStatus.ACTIVE);
        project.setCompletionPercentage(20);
        when(projectRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(project));

        Project patch = patch();
        patch.setCompletionPercentage(100);
        service.updateProject(10L, 1L, patch);

        assertEquals(20, project.getCompletionPercentage(),
                "der Fortschritt ist abgeleitet, nicht vom Client gesetzt");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Projekt, das ueber findById (interner Pfad) gefunden wird. */
    private Project storedProject(Long id, ProjectStatus status) {
        Project p = ownedProject(id, status);
        when(projectRepository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private Project ownedProject(Long id, ProjectStatus status) {
        Project p = new Project();
        p.setId(id);
        p.setName("Projekt " + id);
        p.setStatus(status);
        p.setUser(user);
        p.setWeeklySessionCount(2);
        p.setSessionDurationMinutes(60);
        return p;
    }

    /**
     * Patch-Objekt so, wie es der ProjectMapper baut: alle nicht gesendeten Felder sind null.
     * Ein blankes {@code new Project()} traegt dagegen die Entity-Defaults (PLANNING, 1x, 60 Min)
     * und wuerde ein reines Umbenennen als Aenderung des Wochenpensums aussehen lassen.
     */
    private Project patch() {
        Project p = new Project();
        p.setStatus(null);
        p.setWeeklySessionCount(null);
        p.setSessionDurationMinutes(null);
        p.setCompletionPercentage(null);
        return p;
    }

    private Task task(TaskStatus status) {
        Task t = new Task();
        t.setStatus(status);
        return t;
    }

    /** Ausgelagert, damit der Test lesbar bleibt und InOrder nicht die Assertions ueberwuchert. */
    private static final class InOrderHelper {
        static void verifyDeleteOrder(CalendarEventRepository events, TaskRepository tasks,
                                      ProjectRepository projects, Project project, CalendarEvent block) {
            var inOrder = inOrder(events, tasks, projects);
            inOrder.verify(events).deleteAll(List.of(block));
            inOrder.verify(tasks).detachFromProject(project.getId());
            inOrder.verify(projects).delete(project);
        }
    }
}
