-- Optimistic-lock token prevents silent lost updates.
-- 乐观锁版本号防止更新被静默覆盖。
ALTER TABLE tasks ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Composite indexes match filtered ordering and keyset pagination.
-- 复合索引对应筛选排序与键集分页访问路径。
CREATE INDEX idx_tasks_filter_order
    ON tasks(status, priority, created_at DESC, id DESC);

CREATE INDEX idx_tasks_cursor
    ON tasks(created_at DESC, id DESC);
