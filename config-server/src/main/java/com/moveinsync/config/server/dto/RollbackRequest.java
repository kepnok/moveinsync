package com.moveinsync.config.server.dto;

public record RollbackRequest(
        long targetVersion,
        String reason,
        String tenantId
) {}

