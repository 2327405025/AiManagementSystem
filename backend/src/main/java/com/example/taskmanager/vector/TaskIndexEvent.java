package com.example.taskmanager.vector;

import java.util.Map;

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
