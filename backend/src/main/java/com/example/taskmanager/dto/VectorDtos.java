package com.example.taskmanager.dto;

import com.example.taskmanager.dto.TaskDtos.TaskResponse;

import java.util.List;

/**
 * Semantic-search response contracts independent from the vector provider.
 * 与具体向量数据库供应商无关的语义搜索响应契约。
 */
public final class VectorDtos {
    private VectorDtos() {}

    public record SemanticSearchResponse(
            List<TaskResponse> content,
            String source
    ) {}
}
