package com.moveinsync.config.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.codec.SnapshotCodec;
import com.moveinsync.config.core.model.ConfigSnapshot;
import com.moveinsync.config.server.dto.SyncResponseDto;
import com.moveinsync.config.server.state.ServerStateCache;
import org.springframework.stereotype.Service;

@Service
public class SnapshotService {
    private final ServerStateCache stateCache;
    private final SnapshotCodec snapshotCodec;

    public SnapshotService(ServerStateCache stateCache, SnapshotCodec snapshotCodec) {
        this.stateCache = stateCache;
        this.snapshotCodec = snapshotCodec;
    }

    public SyncResponseDto fullSnapshot(String tenantId) {
        ConfigSnapshot snapshot = stateCache.tenantSnapshot(tenantId);
        JsonNode jsonNode = snapshotCodec.encodeToJson(snapshot);
        return SyncResponseDto.full(snapshot.epoch(), snapshot.version(), jsonNode);
    }

    public ConfigSnapshot getRawSnapshot(String tenantId) {
        return stateCache.tenantSnapshot(tenantId);
    }
}
