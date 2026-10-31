package com.moveinsync.config.server.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RolloutRepository {
    record RolloutRecord(
            UUID id,
            String status,
            String tenantId,
            long stageVersion,
            Long finalVersion,
            String createdBy,
            Instant createdAt,
            Set<String> targetOffices
    ) {}

    void insert(RolloutRecord record);
    Optional<RolloutRecord> findById(UUID id);
    void updateStatus(UUID id, String status, Long finalVersion);
    Set<String> targets(UUID id);
}

