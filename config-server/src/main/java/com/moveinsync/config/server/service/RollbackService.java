package com.moveinsync.config.server.service;

import com.moveinsync.config.core.codec.DiffEntry;
import com.moveinsync.config.core.codec.SnapshotDiff;
import com.moveinsync.config.server.exception.BadRequestException;
import com.moveinsync.config.core.model.*;
import com.moveinsync.config.server.dto.DiffEntryDto;
import com.moveinsync.config.server.dto.PublishResponse;
import com.moveinsync.config.server.dto.RollbackRequest;
import com.moveinsync.config.server.repository.ChangeSetRepository;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.security.Role;
import com.moveinsync.config.server.security.TenantGuard;
import com.moveinsync.config.server.state.ServerStateCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class RollbackService {

    private final ChangeSetRepository changeSetRepository;
    private final ChangeSetService changeSetService;
    private final ServerStateCache stateCache;
    private final TenantGuard tenantGuard;

    public RollbackService(
            ChangeSetRepository changeSetRepository,
            ChangeSetService changeSetService,
            ServerStateCache stateCache,
            TenantGuard tenantGuard
    ) {
        this.changeSetRepository = changeSetRepository;
        this.changeSetService = changeSetService;
        this.stateCache = stateCache;
        this.tenantGuard = tenantGuard;
    }

    public List<DiffEntryDto> preview(long targetVersion, String tenantId, AuthPrincipal principal) {
        tenantGuard.assertRole(principal, Role.OPERATOR, Role.ADMIN);
        if (tenantId != null && !tenantId.isBlank()) {
            tenantGuard.assertTenant(principal, tenantId);
        }

        ConfigSnapshot current = stateCache.head();
        if (targetVersion < 0 || targetVersion >= current.version()) {
            throw new BadRequestException("Target version must be >= 0 and < current version " + current.version());
        }

        List<ChangeItem> inverseItems = computeRollbackItems(current, targetVersion, tenantId);
        if (inverseItems.isEmpty()) {
            return List.of();
        }

        ChangeSet previewCs = new ChangeSet(
                current.version() + 1,
                UUID.randomUUID(),
                ChangeKind.ROLLBACK,
                principal.subject(),
                "Preview rollback to version " + targetVersion,
                Instant.now(),
                inverseItems,
                null,
                targetVersion
        );

        ConfigSnapshot candidate = current.withChangeSet(previewCs);
        List<DiffEntry> diff = SnapshotDiff.diff(current, candidate);
        return diff.stream().map(DiffEntryDto::from).toList();
    }

    @Transactional
    public PublishResponse rollback(RollbackRequest req, String idempotencyKey, AuthPrincipal principal) {
        tenantGuard.assertRole(principal, Role.OPERATOR, Role.ADMIN);
        if (req.tenantId() != null && !req.tenantId().isBlank()) {
            tenantGuard.assertTenant(principal, req.tenantId());
        }

        ConfigSnapshot current = stateCache.head();
        if (req.targetVersion() < 0 || req.targetVersion() >= current.version()) {
            throw new BadRequestException("Target version must be >= 0 and < current version " + current.version());
        }

        List<ChangeItem> inverseItems = computeRollbackItems(current, req.targetVersion(), req.tenantId());
        if (inverseItems.isEmpty()) {
            return PublishResponse.noChange(current.version());
        }

        String reason = (req.reason() != null && !req.reason().isBlank())
                ? req.reason()
                : "Rollback to version " + req.targetVersion();

        return changeSetService.executePublishPipeline(
                ChangeKind.ROLLBACK,
                principal.subject(),
                reason,
                inverseItems,
                idempotencyKey,
                false,
                null,
                req.targetVersion()
        );
    }

    private List<ChangeItem> computeRollbackItems(ConfigSnapshot current, long targetVersion, String tenantId) {
        List<ChangeSet> range = changeSetRepository.findRange(targetVersion + 1, current.version(), current.definitions());

        Map<String, ConfigValue> earliestOldValues = new LinkedHashMap<>();
        Map<String, ScopeRef> scopeMap = new HashMap<>();
        Map<String, String> settingMap = new HashMap<>();
        Map<String, SettingDefinition> earliestOldDefs = new LinkedHashMap<>();

        // Walk in ascending version order so the first time we see a key, its oldValue was the state at targetVersion
        for (ChangeSet cs : range) {
            for (ChangeItem item : cs.items()) {
                if (item instanceof OverrideChange oc) {
                    if (tenantId != null && !tenantId.isBlank()) {
                        String itemTenant = oc.scope().tenantId();
                        if (itemTenant != null && !itemTenant.equals(tenantId)) {
                            continue;
                        }
                    }
                    String compositeKey = oc.settingKey() + "::" + oc.scope().key();
                    if (!earliestOldValues.containsKey(compositeKey)) {
                        earliestOldValues.put(compositeKey, oc.oldValue());
                        scopeMap.put(compositeKey, oc.scope());
                        settingMap.put(compositeKey, oc.settingKey());
                    }
                } else if (item instanceof DefinitionChange dc) {
                    if (!earliestOldDefs.containsKey(dc.settingKey())) {
                        earliestOldDefs.put(dc.settingKey(), dc.oldDef());
                    }
                }
            }
        }

        List<ChangeItem> inverse = new ArrayList<>();

        for (Map.Entry<String, ConfigValue> entry : earliestOldValues.entrySet()) {
            String compositeKey = entry.getKey();
            ConfigValue targetVal = entry.getValue();
            ScopeRef scope = scopeMap.get(compositeKey);
            String settingKey = settingMap.get(compositeKey);

            ConfigValue currentVal = current.overrideAt(settingKey, scope).orElse(null);
            if (!Objects.equals(targetVal, currentVal)) {
                inverse.add(new OverrideChange(settingKey, scope, currentVal, targetVal));
            }
        }

        for (Map.Entry<String, SettingDefinition> entry : earliestOldDefs.entrySet()) {
            String key = entry.getKey();
            SettingDefinition targetDef = entry.getValue();
            SettingDefinition currentDef = current.definitions().get(key);

            if (!Objects.equals(targetDef, currentDef) && targetDef != null) {
                inverse.add(new DefinitionChange(key, currentDef, targetDef));
            }
        }

        return inverse;
    }
}
