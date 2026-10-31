package com.moveinsync.config.server.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateDefinitionRequest(
        String key,
        String type,
        JsonNode defaultValue,
        JsonNode rules,
        String description,
        String reason
) {}

