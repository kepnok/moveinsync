package com.moveinsync.config.server.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record CreateRolloutRequest(
        String settingKey,
        JsonNode value,
        List<String> officeIds,
        String reason
) {}
