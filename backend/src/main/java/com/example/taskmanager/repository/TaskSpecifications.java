package com.example.taskmanager.repository;

import com.example.taskmanager.domain.Priority;
import com.example.taskmanager.domain.Task;
import com.example.taskmanager.domain.TaskStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class TaskSpecifications {
    private TaskSpecifications() {}

    public static Specification<Task> filtered(TaskStatus status, Priority priority, String tag, String query) {
        return Specification.allOf(
                equal("status", status),
                equal("priority", priority),
                tag == null || tag.isBlank() ? null :
                        (root, ignored, cb) -> cb.equal(root.join("tags", JoinType.INNER), tag.trim().toLowerCase()),
                query == null || query.isBlank() ? null : (root, ignored, cb) -> {
                    var pattern = "%" + query.trim().toLowerCase() + "%";
                    return cb.or(
                            cb.like(cb.lower(root.get("title")), pattern),
                            cb.like(cb.lower(root.get("description")), pattern));
                });
    }

    private static <T> Specification<Task> equal(String field, T value) {
        return value == null ? null : (root, ignored, cb) -> cb.equal(root.get(field), value);
    }
}
