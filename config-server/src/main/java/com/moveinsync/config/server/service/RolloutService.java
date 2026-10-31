package com.moveinsync.config.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.server.exception.BadRequestException;
import com.moveinsync.config.server.exception.NotFoundException;
import com.moveinsync.config.core.exception.ValidationFailedException;
import com.moveinsync.config.core.model.*;
import com.moveinsync.config.core.strategy.ValueTypeRegistry;
import com.moveinsync.config.server.dto.*;
import com.moveinsync.config.server.repository.ChangeSetRepository;
import com.moveinsync.config.server.repository.RolloutRepository;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.security.Role;
import com.moveinsync.config.server.security.TenantGuard;
import com.moveinsync.config.server.state.ServerStateCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class RolloutService {

    private final RolloutRepository rolloutRepository;
    private final ChangeSetRepository changeSetRepository;
    private final ChangeSetService changeSetService;
    private final ServerStateCache stateCache;
    private final ValueTypeRegistry typeRegistry;
    private final TenantGuard tenantGuard;

    public RolloutService(
            RolloutRepository rolloutRepository,
            ChangeSetRepository changeSetRepository,
            ChangeSetService changeSetService,
            ServerStateCache stateCache,
            ValueTypeRegistry typeRegistry,
            TenantGuard tenantGuard
    ) {
        this.rolloutRepository = rolloutRepository;
        this.changeSetRepository = changeSetRepository;
        this.changeSetService = changeSetService;
        this.stateCache = stateCache;
        this.typeRegistry = typeRegistry;
        this.tenantGuard = tenantGuard;
    }

    @Transactional
    public RolloutResponse create(CreateRolloutRequest req, AuthPrincipal principal) {
        tenantGuard.assertRole(principal, Role.OPERATOR, Role.ADMIN);

        if (req.officeIds() == null || req.officeIds().isEmpty()) {
            throw new BadRequestException("officeIds cannot be empty for staged rollout");
        }

        ConfigSnapshot state = stateCache.head();
        SettingDefinition def = state.definitions().get(req.settingKey());
        if (def == null) {
            throw new NotFoundException("Setting '" + req.settingKey() + "' not found");
        }

        ConfigValue parsedValue = typeRegistry.parse(def, req.value());

        // Derive tenant from office IDs and assert access
        String tenantId = null;
        List<ScopeRef> scopes = new ArrayList<>();
        for (String officeId : req.officeIds()) {
            ScopeRef scope = ScopeRef.office(officeId);
            scopes.add(scope);
            if (tenantId == null) {
                tenantId = scope.tenantId();
            } else if (!tenantId.equals(scope.tenantId())) {
                throw new BadRequestException("All rollout office targets must belong to the same tenant: " + tenantId);
            }
        }
        tenantGuard.assertScopes(principal, scopes);

        UUID rolloutId = UUID.randomUUID();
        List<ChangeItem> items = new ArrayList<>();
        for (ScopeRef scope : scopes) {
            items.add(new OverrideChange(req.settingKey(), scope, null, parsedValue));
        }

        String reason = (req.reason() != null && !req.reason().isBlank())
                ? req.reason()
                : "Stage rollout " + rolloutId + " for " + req.settingKey();

        PublishResponse pubResp = changeSetService.executePublishPipeline(
                ChangeKind.ROLLOUT_STAGE,
                principal.subject(),
                reason,
                items,
                null,
                false,
                rolloutId,
                null
        );

        RolloutRepository.RolloutRecord record = new RolloutRepository.RolloutRecord(
                rolloutId,
                "ACTIVE",
                tenantId,
                pubResp.version(),
                null,
                principal.subject(),
                Instant.now(),
                new HashSet<>(req.officeIds())
        );
        rolloutRepository.insert(record);

        return RolloutResponse.from(record);
    }

    @Transactional
    public RolloutResponse promote(UUID rolloutId, PromoteRolloutRequest req, AuthPrincipal principal) {
        tenantGuard.assertRole(principal, Role.OPERATOR, Role.ADMIN);

        RolloutRepository.RolloutRecord record = rolloutRepository.findById(rolloutId)
                .orElseThrow(() -> new NotFoundException("Rollout not found: " + rolloutId));

        if (!"ACTIVE".equals(record.status())) {
            throw new BadRequestException("Cannot promote rollout with status " + record.status());
        }

        tenantGuard.assertTenant(principal, record.tenantId());

        ConfigSnapshot state = stateCache.head();
        ChangeSet stageCs = changeSetRepository.findByVersion(record.stageVersion(), state.definitions())
                .orElseThrow(() -> new NotFoundException("Stage change set not found for version: " + record.stageVersion()));

        if (stageCs.items().isEmpty() || !(stageCs.items().get(0) instanceof OverrideChange oc)) {
            throw new IllegalStateException("Corrupted stage change set: missing override item");
        }

        String settingKey = oc.settingKey();
        ConfigValue stagedValue = oc.newValue();

        ScopeRef targetScope = (req != null && req.targetScope() != null)
                ? req.targetScope().toScopeRef()
                : ScopeRef.client(record.tenantId());

        List<ChangeItem> items = new ArrayList<>();
        // 1. Write the override at the broader target scope
        items.add(new OverrideChange(settingKey, targetScope, null, stagedValue));

        // 2. Clear the office-level overrides so they fall back to targetScope
        for (String officeId : record.targetOffices()) {
            items.add(new OverrideChange(settingKey, ScopeRef.office(officeId), null, null));
        }

        String reason = (req != null && req.reason() != null && !req.reason().isBlank())
                ? req.reason()
                : "Promote rollout " + rolloutId + " to " + targetScope.key();

        PublishResponse pubResp = changeSetService.executePublishPipeline(
                ChangeKind.ROLLOUT_PROMOTE,
                principal.subject(),
                reason,
                items,
                null,
                false,
                rolloutId,
                null
        );

        rolloutRepository.updateStatus(rolloutId, "PROMOTED", pubResp.version());

        return RolloutResponse.from(rolloutRepository.findById(rolloutId).orElseThrow());
    }

    @Transactional
    public RolloutResponse cancel(UUID rolloutId, CancelRolloutRequest req, AuthPrincipal principal) {
        tenantGuard.assertRole(principal, Role.OPERATOR, Role.ADMIN);

        RolloutRepository.RolloutRecord record = rolloutRepository.findById(rolloutId)
                .orElseThrow(() -> new NotFoundException("Rollout not found: " + rolloutId));

        if (!"ACTIVE".equals(record.status())) {
            throw new BadRequestException("Cannot cancel rollout with status " + record.status());
        }

        tenantGuard.assertTenant(principal, record.tenantId());

        ConfigSnapshot state = stateCache.head();
        ChangeSet stageCs = changeSetRepository.findByVersion(record.stageVersion(), state.definitions())
                .orElseThrow(() -> new NotFoundException("Stage change set not found for version: " + record.stageVersion()));

        if (stageCs.items().isEmpty() || !(stageCs.items().get(0) instanceof OverrideChange oc)) {
            throw new IllegalStateException("Corrupted stage change set: missing override item");
        }

        String settingKey = oc.settingKey();

        // Clear the office overrides
        List<ChangeItem> items = new ArrayList<>();
        for (String officeId : record.targetOffices()) {
            items.add(new OverrideChange(settingKey, ScopeRef.office(officeId), null, null));
        }

        String reason = (req != null && req.reason() != null && !req.reason().isBlank())
                ? req.reason()
                : "Cancel rollout " + rolloutId;

        PublishResponse pubResp = changeSetService.executePublishPipeline(
                ChangeKind.ROLLOUT_CANCEL,
                principal.subject(),
                reason,
                items,
                null,
                false,
                rolloutId,
                null
        );

        rolloutRepository.updateStatus(rolloutId, "CANCELLED", pubResp.version());

        return RolloutResponse.from(rolloutRepository.findById(rolloutId).orElseThrow());
    }

    public RolloutResponse get(UUID id, AuthPrincipal principal) {
        RolloutRepository.RolloutRecord record = rolloutRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Rollout not found: " + id));
        tenantGuard.assertTenant(principal, record.tenantId());
        return RolloutResponse.from(record);
    }

    public List<RolloutResponse> listActive(String tenantId, AuthPrincipal principal) {
        tenantGuard.assertTenant(principal, tenantId);
        return rolloutRepository.findByTenantAndStatus(tenantId, "ACTIVE").stream()
                .map(RolloutResponse::from)
                .toList();
    }
}
