package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.ProjectDTO;
import com.Finn.everything_app.mapper.ProjectMapper;
import com.Finn.everything_app.model.Project;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;

    @GetMapping
    public ResponseEntity<List<ProjectDTO>> getAllProjects(@CurrentUser Long userId) {
        List<Project> projects = projectService.getUserProjects(userId);
        return ResponseEntity.ok(
                projects.stream().map(projectMapper::toDTO).collect(Collectors.toList())
        );
    }

    @PostMapping
    public ResponseEntity<ProjectDTO> createProject(
            @CurrentUser Long userId,
            @Valid @RequestBody ProjectDTO projectDTO) {

        Project project = projectMapper.toEntity(projectDTO);
        Project created = projectService.createProject(userId, project);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                projectMapper.toDTO(created)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectDTO> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectDTO projectDTO) {

        Project project = projectMapper.toEntity(projectDTO);
        Project updated = projectService.updateProject(id, project);

        return ResponseEntity.ok(projectMapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}