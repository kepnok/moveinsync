package com.moveinsync.config.sdk.sync;

import com.moveinsync.config.core.model.ConfigSnapshot;
import com.moveinsync.config.sdk.api.ServerApi;
import com.moveinsync.config.sdk.api.SyncResponse;
import com.moveinsync.config.sdk.api.VersionResponse;
import com.moveinsync.config.sdk.stats.ClientMetrics;
import com.moveinsync.config.sdk.store.SnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SyncManager.class);

    private final ServerApi serverApi;
    private final SnapshotHolder snapshotHolder;
    private final SnapshotStore snapshotStore;
    private final ClientMetrics metrics;
    private final DeltaApplier deltaApplier = new DeltaApplier();
    private final BackoffPolicy backoff = new BackoffPolicy();

    private final List<String> tenants;
    private final Duration pollInterval;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "config-sync-manager");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, AtomicBoolean> syncPending = new ConcurrentHashMap<>();
    private final Map<String, AutoCloseable> streamHandles = new ConcurrentHashMap<>();
    private final List<com.moveinsync.config.sdk.ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public void addListener(com.moveinsync.config.sdk.ConfigChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public SyncManager(
            ServerApi serverApi,
            SnapshotHolder snapshotHolder,
            SnapshotStore snapshotStore,
            ClientMetrics metrics,
            List<String> tenants,
            Duration pollInterval
    ) {
        this.serverApi = serverApi;
        this.snapshotHolder = snapshotHolder;
        this.snapshotStore = snapshotStore;
        this.metrics = metrics;
        this.tenants = tenants != null && !tenants.isEmpty() ? List.copyOf(tenants) : List.of("GLOBAL");
        this.pollInterval = pollInterval != null ? pollInterval : Duration.ofSeconds(2);

        for (String t : this.tenants) {
            syncPending.put(t, new AtomicBoolean(false));
        }
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        // 1. Initial sync for all tenants
        for (String tenantId : tenants) {
            // Attempt to load from disk cache first
            if (snapshotStore != null) {
                snapshotStore.loadLatestValid(tenantId).ifPresent(diskSnap -> {
                    snapshotHolder.swap(tenantId, diskSnap);
                    metrics.recordDiskLoad(diskSnap.version());
                    log.info("Loaded initial snapshot from disk for tenant {} version {}", tenantId, diskSnap.version());
                });
            }

            // Sync with server immediately
            try {
                syncOnce(tenantId);
            } catch (Exception e) {
                log.warn("Initial sync failed for tenant {}: {}", tenantId, e.getMessage());
            }

            // Start SSE stream for tenant
            startStreamForTenant(tenantId);
        }

        // 2. Schedule periodic jittered poll
        scheduleNextPoll();
    }

    private void scheduleNextPoll() {
        if (!running.get()) return;

        // Jitter: pollInterval ± 20%
        long baseMillis = pollInterval.toMillis();
        long jitter = (long) (baseMillis * 0.20 * (ThreadLocalRandom.current().nextDouble() * 2 - 1));
        long delay = Math.max(500, baseMillis + jitter);

        scheduler.schedule(() -> {
            if (running.get()) {
                pollAllTenants();
                scheduleNextPoll();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void pollAllTenants() {
        for (String tenantId : tenants) {
            try {
                ConfigSnapshot current = snapshotHolder.get(tenantId);
                long currentVersion = current != null ? current.version() : 0L;

                VersionResponse ver = serverApi.fetchVersion(tenantId);
                if (ver.version() > currentVersion) {
                    requestSync(tenantId, "POLL_DETECTED_NEW_VERSION");
                }
            } catch (Exception e) {
                log.debug("Poll error for tenant {}: {}", tenantId, e.getMessage());
            }
        }
    }

    private void startStreamForTenant(String tenantId) {
        if (!running.get()) return;

        AutoCloseable existing = streamHandles.remove(tenantId);
        if (existing != null) {
            try { existing.close(); } catch (Exception ignored) {}
        }

        AutoCloseable handle = serverApi.openStream(
                tenantId,
                version -> onNotification(tenantId, version),
                () -> {
                    // On disconnect: back off and reconnect
                    if (running.get()) {
                        Duration delay = backoff.nextDelay();
                        scheduler.schedule(() -> startStreamForTenant(tenantId), delay.toMillis(), TimeUnit.MILLISECONDS);
                    }
                }
        );

        streamHandles.put(tenantId, handle);
    }

    public void onNotification(String tenantId, long version) {
        ConfigSnapshot current = snapshotHolder.get(tenantId);
        long currentVersion = current != null ? current.version() : 0L;

        // Spec rule: Notification n <= current: Ignore. Increment staleNotificationsIgnored.
        if (current != null && version <= currentVersion) {
            metrics.recordStaleNotification();
            return;
        }

        // Notification n > current: requestSync (coalesced)
        requestSync(tenantId, "SSE_NOTIFICATION");
    }

    public void requestSync(String tenantId, String reason) {
        AtomicBoolean pending = syncPending.computeIfAbsent(tenantId, k -> new AtomicBoolean(false));
        if (pending.compareAndSet(false, true)) {
            scheduler.submit(() -> {
                try {
                    syncOnce(tenantId);
                } finally {
                    pending.set(false);
                }
            });
        } else {
            metrics.recordDuplicateIgnored();
        }
    }

    public synchronized void syncOnce(String tenantId) {
        ConfigSnapshot current = snapshotHolder.get(tenantId);
        long since = current != null ? current.version() : 0L;
        UUID epoch = current != null ? current.epoch() : null;

        try {
            SyncResponse response;
            if (current == null) {
                // Initial fetch requires full snapshot
                response = serverApi.fetchSnapshot(tenantId);
                metrics.recordFullResync();
            } else {
                response = serverApi.fetchChanges(tenantId, since, epoch);
            }

            DeltaApplier.ApplyResult applyResult = deltaApplier.apply(current, response);

            if (applyResult instanceof DeltaApplier.ApplyResult.ResyncRequired resync) {
                // Gap or epoch mismatch -> fetch full snapshot
                log.info("Full resync required for tenant {}: {}", tenantId, resync.reason());
                metrics.recordFullResync();
                response = serverApi.fetchSnapshot(tenantId);
                applyResult = deltaApplier.apply(current, response);
            }

            if (applyResult instanceof DeltaApplier.ApplyResult.Updated updated) {
                ConfigSnapshot candidate = updated.candidate();

                // Atomic state swap - the ONE visible state change
                snapshotHolder.swap(tenantId, candidate);

                for (com.moveinsync.config.sdk.ConfigChangeListener listener : listeners) {
                    try {
                        listener.onChange(candidate);
                    } catch (Exception ex) {
                        log.warn("Error in ConfigChangeListener for tenant {}: {}", tenantId, ex.getMessage());
                    }
                }

                // Persist snapshot to disk
                if (snapshotStore != null) {
                    try {
                        snapshotStore.save(tenantId, candidate);
                    } catch (Exception e) {
                        log.warn("Failed to persist snapshot to disk for tenant {}: {}", tenantId, e.getMessage());
                    }
                }

                if ("DELTA".equalsIgnoreCase(response.mode())) {
                    metrics.recordDeltaApplied();
                }

                metrics.recordSyncSuccess(candidate.version());
                backoff.reset();
            } else if (applyResult instanceof DeltaApplier.ApplyResult.Ignored ignored) {
                log.debug("Sync response ignored for tenant {}: {}", tenantId, ignored.reason());
            }

        } catch (Exception e) {
            metrics.recordSyncFailure();
            log.warn("Sync failed for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    @Override
    public void close() {
        running.set(false);
        for (AutoCloseable h : streamHandles.values()) {
            try { h.close(); } catch (Exception ignored) {}
        }
        streamHandles.clear();
        scheduler.shutdownNow();
    }
}
