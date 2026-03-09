package com.Finn.everything_app.service;

import com.Finn.everything_app.model.Task;
import com.Finn.everything_app.model.TaskStatus;
import com.Finn.everything_app.model.User;
import com.Finn.everything_app.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
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
    private final ApplicationEventPublisher eventPublisher;

    public List<Task> getAllUserTasks(Long userId) {
        return taskRepository.findByUserId(userId);
    }

    public List<Task> getTasksByStatus(Long userId, TaskStatus status) {
        return taskRepository.findByUserIdAndStatus(userId, status);
    }

    public List<Task> getUnscheduledTasks(Long userId) {
        return taskRepository.findTasksForAutoScheduling(userId);
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
            task.setPriority(3);  // Standard
        }

        if (task.getEstimatedDurationMinutes() == null) {
            task.setEstimatedDurationMinutes(60);  // Standard
        }

        Task savedTask = taskRepository.save(task);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
        return savedTask;
    }

    @Transactional
    public Task updateTask(Long taskId, Task updatedTask) {
        Task existing = getTaskById(taskId);

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

        existing.setUpdatedAt(LocalDateTime.now());

        Task savedTask = taskRepository.save(existing);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, existing.getUser().getId()));
        return savedTask;
    }

    @Transactional
    public Task completeTask(Long taskId) {
        Task task = getTaskById(taskId);
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        Task savedTask = taskRepository.save(task);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, task.getUser().getId()));
        return savedTask;
    }

    @Transactional
    public void deleteTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task nicht gefunden"));
        Long userId = task.getUser().getId();
        taskRepository.deleteById(taskId);
        eventPublisher.publishEvent(new ScheduleChangedEvent(this, userId));
    }

    @Transactional
    public void scheduleTask(Long taskId, LocalDateTime startTime, LocalDateTime endTime) {
        Task task = getTaskById(taskId);
        task.setScheduledStartTime(startTime);
        task.setScheduledEndTime(endTime);
        taskRepository.save(task);
    }

    public List<Task> getTasksForScheduling(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return taskRepository.findTasksForScheduling(userId, TaskStatus.TODO, startDate, endDate);
    }
}
