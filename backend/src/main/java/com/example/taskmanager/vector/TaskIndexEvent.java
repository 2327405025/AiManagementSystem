package com.example.taskmanager.vector;

import java.util.Map;

/**
 * Provider-neutral description of a post-commit vector-index mutation.
 * 与供应商无关的事务提交后向量索引变更描述。
 */
public record TaskIndexEvent(
        Long id,
        String document,
        Map<String, Object> metadata,
        Operation operation
) {
    public enum Operation {
        UPSERT,
        DELETE
    }

    public static TaskIndexEvent delete(Long id) {
        return new TaskIndexEvent(id, null, Map.of(), Operation.DELETE);
    }
}
