package com.example.taskmanager.repository;

import com.example.taskmanager.domain.Priority;
import com.example.taskmanager.domain.Task;
import com.example.taskmanager.domain.TaskStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composes optional task filters without duplicating repository methods.
 * 组合可选任务筛选条件，避免为每种组合重复定义 Repository 方法。
 */
public final class TaskSpecifications {
    private TaskSpecifications() {}

    public static Specification<Task> filtered(TaskStatus status, Priority priority, String tag, String query) {
        return Specification.allOf(
                equal("status", status),
                equal("priority", priority),
                tag == null || tag.isBlank() ? null :
                        (root, ignored, cb) -> cb.equal(root.join("tags", JoinType.INNER), tag.trim().toLowerCase()),
                query == null || query.isBlank() ? null : (root, ignored, cb) -> {
                    // User input remains a bound parameter; it is never concatenated into SQL.
                    // 用户输入始终作为绑定参数传递，不会直接拼接到 SQL 中。
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
