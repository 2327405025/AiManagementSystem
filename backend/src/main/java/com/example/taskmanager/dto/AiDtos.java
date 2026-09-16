package com.example.taskmanager.dto;

import com.example.taskmanager.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

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
