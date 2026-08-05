package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.CalendarEventDTO;
import com.Finn.everything_app.dto.ProjectDTO;
import com.Finn.everything_app.dto.TaskDTO;
import com.Finn.everything_app.mapper.CalendarEventMapper;
import com.Finn.everything_app.mapper.ProjectMapper;
import com.Finn.everything_app.mapper.TaskMapper;
import com.Finn.everything_app.model.Project;
import com.Finn.everything_app.repository.CalendarEventRepository;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProjectController {

    /** Vorschau-Fenster der kommenden Projektbloecke, wenn der Aufrufer keins mitgibt. */
    private static final int DEFAULT_SESSION_WINDOW_DAYS = 14;

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final CalendarEventMapper calendarEventMapper;
    private final CalendarEventRepository calendarEventRepository;

    @GetMapping
    public ResponseEntity<List<ProjectDTO>> getAllProjects(@CurrentUser Long userId) {
        List<Project> projects = projectService.getUserProjects(userId);
        return ResponseEntity.ok(
                projects.stream().map(projectMapper::toDTO).collect(Collectors.toList())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectDTO> getProject(@CurrentUser Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(projectMapper.toDTO(projectService.getProjectById(id, userId)));
    }

    /** Aufgaben des Projekts — Quelle des Fortschrittsbalkens im Detail-Screen. */
    @GetMapping("/{id}/tasks")
    public ResponseEntity<List<TaskDTO>> getProjectTasks(@CurrentUser Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectTasks(id, userId).stream()
                .map(taskMapper::toDTO)
                .collect(Collectors.toList()));
    }

    /**
     * Die vom Scheduler platzierten Projektbloecke. Bewusst als CalendarEventDTO: Pin-Zustand
     * (isFixed) und Farbe kommen so ohne zusaetzliches Schema mit.
     */
    @GetMapping("/{id}/sessions")
    public ResponseEntity<List<CalendarEventDTO>> getProjectSessions(
            @CurrentUser Long userId,
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        projectService.getProjectById(id, userId);   // 404 statt leerer Liste bei fremdem Projekt

        LocalDateTime start = from != null ? from : LocalDateTime.now();
        LocalDateTime end = to != null ? to : start.plusDays(DEFAULT_SESSION_WINDOW_DAYS);

        return ResponseEntity.ok(calendarEventRepository
                .findByUserIdAndRelatedProjectIdAndStartTimeBetweenOrderByStartTimeAsc(userId, id, start, end)
                .stream()
                .map(calendarEventMapper::toDTO)
                .collect(Collectors.toList()));
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
            @CurrentUser Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ProjectDTO projectDTO) {

        Project project = projectMapper.toEntity(projectDTO);
        Project updated = projectService.updateProject(id, userId, project);

        return ResponseEntity.ok(projectMapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@CurrentUser Long userId, @PathVariable Long id) {
        projectService.deleteProject(id, userId);
        return ResponseEntity.noContent().build();
    }
}
