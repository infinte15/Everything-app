package com.Finn.everything_app.service;

import com.Finn.everything_app.model.*;
import com.Finn.everything_app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public Project createProject(Long userId, Project project) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User nicht gefunden"));

        project.setUser(user);
        project.setStatus(project.getStatus() != null ? project.getStatus() : ProjectStatus.PLANNING);
        project.setCompletionPercentage(0);
        project.setTasksTotal(0);
        project.setTasksCompleted(0);

        return projectRepository.save(project);
    }

    public List<Project> getUserProjects(Long userId) {
        return projectRepository.findByUserId(userId);
    }

    public Project getProjectById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Projekt nicht gefunden"));
    }

    public List<Project> getProjectsByStatus(Long userId, ProjectStatus status) {
        return projectRepository.findByUserIdAndStatus(userId, status);
    }

    @Transactional
    public Project updateProject(Long id, Project updatedProject) {
        Project project = getProjectById(id);

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
        if (updatedProject.getCompletionPercentage() != null) {
            project.setCompletionPercentage(updatedProject.getCompletionPercentage());
        }

        return projectRepository.save(project);
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = getProjectById(id);
        projectRepository.delete(project);
    }

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


        if (total > 0) {
            int percentage = (int) ((completed * 100.0) / total);
            project.setCompletionPercentage(percentage);

            if (percentage == 100 && project.getStatus() != ProjectStatus.COMPLETED) {
                project.setStatus(ProjectStatus.COMPLETED);
            } else if (percentage > 0 && percentage < 100 && project.getStatus() == ProjectStatus.PLANNING) {
                project.setStatus(ProjectStatus.ACTIVE);
            }
        }

        projectRepository.save(project);
    }


    @Transactional
    public void recalculateProjectStats(Long projectId) {
        if (projectId != null) {
            updateProjectStatistics(projectId);
        }
    }
}