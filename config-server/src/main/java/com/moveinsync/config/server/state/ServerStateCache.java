package com.moveinsync.config.server.state;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.moveinsync.config.core.model.*;
import com.moveinsync.config.server.repository.ConfigHeadRepository;
import com.moveinsync.config.server.repository.DefinitionRepository;
import com.moveinsync.config.server.repository.OverrideRepository;
import com.moveinsync.config.server.repository.ChangeSetRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ServerStateCache {
    private static final Logger log = LoggerFactory.getLogger(ServerStateCache.class);

    private final ConfigHeadRepository headRepository;
    private final DefinitionRepository definitionRepository;
    private final OverrideRepository overrideRepository;
    private final ChangeSetRepository changeSetRepository;
    private final ChangeLog changeLog;

    private volatile ConfigSnapshot headSnapshot;
    private final Cache<TenantVersionKey, ConfigSnapshot> tenantCache;
    private final AtomicLong reloadCounter = new AtomicLong(0);

    public record TenantVersionKey(String tenantId, long version) {}

    public ServerStateCache(
            ConfigHeadRepository headRepository,
            DefinitionRepository definitionRepository,
            OverrideRepository overrideRepository,
            ChangeSetRepository changeSetRepository,
            ChangeLog changeLog
    ) {
        this.headRepository = headRepository;
        this.definitionRepository = definitionRepository;
        this.overrideRepository = overrideRepository;
        this.changeSetRepository = changeSetRepository;
        this.changeLog = changeLog;

        this.tenantCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
    }

    @PostConstruct
    public void init() {
        reloadFromDb();
    }

    public ConfigSnapshot head() {
        return headSnapshot;
    }

    public synchronized void reloadFromDb() {
        log.info("Reloading ServerStateCache from database...");
        reloadCounter.incrementAndGet();

        ConfigHeadRepository.Head headRow = headRepository.getHead();
        List<SettingDefinition> defsList = definitionRepository.findAll();
        Map<String, SettingDefinition> defsMap = new HashMap<>(defsList.size());
        for (SettingDefinition def : defsList) {
            defsMap.put(def.key(), def);
        }

        List<OverrideRepository.OverrideRow> overrideRows = overrideRepository.findAll(defsMap);
        Map<String, Map<ScopeRef, ConfigValue>> overridesMap = new HashMap<>();
        for (OverrideRepository.OverrideRow row : overrideRows) {
            overridesMap.computeIfAbsent(row.settingKey(), k -> new HashMap<>())
                    .put(row.scope(), row.value());
        }

        this.headSnapshot = new ConfigSnapshot(headRow.epoch(), headRow.version(), defsMap, overridesMap);

        // Load recent change sets into ChangeLog
        List<ChangeSet> recent = changeSetRepository.findRecent(5000, defsMap);
        changeLog.load(recent);

        log.info("ServerStateCache reloaded successfully at epoch {} version {}", headRow.epoch(), headRow.version());
    }

    public void advance(ConfigSnapshot next) {
        Objects.requireNonNull(next, "next snapshot cannot be null");
        this.headSnapshot = next;
    }

    public ConfigSnapshot tenantSnapshot(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || "*".equals(tenantId)) {
            return headSnapshot;
        }

        ConfigSnapshot currentHead = this.headSnapshot;
        TenantVersionKey key = new TenantVersionKey(tenantId, currentHead.version());

        return tenantCache.get(key, k -> currentHead.forTenant(k.tenantId()));
    }

    public long reloadCount() {
        return reloadCounter.get();
    }
}

