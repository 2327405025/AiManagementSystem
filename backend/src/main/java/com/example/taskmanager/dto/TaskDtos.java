package com.example.taskmanager.dto;

import com.example.taskmanager.domain.Priority;
import com.example.taskmanager.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * External task API contracts kept separate from mutable JPA entities.
 * 与可变 JPA 实体隔离的任务 API 外部契约。
 *
 * <p>The request version is optional for creation and acts as an optimistic
 * concurrency token for updates.</p>
 * <p>创建时 version 可省略；更新时它作为乐观并发令牌。</p>
 */
public final class TaskDtos {
    private TaskDtos() {}

    public record TaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            TaskStatus status,
            Priority priority,
            Instant dueAt,
            @Size(max = 20) Set<@NotBlank @Size(max = 50) String> tags,
            Long version
    ) {}

    public record TaskResponse(
            Long id,
            Long ownerId,
            String title,
            String description,
            TaskStatus status,
            Priority priority,
            Instant dueAt,
            Instant createdAt,
            Instant updatedAt,
            Set<String> tags,
            Set<Long> dependencyIds,
            long version
    ) {}

    public record DependencyNode(
            Long id,
            String title,
            TaskStatus status,
            List<DependencyNode> dependencies
    ) {}

    public record TaskPage(
            List<TaskResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static TaskPage from(Page<TaskResponse> result) {
            return new TaskPage(result.getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages());
        }
    }

    public record CursorPage(
            List<TaskResponse> content,
            String nextCursor,
            boolean hasNext
    ) {}
}
