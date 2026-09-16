package com.example.taskmanager.dto;

import com.example.taskmanager.dto.TaskDtos.TaskResponse;

import java.util.List;

public final class VectorDtos {
    private VectorDtos() {}

    public record SemanticSearchResponse(
            List<TaskResponse> content,
            String source
    ) {}
}
