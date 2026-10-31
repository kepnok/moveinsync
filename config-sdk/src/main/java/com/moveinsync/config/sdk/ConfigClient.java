package com.moveinsync.config.sdk;

import com.moveinsync.config.core.model.ConfigContext;
import com.moveinsync.config.core.model.ConfigResolver;
import com.moveinsync.config.core.model.ConfigSnapshot;
import com.moveinsync.config.core.model.ConfigValue;
import com.moveinsync.config.core.model.ExplainResult;
import com.moveinsync.config.core.model.TimeWindowValue;
import com.moveinsync.config.sdk.stats.ClientMetrics;
import com.moveinsync.config.sdk.stats.ClientStats;
import com.moveinsync.config.sdk.sync.SnapshotHolder;
import com.moveinsync.config.sdk.sync.SyncManager;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ConfigClient implements AutoCloseable {
    private final String defaultTenantId;
    private final SnapshotHolder snapshotHolder;
    private final SyncManager syncManager;
    private final ConfigResolver resolver;
    private final ClientMetrics metrics;

    ConfigClient(
            String defaultTenantId,
            SnapshotHolder snapshotHolder,
            SyncManager syncManager,
            ConfigResolver resolver,
            ClientMetrics metrics
    ) {
        this.defaultTenantId = (defaultTenantId != null && !defaultTenantId.isBlank()) ? defaultTenantId : "GLOBAL";
        this.snapshotHolder = Objects.requireNonNull(snapshotHolder, "snapshotHolder cannot be null");
        this.syncManager = Objects.requireNonNull(syncManager, "syncManager cannot be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
    }

    public static ConfigClientBuilder builder() {
        return new ConfigClientBuilder();
    }

    public ConfigView view() {
        return view(defaultTenantId);
    }

    public ConfigView view(String tenantId) {
        String targetTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : defaultTenantId;
        ConfigSnapshot snapshot = snapshotHolder.get(targetTenant);
        if (snapshot == null) {
            throw new ConfigUnavailableException("Configuration unavailable for tenant: " + targetTenant);
        }
        return new ConfigView(snapshot, resolver);
    }

    public long getLong(String key, ConfigContext ctx) {
        return view().getLong(key, ctx);
    }

    public Duration getDuration(String key, ConfigContext ctx) {
        return view().getDuration(key, ctx);
    }

    public TimeWindowValue getTimeWindow(String key, ConfigContext ctx) {
        return view().getTimeWindow(key, ctx);
    }

    public String getString(String key, ConfigContext ctx) {
        return view().getString(key, ctx);
    }

    public <T> List<T> getList(String key, ConfigContext ctx, Class<T> elemType) {
        return view().getList(key, ctx, elemType);
    }

    public Optional<ConfigValue> get(String key, ConfigContext ctx) {
        return view().get(key, ctx);
    }

    public ExplainResult explain(String key, ConfigContext ctx) {
        return view().explain(key, ctx);
    }

    public long version() {
        ConfigSnapshot snap = snapshotHolder.get(defaultTenantId);
        return snap != null ? snap.version() : 0L;
    }

    public boolean isReady() {
        return snapshotHolder.hasSnapshot(defaultTenantId);
    }

    public ClientStats stats() {
        return metrics.snapshot();
    }

    public void addListener(ConfigChangeListener listener) {
        syncManager.addListener(listener);
    }

    @Override
    public void close() {
        syncManager.close();
    }
}
