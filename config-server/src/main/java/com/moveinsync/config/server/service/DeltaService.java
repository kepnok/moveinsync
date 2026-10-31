package com.moveinsync.config.server.service;

import com.moveinsync.config.core.model.ConfigSnapshot;
import com.moveinsync.config.server.dto.SyncResponseDto;
import com.moveinsync.config.server.state.ChangeLog;
import com.moveinsync.config.server.state.DeltaResult;
import com.moveinsync.config.server.state.ServerStateCache;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeltaService {
    private final ServerStateCache stateCache;
    private final ChangeLog changeLog;
    private final SnapshotService snapshotService;

    public DeltaService(ServerStateCache stateCache, ChangeLog changeLog, SnapshotService snapshotService) {
        this.stateCache = stateCache;
        this.changeLog = changeLog;
        this.snapshotService = snapshotService;
    }

    public SyncResponseDto changes(String tenantId, long sinceVersion, UUID clientEpoch) {
        ConfigSnapshot head = stateCache.head();

        // Correction 7: On epoch change, force full snapshot
        if (clientEpoch != null && !clientEpoch.equals(head.epoch())) {
            return snapshotService.fullSnapshot(tenantId);
        }

        DeltaResult result = changeLog.since(tenantId, sinceVersion);

        if (result instanceof DeltaResult.Delta d) {
            return SyncResponseDto.delta(head.epoch(), d.fromVersion(), d.toVersion(), d.changeSets());
        } else {
            // FULL_REQUIRED: Client is too far behind the ring buffer or gap
            return snapshotService.fullSnapshot(tenantId);
        }
    }
}

