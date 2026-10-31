package com.moveinsync.config.server.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.model.ChangeSet;
import java.util.List;
import java.util.UUID;

public record SyncResponseDto(
        String mode,
        UUID epoch,
        Long fromVersion,
        Long toVersion,
        Long version,
        List<ChangeSet> changeSets,
        JsonNode snapshot
) {
    public static SyncResponseDto delta(UUID epoch, long fromVersion, long toVersion, List<ChangeSet> changeSets) {
        return new SyncResponseDto("DELTA", epoch, fromVersion, toVersion, toVersion, changeSets, null);
    }

    public static SyncResponseDto full(UUID epoch, long version, JsonNode snapshot) {
        return new SyncResponseDto("FULL", epoch, null, null, version, null, snapshot);
    }
}
