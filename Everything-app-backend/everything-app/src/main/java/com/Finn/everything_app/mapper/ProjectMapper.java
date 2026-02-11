package com.Finn.everything_app.mapper;

import com.Finn.everything_app.dto.ProjectDTO;
import com.Finn.everything_app.model.Project;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public ProjectDTO toDTO(Project project) {
        if (project == null) return null;

        ProjectDTO dto = new ProjectDTO();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setStartDate(project.getStartDate());
        dto.setTargetEndDate(project.getTargetEndDate());
        dto.setActualEndDate(project.getActualEndDate());
        dto.setStatus(project.getStatus());
        dto.setCompletionPercentage(project.getCompletionPercentage());
        dto.setTasksTotal(project.getTasksTotal());
        dto.setTasksCompleted(project.getTasksCompleted());

        return dto;
    }

    public Project toEntity(ProjectDTO dto) {
        if (dto == null) return null;

        Project project = new Project();
        project.setId(dto.getId());
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setStartDate(dto.getStartDate());
        project.setTargetEndDate(dto.getTargetEndDate());
        project.setActualEndDate(dto.getActualEndDate());
        project.setStatus(dto.getStatus());
        project.setCompletionPercentage(dto.getCompletionPercentage());

        return project;
    }
}