package com.moveinsync.config.sdk;

import com.moveinsync.config.core.model.ConfigResolver;
import com.moveinsync.config.core.model.ConfigSnapshot;
import com.moveinsync.config.sdk.api.HttpServerApi;
import com.moveinsync.config.sdk.api.ServerApi;
import com.moveinsync.config.sdk.stats.ClientMetrics;
import com.moveinsync.config.sdk.store.FileSnapshotStore;
import com.moveinsync.config.sdk.store.SnapshotStore;
import com.moveinsync.config.sdk.sync.SnapshotHolder;
import com.moveinsync.config.sdk.sync.SyncManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

public class ConfigClientBuilder {
    private static final Logger log = LoggerFactory.getLogger(ConfigClientBuilder.class);

    private String serverUrl = "http://localhost:8080";
    private String serviceId = "sdk-client";
    private String tenantId = "GLOBAL";
    private final List<String> tenants = new ArrayList<>();
    private TokenProvider tokenProvider;
    private Path cacheDirectory;
    private Duration pollInterval = Duration.ofSeconds(30);
    private Duration initialSyncTimeout = Duration.ofSeconds(5);
    private boolean failFast = true;
    private boolean startEmpty = false;
    private HttpClient httpClient;
    private Clock clock = Clock.systemUTC();
    private ServerApi customServerApi;
    private SnapshotStore customSnapshotStore;

    public ConfigClientBuilder serverUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        return this;
    }

    public ConfigClientBuilder serviceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public ConfigClientBuilder tenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public ConfigClientBuilder tenants(Collection<String> tenants) {
        this.tenants.clear();
        if (tenants != null) {
            this.tenants.addAll(tenants);
        }
        return this;
    }

    public ConfigClientBuilder tokenProvider(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
        return this;
    }

    public ConfigClientBuilder cacheDirectory(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
        return this;
    }

    public ConfigClientBuilder pollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
        return this;
    }

    public ConfigClientBuilder initialSyncTimeout(Duration initialSyncTimeout) {
        this.initialSyncTimeout = initialSyncTimeout;
        return this;
    }

    public ConfigClientBuilder failFast(boolean failFast) {
        this.failFast = failFast;
        return this;
    }

    public ConfigClientBuilder startEmpty(boolean startEmpty) {
        this.startEmpty = startEmpty;
        return this;
    }

    public ConfigClientBuilder httpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        return this;
    }

    public ConfigClientBuilder clock(Clock clock) {
        this.clock = clock;
        return this;
    }

    public ConfigClientBuilder serverApi(ServerApi serverApi) {
        this.customServerApi = serverApi;
        return this;
    }

    public ConfigClientBuilder snapshotStore(SnapshotStore snapshotStore) {
        this.customSnapshotStore = snapshotStore;
        return this;
    }

    public ConfigClient build() {
        List<String> targetTenants = new ArrayList<>(tenants);
        if (targetTenants.isEmpty()) {
            targetTenants.add((tenantId != null && !tenantId.isBlank()) ? tenantId : "GLOBAL");
        }

        SnapshotStore store = customSnapshotStore != null ? customSnapshotStore :
                (cacheDirectory != null ? new FileSnapshotStore(cacheDirectory) : null);

        ServerApi api = customServerApi != null ? customServerApi :
                new HttpServerApi(serverUrl, serviceId, tokenProvider, initialSyncTimeout, httpClient);

        SnapshotHolder holder = new SnapshotHolder();
        ClientMetrics metrics = new ClientMetrics(clock);

        SyncManager syncManager = new SyncManager(
                api,
                holder,
                store,
                metrics,
                targetTenants,
                pollInterval
        );

        // Start sync manager: loads disk cache, attempts initial sync, starts SSE & poll
        syncManager.start();

        // Check readiness for all target tenants
        for (String t : targetTenants) {
            if (!holder.hasSnapshot(t)) {
                if (startEmpty) {
                    holder.swap(t, ConfigSnapshot.empty(UUID.randomUUID()));
                    log.info("Initialized empty snapshot at version 0 for tenant {}", t);
                } else if (failFast) {
                    syncManager.close();
                    throw new ConfigUnavailableException("Failed to initialize configuration from server or cache for tenant: " + t);
                }
            }
        }

        return new ConfigClient(
                targetTenants.get(0),
                holder,
                syncManager,
                new ConfigResolver(),
                metrics
        );
    }
}
