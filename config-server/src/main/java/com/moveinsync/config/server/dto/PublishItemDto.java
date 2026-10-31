package com.moveinsync.config.server.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record PublishItemDto(
        String key,
        ScopeDto scope,
        JsonNode value
) {}

