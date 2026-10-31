package com.moveinsync.config.server.dto;

public record PromoteRolloutRequest(
        String reason,
        ScopeDto targetScope
) {}
