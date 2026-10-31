package com.moveinsync.config.server.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.codec.SnapshotCodec;
import com.moveinsync.config.core.model.ChangeSet;
import java.util.List;
import java.util.UUID;

public record SyncResponseDto(
        String mode,
        UUID epoch,
        Long fromVersion,
        Long toVersion,
        Long version,
        List<JsonNode> changeSets,
        JsonNode snapshot
) {
    private static final SnapshotCodec CODEC = new SnapshotCodec();

    public static SyncResponseDto delta(UUID epoch, long fromVersion, long toVersion, List<ChangeSet> changeSets) {
        List<JsonNode> encoded = changeSets != null
                ? changeSets.stream().map(CODEC::encode).toList()
                : List.of();
        return new SyncResponseDto("DELTA", epoch, fromVersion, toVersion, toVersion, encoded, null);
    }

    public static SyncResponseDto full(UUID epoch, long version, JsonNode snapshot) {
        return new SyncResponseDto("FULL", epoch, null, null, version, null, snapshot);
    }
}
