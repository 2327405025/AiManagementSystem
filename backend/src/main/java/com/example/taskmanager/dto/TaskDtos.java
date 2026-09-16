package com.example.taskmanager.dto;

import com.example.taskmanager.domain.Priority;
import com.example.taskmanager.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class TaskDtos {
    private TaskDtos() {}

    public record TaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            TaskStatus status,
            Priority priority,
            Instant dueAt,
            @Size(max = 20) Set<@NotBlank @Size(max = 50) String> tags
    ) {}

    public record TaskResponse(
            Long id,
            String title,
            String description,
            TaskStatus status,
            Priority priority,
            Instant dueAt,
            Instant createdAt,
            Instant updatedAt,
            Set<String> tags,
            Set<Long> dependencyIds
    ) {}

    public record DependencyNode(
            Long id,
            String title,
            TaskStatus status,
            List<DependencyNode> dependencies
    ) {}
}
