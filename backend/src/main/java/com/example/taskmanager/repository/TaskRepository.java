package com.example.taskmanager.repository;

import com.example.taskmanager.domain.Task;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    /**
     * Stable keyset pagination avoids the increasing scan cost of deep offsets.
     * The ID tie-breaker preserves ordering when timestamps are equal.
     * 稳定的键集分页避免深层 offset 带来的扫描成本；时间相同时用 ID 保持确定顺序。
     */
    @Query("""
            SELECT task FROM Task task
            WHERE task.ownerId = :ownerId
              AND (
                    :createdAt IS NULL
                    OR task.createdAt < :createdAt
                    OR (task.createdAt = :createdAt AND task.id < :id)
                  )
            ORDER BY task.createdAt DESC, task.id DESC
            """)
    Slice<Task> findNextSlice(Long ownerId, Instant createdAt, Long id, Pageable pageable);
}
