package com.example.taskmanager.controller;

import com.example.taskmanager.domain.Priority;
import com.example.taskmanager.domain.TaskStatus;
import com.example.taskmanager.dto.TaskDtos.DependencyNode;
import com.example.taskmanager.dto.TaskDtos.TaskPage;
import com.example.taskmanager.dto.TaskDtos.TaskRequest;
import com.example.taskmanager.dto.TaskDtos.TaskResponse;
import com.example.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "updatedAt", "priority", "status", "title");
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request) {
        TaskResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/tasks/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    TaskResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping
    TaskPage list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        if (!SORT_FIELDS.contains(sort)) {
            throw new IllegalArgumentException("Unsupported sort field: " + sort);
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        }
        Sort.Direction sortDirection = Sort.Direction.fromOptionalString(direction)
                .orElseThrow(() -> new IllegalArgumentException("direction must be asc or desc"));
        return TaskPage.from(service.list(status, priority, tag, query,
                PageRequest.of(page, size, Sort.by(sortDirection, sort))));
    }

    @PutMapping("/{id}")
    TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{taskId}/dependencies/{dependencyId}")
    TaskResponse addDependency(@PathVariable Long taskId, @PathVariable Long dependencyId) {
        return service.addDependency(taskId, dependencyId);
    }

    @DeleteMapping("/{taskId}/dependencies/{dependencyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeDependency(@PathVariable Long taskId, @PathVariable Long dependencyId) {
        service.removeDependency(taskId, dependencyId);
    }

    @GetMapping("/{id}/dependency-tree")
    DependencyNode dependencyTree(@PathVariable Long id) {
        return service.dependencyTree(id);
    }
}
