package com.example.taskmanager.service;

import com.example.taskmanager.dto.VectorDtos.SemanticSearchResponse;
import com.example.taskmanager.vector.ChromaTaskIndex;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Keeps vector search optional: Chroma failures degrade to indexed database
 * filtering instead of becoming user-visible availability failures.
 * 保持向量搜索为可选能力：Chroma 故障时降级到数据库过滤，不影响用户可用性。
 */
@Service
public class SemanticSearchService {
    private final ChromaTaskIndex index;
    private final TaskService tasks;

    public SemanticSearchService(ChromaTaskIndex index, TaskService tasks) {
        this.index = index;
        this.tasks = tasks;
    }

    public SemanticSearchResponse search(String query, int limit) {
        try {
            var ids = index.search(query, limit);
            return new SemanticSearchResponse(tasks.getMany(ids), "vector");
        } catch (RuntimeException exception) {
            var fallback = tasks.list(null, null, null, query,
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));
            return new SemanticSearchResponse(fallback.getContent(), "keyword_fallback");
        }
    }
}
