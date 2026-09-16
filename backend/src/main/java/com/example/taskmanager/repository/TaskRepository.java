package com.example.taskmanager.repository;

import com.example.taskmanager.domain.Task;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    @Query("""
            SELECT task FROM Task task
            WHERE :createdAt IS NULL
               OR task.createdAt < :createdAt
               OR (task.createdAt = :createdAt AND task.id < :id)
            ORDER BY task.createdAt DESC, task.id DESC
            """)
    Slice<Task> findNextSlice(Instant createdAt, Long id, Pageable pageable);
}
