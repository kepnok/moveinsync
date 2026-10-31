package com.moveinsync.config.server.dto;

import com.moveinsync.config.server.repository.RolloutRepository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RolloutResponse(
        UUID id,
        String status,
        String tenantId,
        long stageVersion,
        Long finalVersion,
        String createdBy,
        Instant createdAt,
        Set<String> targetOffices
) {
    public static RolloutResponse from(RolloutRepository.RolloutRecord r) {
        return new RolloutResponse(
                r.id(),
                r.status(),
                r.tenantId(),
                r.stageVersion(),
                r.finalVersion(),
                r.createdBy(),
                r.createdAt(),
                r.targetOffices()
        );
    }
}

