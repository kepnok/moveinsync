package com.moveinsync.config.server.repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface OutboxRepository {
    record OutboxRow(long id, long version, Set<String> tenants, Instant createdAt, Instant publishedAt) {}

    void insert(long version, Set<String> tenants);
    List<OutboxRow> lockPending(int batchSize);
    void markPublished(long version);
    long pendingCount();
}

