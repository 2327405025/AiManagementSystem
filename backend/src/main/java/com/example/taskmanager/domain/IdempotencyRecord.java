package com.example.taskmanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Durable mapping from a client retry key to its created task.
 * The primary-key constraint is the final guard against concurrent duplicates.
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id
    @Column(name = "idempotency_key", length = 100)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String key, String requestHash, Long taskId) {
        this.key = key;
        this.requestHash = requestHash;
        this.taskId = taskId;
        this.createdAt = Instant.now();
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Long getTaskId() {
        return taskId;
    }
}
