package com.moveinsync.config.sdk.api;

import java.util.UUID;
import java.util.function.Consumer;

public interface ServerApi extends AutoCloseable {
    SyncResponse fetchSnapshot(String tenantId);
    SyncResponse fetchChanges(String tenantId, long sinceVersion, UUID epoch);
    VersionResponse fetchVersion(String tenantId);
    AutoCloseable openStream(String tenantId, Consumer<Long> onVersionNotification, Runnable onDisconnect);

    @Override
    default void close() {}
}

