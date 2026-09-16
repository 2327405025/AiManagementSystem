package com.example.taskmanager.dto;

import com.example.taskmanager.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Validated AI request and suggestion contracts.
 * 经过校验的 AI 请求与建议契约。
 *
 * <p>The source field makes provider output and deterministic fallback
 * observable to clients.</p>
 * <p>source 字段让客户端能够区分供应商输出与确定性规则降级。</p>
 */
public final class AiDtos {
    private AiDtos() {}

    public record NaturalLanguageRequest(@NotBlank @Size(max = 1000) String text) {}

    public record TaskSuggestion(
            String title,
            String description,
            Instant dueAt,
            Priority priority,
            Set<String> tags,
            String source
    ) {}

    public record DecomposeRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description
    ) {}

    public record SubtaskSuggestion(String title, Priority priority, Set<String> tags) {}

    public record Decomposition(List<SubtaskSuggestion> subtasks, String source) {}
}
