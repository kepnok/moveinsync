package com.moveinsync.config.sdk.stats;

public record ClientStats(
        long version,
        long lastSyncAtMillis,
        long stalenessMillis,
        long syncs,
        long syncFailures,
        long staleNotificationsIgnored,
        long duplicatesIgnored,
        long fullResyncs,
        long deltaApplied,
        long diskLoads,
        boolean healthy
) {}
