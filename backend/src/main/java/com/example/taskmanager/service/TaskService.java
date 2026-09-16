package com.example.taskmanager.service;

import com.example.taskmanager.cache.TaskCache;
import com.example.taskmanager.domain.Priority;
import com.example.taskmanager.domain.Task;
import com.example.taskmanager.domain.TaskStatus;
import com.example.taskmanager.dto.TaskDtos.CursorPage;
import com.example.taskmanager.dto.TaskDtos.DependencyNode;
import com.example.taskmanager.dto.TaskDtos.TaskRequest;
import com.example.taskmanager.dto.TaskDtos.TaskResponse;
import com.example.taskmanager.exception.ApiException;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.repository.TaskSpecifications;
import com.example.taskmanager.vector.TaskIndexEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository repository;
    private final TaskCache cache;
    private final ApplicationEventPublisher events;

    public TaskService(TaskRepository repository, TaskCache cache, ApplicationEventPublisher events) {
        this.repository = repository;
        this.cache = cache;
        this.events = events;
    }

    @Transactional
    public TaskResponse create(TaskRequest request) {
        Task task = new Task();
        apply(task, request, true);
        TaskResponse response = toResponse(repository.saveAndFlush(task));
        publishAfterCommit(indexEvent(task));
        return response;
    }

    public TaskResponse get(Long id) {
        return cache.get(id).orElseGet(() -> {
            TaskResponse response = toResponse(require(id));
            cache.put(response);
            return response;
        });
    }

    public List<TaskResponse> getMany(List<Long> ids) {
        return ids.stream().map(this::get).toList();
    }

    public Page<TaskResponse> list(TaskStatus status, Priority priority, String tag, String query, Pageable pageable) {
        return repository.findAll(TaskSpecifications.filtered(status, priority, tag, query), pageable)
                .map(this::toResponse);
    }

    public CursorPage listByCursor(String cursor, int size) {
        Cursor value = decodeCursor(cursor);
        var slice = repository.findNextSlice(value.createdAt(), value.id(), PageRequest.of(0, size));
        List<TaskResponse> content = slice.getContent().stream().map(this::toResponse).toList();
        String next = slice.hasNext() && !slice.getContent().isEmpty()
                ? encodeCursor(slice.getContent().getLast())
                : null;
        return new CursorPage(content, next, slice.hasNext());
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = require(id);
        if (request.version() != null && request.version() != task.getVersion()) {
            throw ApiException.conflict("Task was modified by another request; reload and retry");
        }
        apply(task, request, false);
        repository.flush();
        evictAfterCommit(id);
        publishAfterCommit(indexEvent(task));
        return toResponse(task);
    }

    @Transactional
    public void delete(Long id) {
        Task task = require(id);
        repository.delete(task);
        evictAfterCommit(id);
        publishAfterCommit(TaskIndexEvent.delete(id));
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
        evictAfterCommit(taskId);
        return toResponse(task);
    }

    @Transactional
    public void removeDependency(Long taskId, Long dependencyId) {
        Task task = require(taskId);
        boolean removed = task.getDependencies().removeIf(item -> item.getId().equals(dependencyId));
        if (!removed) {
            throw ApiException.notFound("Dependency", dependencyId);
        }
        evictAfterCommit(taskId);
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
                Set.copyOf(task.getTags()), dependencies, task.getVersion());
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

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new Cursor(null, null);
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 3);
            return new Cursor(
                    Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1])),
                    Long.parseLong(parts[2]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid cursor");
        }
    }

    private String encodeCursor(Task task) {
        String value = task.getCreatedAt().getEpochSecond() + ":"
                + task.getCreatedAt().getNano() + ":" + task.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Cursor(Instant createdAt, Long id) {}

    private void evictAfterCommit(Long id) {
        afterCommit(() -> cache.evict(id));
    }

    private void publishAfterCommit(TaskIndexEvent event) {
        afterCommit(() -> events.publishEvent(event));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private TaskIndexEvent indexEvent(Task task) {
        String document = task.getTitle() + "\n"
                + (task.getDescription() == null ? "" : task.getDescription()) + "\n"
                + String.join(" ", task.getTags());
        return new TaskIndexEvent(task.getId(), document,
                Map.of("status", task.getStatus().value(), "priority", task.getPriority().value()),
                TaskIndexEvent.Operation.UPSERT);
    }
}
