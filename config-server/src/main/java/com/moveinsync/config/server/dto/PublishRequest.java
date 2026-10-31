package com.moveinsync.config.server.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record PublishRequest(
        String reason,
        List<PublishItemDto> items,
        Boolean dryRun
) {}

