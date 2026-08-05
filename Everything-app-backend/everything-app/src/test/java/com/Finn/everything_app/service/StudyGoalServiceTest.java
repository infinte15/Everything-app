package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.CourseRepository;
import com.Finn.everything_app.repository.StudyGoalRepository;
import com.Finn.everything_app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Der Kern dieser Klasse ist die Brücke in den Scheduler: ein Lernziel spiegelt sich in einen
 * ganz normalen Task, weil CP-SAT nur Tasks, Habits und Workouts kennt. Geprüft wird deshalb
 * vor allem, ob dieser Task die richtigen Zahlen trägt und ob er den Zustandswechseln des
 * Ziels folgt.
 */
@ExtendWith(MockitoExtension.class)
class StudyGoalServiceTest {

    @Mock StudyGoalRepository studyGoalRepository;
    @Mock CourseRepository courseRepository;
    @Mock UserRepository userRepository;
    @Mock TaskService taskService;

    @InjectMocks StudyGoalService service;

    private static final long USER = 1L;
    private static final long COURSE = 50L;

    private LocalDate monday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private User user() {
        User u = new User();
        u.setId(USER);
        return u;
    }

    private Course course() {
        Course c = new Course();
        c.setId(COURSE);
        c.setName("Analysis I");
        c.setColor("#3B82F6");
        return c;
    }

    /** Ein gespeichertes Ziel dieser Woche mit optionalem Brücken-Task. */
    private StudyGoal storedGoal(double goalHours, double logged, Task bridge) {
        StudyGoal goal = new StudyGoal();
        goal.setId(9L);
        goal.setUser(user());
        goal.setCourse(course());
        goal.setWeeklyGoalHours(goalHours);
        goal.setLoggedHours(logged);
        goal.setLoggedWeekStart(monday());
        goal.setTask(bridge);

        when(studyGoalRepository.findByIdAndUserId(9L, USER)).thenReturn(Optional.of(goal));
        // lenient, weil der Löschpfad nicht speichert.
        lenient().when(studyGoalRepository.save(goal)).thenReturn(goal);
        return goal;
    }

    private Task bridgeTask(long id, TaskStatus status) {
        Task t = new Task();
        t.setId(id);
        t.setUser(user());
        t.setStatus(status);
        return t;
    }

