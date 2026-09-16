ALTER TABLE tasks ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_tasks_filter_order
    ON tasks(status, priority, created_at DESC, id DESC);

CREATE INDEX idx_tasks_cursor
    ON tasks(created_at DESC, id DESC);
