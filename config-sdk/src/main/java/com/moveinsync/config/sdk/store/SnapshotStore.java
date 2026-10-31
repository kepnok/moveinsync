package com.moveinsync.config.sdk.store;

import com.moveinsync.config.core.model.ConfigSnapshot;
import java.util.Optional;

public interface SnapshotStore {
    void save(String tenantId, ConfigSnapshot snapshot);
    Optional<ConfigSnapshot> loadLatestValid(String tenantId);
}

