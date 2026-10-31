package com.moveinsync.config.server.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.codec.DiffEntry;

public record DiffEntryDto(
        String settingKey,
        String scope,
        JsonNode before,
        JsonNode after
) {
    public static DiffEntryDto from(DiffEntry entry) {
        return new DiffEntryDto(
                entry.settingKey(),
                entry.scope().key(),
                entry.before() != null ? entry.before().toJson() : null,
                entry.after() != null ? entry.after().toJson() : null
        );
    }
}

