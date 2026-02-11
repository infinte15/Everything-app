package com.Finn.everything_app.controller;

import com.Finn.everything_app.dto.TaskDTO;
import com.Finn.everything_app.mapper.TaskMapper;
import com.Finn.everything_app.model.Task;
import com.Finn.everything_app.model.TaskStatus;
import com.Finn.everything_app.security.CurrentUser;
import com.Finn.everything_app.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskService taskService;
    private final TaskMapper taskMapper;

    // GET /api/tasks  -->Hole alle Tasks
    @GetMapping
    public ResponseEntity<List<TaskDTO>> getAllTasks(@CurrentUser Long userId) {
        List<Task> tasks = taskService.getAllUserTasks(userId);
        List<TaskDTO> taskDTOs = tasks.stream()
                .map(taskMapper::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(taskDTOs);
    }

    // GET /api/tasks/{id} --> einzelne Task
    @GetMapping("/{id}")
    public ResponseEntity<TaskDTO> getTaskById(@PathVariable Long id) {
        Task task = taskService.getTaskById(id);
        return ResponseEntity.ok(taskMapper.toDTO(task));
    }

    // GET /api/tasks/status/{status}  -->  Tasks nach Status
    @GetMapping("/status/{status}")
    public ResponseEntity<List<TaskDTO>> getTasksByStatus(
            @CurrentUser Long userId,
            @PathVariable TaskStatus status) {

        List<Task> tasks = taskService.getTasksByStatus(userId, status);
        List<TaskDTO> taskDTOs = tasks.stream()
                .map(taskMapper::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(taskDTOs);
    }

    //GET /api/tasks/unscheduled  -->ungeplante Tasks
    @GetMapping("/unscheduled")
    public ResponseEntity<List<TaskDTO>> getUnscheduledTasks(@CurrentUser Long userId){
        List<Task> tasks = taskService.getUnscheduledTasks(userId);
        List<TaskDTO> taskDTOs = tasks.stream().map(taskMapper::toDTO).collect(Collectors.toList());

        return ResponseEntity.ok(taskDTOs);
    }

    //POST /api/tasks  --> neue Task
    @PostMapping
    public ResponseEntity<TaskDTO> createTask(@CurrentUser Long userId, @Valid @RequestBody TaskDTO taskDTO){
        Task task = taskMapper.toEntity(taskDTO);
        Task created = taskService.createTask(userId,task);

        return ResponseEntity.status(HttpStatus.CREATED).body(taskMapper.toDTO(created));
    }

    //PUT /api/tasks/{id}  --> Task updaten
    @PutMapping("/{id}")
    public ResponseEntity<TaskDTO> updateTask(@PathVariable Long id, @Valid @RequestBody TaskDTO taskDTO){
        Task task = taskMapper.toEntity(taskDTO);
        Task updated = taskService.updateTask(id, task);

        return ResponseEntity.ok(taskMapper.toDTO(updated));
    }

    //PUT /api/tasks/{id}/complete
    @PutMapping("/{id}/complete")
    public ResponseEntity<TaskDTO> completeTask(@PathVariable Long id){
        Task completed = taskService.completeTask(id);
        return ResponseEntity.ok(taskMapper.toDTO(completed));
    }

    //DELETE /api/tasks/{id}  --> task löschen
    @DeleteMapping
    public ResponseEntity<Void> deleteTask(@PathVariable Long id){
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
