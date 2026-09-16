package com.example.taskmanager.service;

import com.example.taskmanager.domain.Priority;
import com.example.taskmanager.domain.Task;
import com.example.taskmanager.domain.TaskStatus;
import com.example.taskmanager.dto.TaskDtos.DependencyNode;
import com.example.taskmanager.dto.TaskDtos.TaskRequest;
import com.example.taskmanager.dto.TaskDtos.TaskResponse;
import com.example.taskmanager.exception.ApiException;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.repository.TaskSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TaskResponse create(TaskRequest request) {
        Task task = new Task();
        apply(task, request, true);
        return toResponse(repository.save(task));
    }

    public TaskResponse get(Long id) {
        return toResponse(require(id));
    }

    public Page<TaskResponse> list(TaskStatus status, Priority priority, String tag, String query, Pageable pageable) {
        return repository.findAll(TaskSpecifications.filtered(status, priority, tag, query), pageable)
                .map(this::toResponse);
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = require(id);
        apply(task, request, false);
        return toResponse(task);
    }

    @Transactional
    public void delete(Long id) {
        Task task = require(id);
        repository.delete(task);
    }

    @Transactional
    public TaskResponse addDependency(Long taskId, Long dependencyId) {
        if (taskId.equals(dependencyId)) {
            throw ApiException.conflict("A task cannot depend on itself");
        }
        Task task = require(taskId);
        Task dependency = require(dependencyId);
        if (reaches(dependency, taskId, new HashSet<>())) {
            throw ApiException.conflict("Dependency would create a cycle");
        }
        task.getDependencies().add(dependency);
        return toResponse(task);
    }

    @Transactional
    public void removeDependency(Long taskId, Long dependencyId) {
        Task task = require(taskId);
        boolean removed = task.getDependencies().removeIf(item -> item.getId().equals(dependencyId));
        if (!removed) {
            throw ApiException.notFound("Dependency", dependencyId);
        }
    }

    public DependencyNode dependencyTree(Long id) {
        return toNode(require(id), new HashSet<>());
    }

    private void apply(Task task, TaskRequest request, boolean creating) {
        task.setTitle(request.title().trim());
        task.setDescription(blankToNull(request.description()));
        if (request.status() != null) {
            if (request.status() == TaskStatus.COMPLETED) {
                ensureDependenciesCompleted(task);
            }
            task.setStatus(request.status());
        } else if (creating) {
            task.setStatus(TaskStatus.PENDING);
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        } else if (creating) {
            task.setPriority(Priority.MEDIUM);
        }
        task.setDueAt(request.dueAt());
        task.setTags(normalizeTags(request.tags()));
    }

    private void ensureDependenciesCompleted(Task task) {
        List<Long> blocking = task.getDependencies().stream()
                .filter(dependency -> dependency.getStatus() != TaskStatus.COMPLETED)
                .map(Task::getId)
                .toList();
        if (!blocking.isEmpty()) {
            throw ApiException.conflict("Complete dependencies first: " + blocking);
        }
    }

    private boolean reaches(Task current, Long targetId, Set<Long> visited) {
        if (current.getId().equals(targetId)) {
            return true;
        }
        if (!visited.add(current.getId())) {
            return false;
        }
        return current.getDependencies().stream().anyMatch(next -> reaches(next, targetId, visited));
    }

    private DependencyNode toNode(Task task, Set<Long> path) {
        if (!path.add(task.getId())) {
            throw ApiException.conflict("Existing dependency cycle detected");
        }
        List<DependencyNode> children = task.getDependencies().stream()
                .map(dependency -> toNode(dependency, new HashSet<>(path)))
                .toList();
        return new DependencyNode(task.getId(), task.getTitle(), task.getStatus(), children);
    }

    private Task require(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Task", id));
    }

    private TaskResponse toResponse(Task task) {
        Set<Long> dependencies = task.getDependencies().stream()
                .map(Task::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                task.getPriority(), task.getDueAt(), task.getCreatedAt(), task.getUpdatedAt(),
                Set.copyOf(task.getTags()), dependencies);
    }

    private Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) {
            return new LinkedHashSet<>();
        }
        return tags.stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
