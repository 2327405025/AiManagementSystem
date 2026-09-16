-- Durable retry-key mapping; the primary key arbitrates concurrent creates.
-- 持久化重试 Key 映射；主键负责裁决并发创建。
CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_idempotency_created_at ON idempotency_records(created_at);
