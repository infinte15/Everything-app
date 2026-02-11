package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.TaskDTO;
import com.Finn.everything_app.model.Task;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

    public TaskDTO toDTO(Task task) {
        if (task == null) return null;

        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setPriority(task.getPriority());
        dto.setDeadline(task.getDeadline());
        dto.setEstimatedDurationMinutes(task.getEstimatedDurationMinutes());
        dto.setScheduledStartTime(task.getScheduledStartTime());
        dto.setScheduledEndTime(task.getScheduledEndTime());
        dto.setStatus(task.getStatus());
        dto.setSpaceType(task.getSpaceType());
        dto.setProjectId(task.getProject() != null ? task.getProject().getId() : null);
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        dto.setCompletedAt(task.getCompletedAt());

        return dto;
    }

    public Task toEntity(TaskDTO dto) {
        if (dto == null) return null;

        Task task = new Task();
        task.setId(dto.getId());
        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setPriority(dto.getPriority());
        task.setDeadline(dto.getDeadline());
        task.setEstimatedDurationMinutes(dto.getEstimatedDurationMinutes());
        task.setStatus(dto.getStatus());
        task.setSpaceType(dto.getSpaceType());

        return task;
    }
}