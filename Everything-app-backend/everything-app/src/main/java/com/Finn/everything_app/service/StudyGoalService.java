package com.Finn.everything_app.service;

import com.Finn.everything_app.exception.BadRequestException;
import com.Finn.everything_app.exception.ResourceNotFoundException;
import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Wöchentliche Lernziele je Modul.
 *
 * Der eigentliche Kniff steckt in {@link #syncTask}: der SmartScheduler kennt nur Tasks,
 * Habits und Workouts. Statt CP-SAT einen weiteren Itemtyp beizubringen (mit eigenen
 * Gewichten, Tagesfenstern, Drop-Penalty und At-Risk-Abbildung), spiegelt sich ein Lernziel
 * in einen ganz normalen Task. Der wird geplant wie jeder andere, weicht Vorlesungen aus und
 * landet über den bestehenden Weg im Kalender.
 */
@Service
@RequiredArgsConstructor
public class StudyGoalService {

    private final StudyGoalRepository studyGoalRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;

    /** Kategorie des Brücken-Tasks; daran erkennt die Aufgabenliste ihn wieder. */
    static final String BRIDGE_CATEGORY = "Lernziel";

    /**
     * Größter Einzelblock. 90 statt der früheren 120 Minuten: aus 6 Stunden werden damit vier
     * Blöcke statt drei — und vier Blöcke passen bei höchstens zwei pro Tag zwingend auf
     * mindestens zwei Tage.
     */
    private static final int MAX_CHUNK_MINUTES = 90;

    /**
     * Höchstens zwei Sitzungen am Tag. Ohne diese Grenze legt der Solver alle Blöcke direkt
     * hintereinander — die Stunden waren also längst aufgeteilt, sahen im Kalender aber aus
     * wie ein einziger langer Balken.
     */
    private static final int MAX_CHUNKS_PER_DAY = 2;

    @Transactional
    public List<StudyGoal> getGoals(Long userId) {
        List<StudyGoal> goals = studyGoalRepository.findByUserIdOrderByIdAsc(userId);
        goals.forEach(this::rollOver);
        return goals;
    }

    @Transactional
    public StudyGoal createGoal(Long userId, Long courseId, StudyGoal incoming) {
        if (courseId == null) throw new BadRequestException("Ein Lernziel braucht ein Modul");
        if (studyGoalRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new BadRequestException("Für dieses Modul gibt es bereits ein Lernziel");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User nicht gefunden"));
        Course course = courseRepository.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Modul nicht gefunden"));

        incoming.setUser(user);
        incoming.setCourse(course);
        incoming.setLoggedWeekStart(mondayOfThisWeek());
        if (incoming.getLoggedHours() == null) incoming.setLoggedHours(0.0);

        StudyGoal saved = studyGoalRepository.save(incoming);
        syncTask(saved);
        return studyGoalRepository.save(saved);
    }

    @Transactional
    public StudyGoal updateGoal(Long userId, Long id, StudyGoal incoming) {
        StudyGoal goal = requireGoal(userId, id);
        rollOver(goal);

        if (incoming.getWeeklyGoalHours() != null) goal.setWeeklyGoalHours(incoming.getWeeklyGoalHours());
        if (incoming.getEmoji() != null)           goal.setEmoji(incoming.getEmoji());
        // loggedHours bewusst NICHT aus dem Patch: das Feld hat den Initialwert 0.0, ein aus
        // dem DTO gebautes Ziel trägt ihn also immer mit — und jedes Ändern der Zielstunden
        // hätte die erfassten Stunden stillschweigend auf null zurückgesetzt. Erfasst wird
        // über POST /goals/{id}/log.

        syncTask(goal);
        return studyGoalRepository.save(goal);
    }

    @Transactional
    public StudyGoal logHours(Long userId, Long id, double hours) {
        if (hours <= 0) throw new BadRequestException("Stundenzahl muss größer als 0 sein");

        return applyLoggedDelta(requireGoal(userId, id), hours);
    }

    /**
     * Verrechnet [delta] Stunden mit dem Ziel — auch negativ.
     *
     * Getrennt von {@link #logHours}, damit dessen Ablehnung von {@code hours <= 0} für die
     * Handeingabe bestehen bleibt: ein zurückgenommener Kalenderblock muss abziehen können,
     * ein Dialog mit „0 Stunden" nicht.
     */
    @Transactional
    public StudyGoal applyLoggedDelta(StudyGoal goal, double delta) {
        rollOver(goal);
        goal.setLoggedHours(Math.max(0.0, goal.getLoggedHours() + delta));

        syncTask(goal);
        return studyGoalRepository.save(goal);
    }

    @Transactional
    public void deleteGoal(Long userId, Long id) {
        StudyGoal goal = requireGoal(userId, id);
        Task bridge = goal.getTask();

        // Erst die Verknüpfung lösen, dann löschen: sonst zeigt study_goals.task_id noch auf
        // die Zeile, die TaskService gerade wegräumt.
        goal.setTask(null);
        studyGoalRepository.delete(goal);
        if (bridge != null) taskService.deleteTask(bridge.getId());
    }

    /** Räumt die Ziele eines Moduls samt Brücken-Tasks weg. Aufgerufen vom CourseService. */
    @Transactional
    public void deleteGoalsOfCourse(Long courseId) {
        List<StudyGoal> goals = studyGoalRepository.findByCourseId(courseId);
        List<Long> bridgeIds = goals.stream()
                .map(StudyGoal::getTask)
                .filter(t -> t != null)
                .map(Task::getId)
                .toList();

        goals.forEach(g -> g.setTask(null));
        studyGoalRepository.deleteAll(goals);
        bridgeIds.forEach(taskService::deleteTask);
    }

    // ------------------------------------------------------------------
    // Intern
    // ------------------------------------------------------------------

    private StudyGoal requireGoal(Long userId, Long id) {
        return studyGoalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Lernziel nicht gefunden"));
    }

    /**
     * Neue Woche heißt: die erfassten Stunden fangen wieder bei null an.
     *
     * Bewusst hier und nicht in {@code @PostLoad} — eine Entität, die sich beim Laden selbst
     * ändert, ist beim Debuggen die Hölle, und Hibernate schriebe die Änderung beim nächsten
     * Flush ungefragt zurück.
     */
    private void rollOver(StudyGoal goal) {
        LocalDate monday = mondayOfThisWeek();
        if (!monday.equals(goal.getLoggedWeekStart())) {
            goal.setLoggedWeekStart(monday);
            goal.setLoggedHours(0.0);
        }
    }

    /**
     * Spiegelt das Ziel in seinen Brücken-Task.
     *
     * Die Zuordnung hängt am Fremdschlüssel {@code study_goals.task_id}, nicht mehr an einem
     * zusammengesetzten category-String wie {@code "study-goal:<Fach>:<Woche>"} — der brach,
     * sobald der Nutzer das Fach umbenannte, und ließ eine Aufgabenleiche zurück.
     */
    private void syncTask(StudyGoal goal) {
        double remaining = goal.getRemainingHours();
        Task bridge = goal.getTask();

        if (remaining <= 0) {
            // Ziel erreicht: der Task ist erledigt, und die Neuplanung räumt seine Blöcke weg.
            if (bridge != null && bridge.getStatus() != TaskStatus.COMPLETED) {
                taskService.completeTask(bridge.getId());
            }
            return;
        }

        LocalDate monday = mondayOfThisWeek();
        int minutes = (int) Math.round(remaining * 60);

        if (bridge == null) {
            Task task = new Task();
            applyBridgeFields(task, goal, monday, minutes);
            goal.setTask(taskService.createTask(goal.getUser().getId(), task));
            return;
        }

        Task patch = new Task();
        applyBridgeFields(patch, goal, monday, minutes);
        // Zurück auf offen, falls die Stunden nachträglich hochgesetzt wurden: ein
        // abgeschlossener Task wird nicht mehr geplant und das Ziel bliebe unsichtbar.
        patch.setStatus(TaskStatus.TODO);
        taskService.updateTask(bridge.getId(), patch);
    }

    private void applyBridgeFields(Task task, StudyGoal goal, LocalDate monday, int minutes) {
        task.setTitle("Lernen: " + goal.getCourse().getName());
        task.setCategory(BRIDGE_CATEGORY);
        task.setSpaceType(SpaceType.STUDY);
        task.setPriority(3);
        task.setEstimatedDurationMinutes(minutes);
        task.setDeadline(monday.plusDays(7).atTime(23, 59));
        task.setNotBefore(monday.atStartOfDay());
        task.setSplittable(true);
        task.setMaxChunkMinutes(MAX_CHUNK_MINUTES);
        task.setMaxChunksPerDay(MAX_CHUNKS_PER_DAY);
    }

    private LocalDate mondayOfThisWeek() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }
}
