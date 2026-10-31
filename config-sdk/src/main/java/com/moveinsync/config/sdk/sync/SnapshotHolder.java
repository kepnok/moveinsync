package com.moveinsync.config.sdk.sync;

import com.moveinsync.config.core.model.ConfigSnapshot;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class SnapshotHolder {
    private final ConcurrentHashMap<String, AtomicReference<ConfigSnapshot>> tenantSnapshots = new ConcurrentHashMap<>();

    public ConfigSnapshot get(String tenantId) {
        String key = (tenantId != null && !tenantId.isBlank()) ? tenantId : "GLOBAL";
        AtomicReference<ConfigSnapshot> ref = tenantSnapshots.get(key);
        return ref != null ? ref.get() : null;
    }

    public void swap(String tenantId, ConfigSnapshot next) {
        Objects.requireNonNull(next, "next snapshot cannot be null");
        String key = (tenantId != null && !tenantId.isBlank()) ? tenantId : "GLOBAL";
        tenantSnapshots.computeIfAbsent(key, k -> new AtomicReference<>()).set(next);
    }

    public boolean hasSnapshot(String tenantId) {
        return get(tenantId) != null;
    }
}

