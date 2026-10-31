package com.moveinsync.config.sdk.stats;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

public class ClientMetrics {
    private final Clock clock;
    private final AtomicLong version = new AtomicLong(0);
    private final AtomicLong lastSyncAtMillis = new AtomicLong(0);
    private final AtomicLong syncs = new AtomicLong(0);
    private final AtomicLong syncFailures = new AtomicLong(0);
    private final AtomicLong staleNotificationsIgnored = new AtomicLong(0);
    private final AtomicLong duplicatesIgnored = new AtomicLong(0);
    private final AtomicLong fullResyncs = new AtomicLong(0);
    private final AtomicLong deltaApplied = new AtomicLong(0);
    private final AtomicLong diskLoads = new AtomicLong(0);

    public ClientMetrics(Clock clock) {
        this.clock = clock;
    }

    public void recordSyncSuccess(long newVersion) {
        syncs.incrementAndGet();
        version.set(newVersion);
        lastSyncAtMillis.set(clock.millis());
    }

    public void recordSyncFailure() {
        syncFailures.incrementAndGet();
    }

    public void recordStaleNotification() {
        staleNotificationsIgnored.incrementAndGet();
    }

    public void recordDuplicateIgnored() {
        duplicatesIgnored.incrementAndGet();
    }

    public void recordFullResync() {
        fullResyncs.incrementAndGet();
    }

    public void recordDeltaApplied() {
        deltaApplied.incrementAndGet();
    }

    public void recordDiskLoad(long loadedVersion) {
        diskLoads.incrementAndGet();
        version.set(loadedVersion);
        lastSyncAtMillis.set(clock.millis());
    }

    public ClientStats snapshot() {
        long now = clock.millis();
        long last = lastSyncAtMillis.get();
        long staleness = last > 0 ? Math.max(0, now - last) : 0;
        boolean healthy = last > 0 && (now - last) < 15000;

        return new ClientStats(
                version.get(),
                last,
                staleness,
                syncs.get(),
                syncFailures.get(),
                staleNotificationsIgnored.get(),
                duplicatesIgnored.get(),
                fullResyncs.get(),
                deltaApplied.get(),
                diskLoads.get(),
                healthy
        );
    }

    public long version() {
        return version.get();
    }

    public long staleNotificationsIgnored() {
        return staleNotificationsIgnored.get();
    }

    public long syncFailures() {
        return syncFailures.get();
    }
}
