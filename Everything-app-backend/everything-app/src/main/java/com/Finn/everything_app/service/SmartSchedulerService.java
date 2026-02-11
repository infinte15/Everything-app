package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartSchedulerService {

    private final TaskRepository taskRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final HabitRepository habitRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final UserService userService;
    private final CalendarEventService calendarEventService;
    private final TaskService taskService;

    @Transactional
    public ScheduleResult generateOptimalSchedule(Long userId, LocalDate startDate, LocalDate endDate){
        log.info("Generiere Schedule für User {} von {} bis {}", userId, startDate, endDate);

        UserPreferences prefs = userService.getUserPreferences(userId);                                                                                                         //User Präferenzen laden
        calendarEventService.clearScheduledEvents(userId);                                                                                                                      //Kalender clearen
        ScheduleInput input = collectScheduleInput(userId, startDate, endDate);                                                                                                 //Daten holen
        List<TimeSlot> availableSlots = generateAvailableTimeSlots(userId, startDate, endDate, prefs, input.getFixedEvents());                                                  //Zeitslots generieren
        log.info("{} verfügbare Zeitslots gefunden", availableSlots.size());
        List<Task> prioritzedTasks = prioritizeTasks(input.getTasks(), prefs);                                                                                                  //Tasks priosieren
        List<ScheduledItem> scheduledTasks = scheduleTasksToSlots(prioritzedTasks, availableSlots, prefs);                                                                      //Tasks einplanen
        List<ScheduledItem> scheduledHabits = scheduleRecurringActivities(input.getHabits(), input.getWorkouts(), availableSlots, prefs);                                       //Habits einplanen
        saveScheduleToDatabase(userId, scheduledTasks, scheduledHabits);                                                                                                        //In DB speichern

        ScheduleResult result = new ScheduleResult();                                                                                                                           //Ergebnis erstellen
        result.setScheduledTasks(scheduledTasks);
        result.setScheduledHabits(scheduledHabits);
        result.setUnscheduledTasks(findUnscheduledTasks(prioritzedTasks, scheduledTasks));
        result.setTotalTasksScheduled(scheduledTasks.size());
        result.setTotalHoursScheduled(calculateTotalHours(scheduledTasks, scheduledHabits));

        log.info("Schedule generiert: {} Tasks, {} Habits eingeplant, {} Tasks nicht eingeplant", scheduledTasks.size(), scheduledHabits.size(), result.getUnscheduledTasks().size());

        return result;
    }


    //Input Daten sammeln
    private ScheduleInput collectScheduleInput(Long userId, LocalDate startDate, LocalDate endDate){
        ScheduleInput input = new ScheduleInput();

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23,59,59);

        input.setTasks(taskService.getUnscheduledTasks(userId));                                                                                                                //Uneingeplante Tasks
        input.setFixedEvents(calendarEventService.getFixedEvents(userId, start, end));                                                                                          //Fixe Events
        input.setHabits(habitRepository.findActiveHabits(userId,startDate));                                                                                                    //Habits
        input.setWorkouts(workoutSessionRepository.findByUserIdAndScheduledDateTimeBetween(userId, start, end));                                                                //Workouts
        input.setCourseSchedules(courseScheduleRepository.findByUserId(userId));                                                                                                //Kurse

        log.debug("Input gesammelt: {} Tasks, {} fixe Events, {} Habits, {} Workouts, {} Kurse",
                input.getTasks().size(), input.getFixedEvents().size(),
                input.getHabits().size(), input.getWorkouts().size(), input.getCourseSchedules().size());


        return input;
    }

    //Timeslots generieren
    private List<TimeSlot> generateAvailableTimeSlots(Long userId, LocalDate startDate, LocalDate endDate, UserPreferences prefs, List<CalendarEvent> fixedEvents) {
        List<TimeSlot> slots = new ArrayList<>();
        for(LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)){
            List<TimeSlot> daySlots = generateDaySlots(date, prefs, fixedEvents);
            slots.addAll(daySlots);
        }

        return slots;
    }
    //Timeslots für einzelne Tage
    private List<TimeSlot> generateDaySlots(
            LocalDate date,
            UserPreferences prefs,
            List<CalendarEvent> fixedEvents) {

        List<TimeSlot> slots = new ArrayList<>();

        LocalTime workStart = prefs.getWorkdayStart() != null
                ? prefs.getWorkdayStart()
                : LocalTime.of(8, 0);
        LocalTime workEnd = prefs.getWorkdayEnd() != null
                ? prefs.getWorkdayEnd()
                : LocalTime.of(22, 0);

        LocalDateTime currentTime = date.atTime(workStart);
        LocalDateTime endOfDay = date.atTime(workEnd);

        List<CalendarEvent> dayEvents = fixedEvents.stream()
                .filter(e -> e.getStartTime().toLocalDate().equals(date))
                .sorted(Comparator.comparing(CalendarEvent::getStartTime))
                .toList();

        for (CalendarEvent event : dayEvents) {
            // Slot vor dem Event
            if (currentTime.isBefore(event.getStartTime())) {
                long durationMinutes = ChronoUnit.MINUTES.between(currentTime, event.getStartTime());

                if (durationMinutes >= 10) {
                    TimeSlot slot = new TimeSlot();
                    slot.setStart(currentTime);
                    slot.setEnd(event.getStartTime());
                    slot.setDuration((int) durationMinutes);
                    slot.setDate(date);
                    slots.add(slot);
                }
            }

            currentTime = event.getEndTime();
        }

        if (currentTime.isBefore(endOfDay)) {
            long durationMinutes = ChronoUnit.MINUTES.between(currentTime, endOfDay);

            if (durationMinutes >= 10) {
                TimeSlot slot = new TimeSlot();
                slot.setStart(currentTime);
                slot.setEnd(endOfDay);
                slot.setDuration((int) durationMinutes);
                slot.setDate(date);
                slots.add(slot);
            }
        }

        return slots;
    }

    //Tasks priorisieren
    private List<Task> prioritizeTasks(List<Task> tasks, UserPreferences prefs) {
        return tasks.stream()
                .sorted((t1, t2) -> {
                    //Überfällige Tasks
                    boolean t1Overdue = t1.getDeadline() != null &&
                            t1.getDeadline().isBefore(LocalDateTime.now());
                    boolean t2Overdue = t2.getDeadline() != null &&
                            t2.getDeadline().isBefore(LocalDateTime.now());

                    if (t1Overdue != t2Overdue) {
                        return t1Overdue ? -1 : 1;
                    }

                    //Priorität
                    int priority1 = t1.getPriority() != null ? t1.getPriority() : 3;
                    int priority2 = t2.getPriority() != null ? t2.getPriority() : 3;
                    int priorityCompare = Integer.compare(priority2, priority1);

                    if (priorityCompare != 0) {
                        return priorityCompare;
                    }

                    //Deadline
                    if (t1.getDeadline() != null && t2.getDeadline() != null) {
                        return t1.getDeadline().compareTo(t2.getDeadline());
                    }
                    if (t1.getDeadline() != null) return -1;
                    if (t2.getDeadline() != null) return 1;

                    //Erstelldatum
                    return t1.getCreatedAt().compareTo(t2.getCreatedAt());
                })
                .collect(Collectors.toList());
    }

    //Tasks einplanen
    private List<ScheduledItem> scheduleTasksToSlots(
            List<Task> prioritizedTasks,
            List<TimeSlot> availableSlots,
            UserPreferences prefs) {

        List<ScheduledItem> scheduled = new ArrayList<>();
        List<TimeSlot> remainingSlots = new ArrayList<>(availableSlots);

        int tasksPerDay = 0;
        LocalDate currentDate = null;
        int maxTasksPerDay = prefs.getMaxTasksPerDay() != null ? prefs.getMaxTasksPerDay() : 10;

        for (Task task : prioritizedTasks) {

            if (currentDate != null && tasksPerDay >= maxTasksPerDay) {
                LocalDate finalCurrentDate = currentDate;
                remainingSlots = remainingSlots.stream()
                        .filter(s -> s.getDate().isAfter(finalCurrentDate))
                        .collect(Collectors.toList());
                tasksPerDay = 0;
                currentDate = null;
            }

            //besten Slot
            TimeSlot bestSlot = findBestSlotForTask(task, remainingSlots, prefs);

            if (bestSlot != null) {
                //einplanen
                int duration = task.getEstimatedDurationMinutes() != null
                        ? task.getEstimatedDurationMinutes()
                        : 60;

                ScheduledItem item = new ScheduledItem();
                item.setTask(task);
                item.setStartTime(bestSlot.getStart());
                item.setEndTime(bestSlot.getStart().plusMinutes(duration));
                item.setType(ScheduledItemType.TASK);

                scheduled.add(item);

                // Update Task
                taskService.scheduleTask(
                        task.getId(),
                        item.getStartTime(),
                        item.getEndTime()
                );

                // Aktualisiere Slots
                updateSlotAfterScheduling(bestSlot, duration, remainingSlots);

                // Zähle Tasks
                if (currentDate == null || !currentDate.equals(bestSlot.getDate())) {
                    currentDate = bestSlot.getDate();
                    tasksPerDay = 1;
                } else {
                    tasksPerDay++;
                }

                log.debug("Task '{}' geplant für {} (Score: {})",
                        task.getTitle(), item.getStartTime(),
                        calculateSlotScore(bestSlot, task, prefs));
            } else {
                log.warn("Kein passender Slot für Task '{}' gefunden", task.getTitle());
            }
        }

        return scheduled;
    }

    //Bester Slot
    private TimeSlot findBestSlotForTask(Task task, List<TimeSlot> slots, UserPreferences prefs) {
        if (slots.isEmpty()) {
            return null;
        }

        int taskDuration = task.getEstimatedDurationMinutes() != null
                ? task.getEstimatedDurationMinutes()
                : 60;

        return slots.stream()
                .filter(slot -> slot.getDuration() >= taskDuration)
                .max(Comparator.comparing(slot -> calculateSlotScore(slot, task, prefs)))
                .orElse(null);
    }

    //Test?
    private double calculateSlotScore(TimeSlot slot, Task task, UserPreferences prefs) {
        double score = 0.0;

        //Deadline
        if (task.getDeadline() != null) {
            long daysUntilDeadline = ChronoUnit.DAYS.between(
                    slot.getDate(),
                    task.getDeadline().toLocalDate()
            );

            if (daysUntilDeadline < 0) {
                score += 100.0;
            } else if (daysUntilDeadline == 0) {
                score += 80.0;
            } else if (daysUntilDeadline <= 3) {
                score += 60.0 / (daysUntilDeadline + 1);
            } else {
                score += 40.0 / (daysUntilDeadline + 1);
            }
        } else {
            score += 10.0;
        }

        //Prioritäten
        int priority = task.getPriority() != null ? task.getPriority() : 3;
        score += (priority * 6.0);

        //Tageszeit
        if (isInPreferredTimeRange(slot, prefs)) {
            score += 20.0;
        }

        // Slot-Passung
        int slotDuration = slot.getDuration();
        int taskDuration = task.getEstimatedDurationMinutes() != null
                ? task.getEstimatedDurationMinutes()
                : 60;

        if (slotDuration == taskDuration) {
            score += 10.0;
        } else if (slotDuration < taskDuration + 30) {
            score += 8.0;
        } else {
            score += 5.0;
        }

        return score;
    }

    //Prüft ob Slot in bevorzugter Tageszeit liegt
    private boolean isInPreferredTimeRange(TimeSlot slot, UserPreferences prefs) {
        LocalTime slotTime = slot.getStart().toLocalTime();
        ProductivityPeakTime peakTime = prefs.getPeakProductivityTime();

        if (peakTime == null) {
            return true;
        }

        return switch (peakTime) {
            case MORNING -> slotTime.isAfter(LocalTime.of(6, 0)) &&
                    slotTime.isBefore(LocalTime.of(12, 0));
            case AFTERNOON -> slotTime.isAfter(LocalTime.of(12, 0)) &&
                    slotTime.isBefore(LocalTime.of(18, 0));
            case EVENING -> slotTime.isAfter(LocalTime.of(18, 0)) &&
                    slotTime.isBefore(LocalTime.of(24, 0));
            default -> true;
        };
    }

    //Aktualisiert Slot
    private void updateSlotAfterScheduling(TimeSlot slot, int usedMinutes, List<TimeSlot> remainingSlots) {
        int remaining = slot.getDuration() - usedMinutes;

        if (remaining < 10) {
            remainingSlots.remove(slot);
        } else {
            slot.setStart(slot.getStart().plusMinutes(usedMinutes));
            slot.setDuration(remaining);
        }
    }

    //wiederkehrende Aktivitäten
    private List<ScheduledItem> scheduleRecurringActivities(
            List<Habit> habits,
            List<WorkoutSession> workouts,
            List<TimeSlot> availableSlots,
            UserPreferences prefs) {

        List<ScheduledItem> scheduled = new ArrayList<>();

        //Habits
        for (Habit habit : habits) {
            ScheduledItem item = scheduleHabit(habit, availableSlots, prefs);
            if (item != null) {
                scheduled.add(item);

                // Update Slots
                int duration = habit.getDurationMinutes() != null ? habit.getDurationMinutes() : 30;
                TimeSlot usedSlot = findSlotForTime(item.getStartTime(), availableSlots);
                if (usedSlot != null) {
                    updateSlotAfterScheduling(usedSlot, duration, availableSlots);
                }
            }
        }

        //Workouts
        for (WorkoutSession workout : workouts) {
            if (workout.getStartTime() != null) {
                ScheduledItem item = new ScheduledItem();
                item.setWorkoutSession(workout);
                item.setStartTime(workout.getStartTime());
                item.setEndTime(workout.getEndTime() != null
                        ? workout.getEndTime()
                        : workout.getStartTime().plusMinutes(
                        workout.getDurationMinutes() != null ? workout.getDurationMinutes() : 60
                ));
                item.setType(ScheduledItemType.WORKOUT);
                scheduled.add(item);
            }
        }

        return scheduled;
    }

    // einzelnes Habit

    private ScheduledItem scheduleHabit(Habit habit, List<TimeSlot> slots, UserPreferences prefs) {
        LocalTime preferredTime = habit.getPreferredTime() != null
                ? habit.getPreferredTime()
                : LocalTime.of(9, 0);

        int duration = habit.getDurationMinutes() != null ? habit.getDurationMinutes() : 30;

        TimeSlot bestSlot = slots.stream()
                .filter(s -> s.getDuration() >= duration)
                .min(Comparator.comparing(s -> {
                    LocalTime slotTime = s.getStart().toLocalTime();
                    return Math.abs(ChronoUnit.MINUTES.between(slotTime, preferredTime));
                }))
                .orElse(null);

        if (bestSlot != null) {
            ScheduledItem item = new ScheduledItem();
            item.setHabit(habit);
            item.setStartTime(bestSlot.getStart());
            item.setEndTime(bestSlot.getStart().plusMinutes(duration));
            item.setType(ScheduledItemType.HABIT);
            return item;
        }

        return null;
    }

    //Speichert geplante Items in Datenbank als CalendarEvents
    @Transactional
    private void saveScheduleToDatabase(
            Long userId,
            List<ScheduledItem> scheduledTasks,
            List<ScheduledItem> scheduledHabits) {

        User user = userService.findById(userId);

        // Speichere Task-Events
        for (ScheduledItem item : scheduledTasks) {
            CalendarEvent event = new CalendarEvent();
            event.setUser(user);
            event.setTitle(item.getTask().getTitle());
            event.setDescription(item.getTask().getDescription());
            event.setStartTime(item.getStartTime());
            event.setEndTime(item.getEndTime());
            event.setEventType(EventType.TASK);

            event.setRelatedTask(item.getTask());

            event.setIsFixed(false);
            event.setColor(getColorForTask(item.getTask()));

            calendarEventRepository.save(event);
        }

        // Speichere Habit & Workout-Events
        for (ScheduledItem item : scheduledHabits) {
            CalendarEvent event = new CalendarEvent();
            event.setUser(user);
            event.setStartTime(item.getStartTime());
            event.setEndTime(item.getEndTime());
            event.setIsFixed(false);

            if (item.getType() == ScheduledItemType.HABIT && item.getHabit() != null) {
                event.setTitle(item.getHabit().getName());
                event.setDescription(item.getHabit().getDescription());
                event.setEventType(EventType.HABIT);

                event.setRelatedHabit(item.getHabit());

                event.setColor("#4CAF50");

            } else if (item.getType() == ScheduledItemType.WORKOUT && item.getWorkoutSession() != null) {
                event.setTitle(item.getWorkoutSession().getName());
                event.setDescription(item.getWorkoutSession().getDescription());
                event.setEventType(EventType.WORKOUT);
                event.setRelatedWorkout(item.getWorkoutSession());
                event.setColor("#FF5722");
            }

            calendarEventRepository.save(event);
        }

        log.info("Gespeichert: {} Task-Events, {} Habit/Workout-Events",
                scheduledTasks.size(), scheduledHabits.size());
    }

    //Farben
    private String getColorForTask(Task task) {
        //Priorität
        if (task.getPriority() != null && task.getPriority() >= 4) {
            return switch (task.getPriority()) {
                case 5 -> "#F44336";
                case 4 -> "#FF9800";
                default -> "#2196F3";
            };
        }

        //SpaceType
        if (task.getSpaceType() != null) {
            return getColorForSpaceType(task.getSpaceType());
        }

        // 3. Default
        return "#2196F3";
    }

    //Farben Spaces
    private String getColorForSpaceType(SpaceType spaceType) {
        if (spaceType == null) {
            return "#2196F3";  // Blau default
        }

        return switch (spaceType) {
            case SPORTS -> "#9C27B0";      // Lila
            case STUDY -> "#2196F3";    // Blau
            case PROJECTS -> "#00BCD4";   // Cyan
            case TASKS -> "#FF5722";       // Orange-Rot
            case RECIPES -> "#4CAF50";   // Grün
            default -> "#2196F3";
        };
    }

    //nicht eingeplante Tasks
    private List<Task> findUnscheduledTasks(List<Task> all, List<ScheduledItem> scheduled) {
        Set<Long> scheduledIds = scheduled.stream()
                .filter(item -> item.getTask() != null)
                .map(item -> item.getTask().getId())
                .collect(Collectors.toSet());

        return all.stream()
                .filter(task -> !scheduledIds.contains(task.getId()))
                .collect(Collectors.toList());
    }

    //Gesamtstunden
    @SafeVarargs
    private final double calculateTotalHours(List<ScheduledItem>... itemLists) {
        int totalMinutes = 0;

        for (List<ScheduledItem> items : itemLists) {
            for (ScheduledItem item : items) {
                long minutes = ChronoUnit.MINUTES.between(item.getStartTime(), item.getEndTime());
                totalMinutes += minutes;
            }
        }

        return totalMinutes / 60.0;
    }


    private TimeSlot findSlotForTime(LocalDateTime time, List<TimeSlot> slots) {
        return slots.stream()
                .filter(s -> !s.getStart().isAfter(time) && !s.getEnd().isBefore(time))
                .findFirst()
                .orElse(null);
    }

}