    private Task capturedCreatedTask() {
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).createTask(eq(USER), captor.capture());
        return captor.getValue();
    }

    private Task capturedUpdatedTask(long taskId) {
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskService).updateTask(eq(taskId), captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------

    @Test
    void einZielFremderNutzerWirdAbgewiesen() {
        when(studyGoalRepository.findByIdAndUserId(9L, 99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.logHours(99L, 9L, 1.0));
        verifyNoInteractions(taskService);
    }

    @Test
    void einNeuesZielLegtDenBrueckenTaskMitDenReststundenAn() {
        when(studyGoalRepository.existsByUserIdAndCourseId(USER, COURSE)).thenReturn(false);
        when(userRepository.findById(USER)).thenReturn(Optional.of(user()));
        when(courseRepository.findByIdAndUserId(COURSE, USER)).thenReturn(Optional.of(course()));
        when(studyGoalRepository.save(any(StudyGoal.class))).thenAnswer(i -> i.getArgument(0));
        when(taskService.createTask(eq(USER), any(Task.class))).thenAnswer(i -> {
            Task t = i.getArgument(1);
            t.setId(300L);
            return t;
        });

        StudyGoal incoming = new StudyGoal();
        incoming.setWeeklyGoalHours(5.0);

        StudyGoal created = service.createGoal(USER, COURSE, incoming);

        Task task = capturedCreatedTask();
        assertEquals("Lernen: Analysis I", task.getTitle());
        assertEquals(300, task.getEstimatedDurationMinutes(), "5 Stunden sind 300 Minuten");
        assertEquals(SpaceType.STUDY, task.getSpaceType());
        assertEquals("Lernziel", task.getCategory(),
                "der zusammengesetzte category-String der alten Brücke brach beim Umbenennen des Fachs");
        assertEquals(monday().atStartOfDay(), task.getNotBefore());
        assertEquals(monday().plusDays(7).atTime(23, 59), task.getDeadline());
        assertTrue(task.getSplittable(), "sonst legte der Solver einen einzigen 5-Stunden-Klotz");
        assertEquals(90, task.getMaxChunkMinutes());
        assertEquals(2, task.getMaxChunksPerDay(),
                "ohne Tagesgrenze legt der Solver die Bloecke direkt hintereinander");
        assertNotNull(created.getTask(), "die Zuordnung hängt am Fremdschlüssel, nicht an der Kategorie");
    }

    @Test
    void zweiZieleFuerDasselbeModulSindNichtErlaubt() {
        when(studyGoalRepository.existsByUserIdAndCourseId(USER, COURSE)).thenReturn(true);

        StudyGoal incoming = new StudyGoal();
        incoming.setWeeklyGoalHours(3.0);

        assertThrows(BadRequestException.class, () -> service.createGoal(USER, COURSE, incoming));
        verifyNoInteractions(taskService);
    }

    @Test
    void erfassteStundenVerkleinernDieRestdauer() {
        storedGoal(5.0, 0.0, bridgeTask(300L, TaskStatus.TODO));

        StudyGoal updated = service.logHours(USER, 9L, 2.0);

        assertEquals(2.0, updated.getLoggedHours());
        assertEquals(180, capturedUpdatedTask(300L).getEstimatedDurationMinutes(),
                "von 5 Stunden bleiben 3 übrig");
    }

    @Test
    void einErreichtesZielSchliesstDenBrueckenTaskAb() {
        storedGoal(5.0, 4.0, bridgeTask(300L, TaskStatus.TODO));

        service.logHours(USER, 9L, 1.0);

        verify(taskService).completeTask(300L);
        verify(taskService, never()).updateTask(anyLong(), any());
    }

    @Test
    void hochgesetzteStundenHolenDenAbgeschlossenenTaskZurueck() {
        storedGoal(5.0, 5.0, bridgeTask(300L, TaskStatus.COMPLETED));

        StudyGoal incoming = new StudyGoal();
        incoming.setWeeklyGoalHours(8.0);
        service.updateGoal(USER, 9L, incoming);

        Task patch = capturedUpdatedTask(300L);
        assertEquals(TaskStatus.TODO, patch.getStatus(),
                "ein abgeschlossener Task wird nicht mehr geplant, das Ziel bliebe unsichtbar");
        assertEquals(180, patch.getEstimatedDurationMinutes(), "8 Stunden Ziel minus 5 erfasste");
    }

    @Test
    void einZielAendernLaesstDieErfasstenStundenInRuhe() {
        // StudyGoal.loggedHours hat den Initialwert 0.0. Ein aus dem DTO gebautes Ziel trägt
        // ihn also immer mit — würde updateGoal ihn übernehmen, setzte jedes Anheben der
        // Zielstunden die erfassten Stunden stillschweigend auf null zurück.
        StudyGoal goal = storedGoal(5.0, 3.0, bridgeTask(300L, TaskStatus.TODO));

        StudyGoal incoming = new StudyGoal();
        incoming.setWeeklyGoalHours(6.0);
        service.updateGoal(USER, 9L, incoming);

        assertEquals(3.0, goal.getLoggedHours(), "die erfassten Stunden gehören POST /log");
        assertEquals(180, capturedUpdatedTask(300L).getEstimatedDurationMinutes());
    }

    @Test
    void eineNeueWocheSetztDieErfasstenStundenZurueck() {
        StudyGoal goal = new StudyGoal();
        goal.setId(9L);
        goal.setUser(user());
        goal.setCourse(course());
        goal.setWeeklyGoalHours(5.0);
        goal.setLoggedHours(5.0);
        goal.setLoggedWeekStart(monday().minusWeeks(1));
        goal.setTask(bridgeTask(300L, TaskStatus.COMPLETED));

        when(studyGoalRepository.findByUserIdOrderByIdAsc(USER)).thenReturn(List.of(goal));

        List<StudyGoal> goals = service.getGoals(USER);

        assertEquals(0.0, goals.get(0).getLoggedHours(), "letzte Woche zählt nicht mehr mit");
        assertEquals(monday(), goals.get(0).getLoggedWeekStart());
    }

    @Test
    void loeschenNimmtDenBrueckenTaskMit() {
        StudyGoal goal = storedGoal(5.0, 0.0, bridgeTask(300L, TaskStatus.TODO));

        service.deleteGoal(USER, 9L);

        assertNull(goal.getTask(), "erst die Verknüpfung lösen, sonst zeigt task_id ins Leere");
        verify(studyGoalRepository).delete(goal);
        verify(taskService).deleteTask(300L);
    }

    @Test
    void mitDemModulVerschwindenSeineZieleUndDerenTasks() {
        StudyGoal goal = new StudyGoal();
        goal.setId(9L);
        goal.setTask(bridgeTask(300L, TaskStatus.TODO));
        when(studyGoalRepository.findByCourseId(COURSE)).thenReturn(List.of(goal));

        service.deleteGoalsOfCourse(COURSE);

        assertNull(goal.getTask());
        verify(studyGoalRepository).deleteAll(List.of(goal));
        verify(taskService).deleteTask(300L);
    }
}
