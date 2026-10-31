package com.moveinsync.config.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.moveinsync.config.core.codec.DiffEntry;
import com.moveinsync.config.core.codec.SnapshotDiff;
import com.moveinsync.config.core.exception.UnknownSettingException;
import com.moveinsync.config.core.exception.ValidationFailedException;
import com.moveinsync.config.core.model.*;
import com.moveinsync.config.core.strategy.ValueTypeRegistry;
import com.moveinsync.config.core.validation.ChangeSetValidator;
import com.moveinsync.config.core.validation.Violation;
import com.moveinsync.config.server.dto.DiffEntryDto;
import com.moveinsync.config.server.dto.PublishItemDto;
import com.moveinsync.config.server.dto.PublishRequest;
import com.moveinsync.config.server.dto.PublishResponse;
import com.moveinsync.config.server.propagation.OutboxPublisher;
import com.moveinsync.config.server.repository.ChangeSetRepository;
import com.moveinsync.config.server.repository.ConfigHeadRepository;
import com.moveinsync.config.server.repository.DefinitionRepository;
import com.moveinsync.config.server.repository.OutboxRepository;
import com.moveinsync.config.server.repository.OverrideRepository;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.security.Role;
import com.moveinsync.config.server.security.TenantGuard;
import com.moveinsync.config.server.state.ChangeLog;
import com.moveinsync.config.server.state.ServerStateCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ChangeSetService {
    private static final Logger log = LoggerFactory.getLogger(ChangeSetService.class);

    private final ConfigHeadRepository headRepository;
    private final DefinitionRepository definitionRepository;
    private final OverrideRepository overrideRepository;
    private final ChangeSetRepository changeSetRepository;
    private final OutboxRepository outboxRepository;
    private final ServerStateCache stateCache;
    private final ChangeLog changeLog;
    private final OutboxPublisher outboxPublisher;
    private final ChangeSetValidator validator;
    private final ValueTypeRegistry typeRegistry;
    private final TenantGuard tenantGuard;

    private final Timer publishTimer;
    private final Counter publishSuccess;
    private final Counter publishRejected;

    public ChangeSetService(
            ConfigHeadRepository headRepository,
            DefinitionRepository definitionRepository,
            OverrideRepository overrideRepository,
            ChangeSetRepository changeSetRepository,
            OutboxRepository outboxRepository,
            ServerStateCache stateCache,
            ChangeLog changeLog,
            OutboxPublisher outboxPublisher,
            ChangeSetValidator validator,
            ValueTypeRegistry typeRegistry,
            TenantGuard tenantGuard,
            MeterRegistry meterRegistry
    ) {
        this.headRepository = headRepository;
        this.definitionRepository = definitionRepository;
        this.overrideRepository = overrideRepository;
        this.changeSetRepository = changeSetRepository;
        this.outboxRepository = outboxRepository;
        this.stateCache = stateCache;
        this.changeLog = changeLog;
        this.outboxPublisher = outboxPublisher;
        this.validator = validator;
        this.typeRegistry = typeRegistry;
        this.tenantGuard = tenantGuard;

        this.publishTimer = Timer.builder("config_publish_seconds")
                .description("Latency of publish pipeline")
                .register(meterRegistry);
        this.publishSuccess = Counter.builder("config_publish_total")
                .tag("result", "success")
                .register(meterRegistry);
        this.publishRejected = Counter.builder("config_publish_total")
                .tag("result", "rejected")
                .register(meterRegistry);
    }

    @Transactional
    public PublishResponse publish(PublishRequest request, AuthPrincipal principal, String idempotencyKey) {
        tenantGuard.assertRole(principal, Role.OPERATOR, Role.ADMIN);

        // Check idempotency before opening transaction
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            ConfigSnapshot state = stateCache.head();
            Optional<ChangeSet> existing = changeSetRepository.findByIdempotencyKey(idempotencyKey, state.definitions());
            if (existing.isPresent()) {
                ChangeSet cs = existing.get();
                return PublishResponse.committed(cs.version(), cs.id(), List.of());
            }
        }

        // Validate scopes
        List<ScopeRef> scopes = new ArrayList<>();
        if (request.items() != null) {
            for (PublishItemDto item : request.items()) {
                scopes.add(item.scope().toScopeRef());
            }
        }
        tenantGuard.assertScopes(principal, scopes);

        // Parse items fast (step 5)
        ConfigSnapshot state = stateCache.head();
        List<ChangeItem> parsedDraftItems = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();

        if (request.items() != null) {
            for (PublishItemDto itemDto : request.items()) {
                SettingDefinition def = state.definitions().get(itemDto.key());
                if (def == null) {
                    violations.add(new Violation("UNKNOWN_SETTING", itemDto.key(), "Setting '" + itemDto.key() + "' does not exist"));
                    continue;
                }
                ScopeRef scope = itemDto.scope().toScopeRef();
                ConfigValue newValue = null;
                if (itemDto.value() != null && !itemDto.value().isNull()) {
                    try {
                        newValue = typeRegistry.parse(def, itemDto.value());
                    } catch (ValidationFailedException vfe) {
                        violations.addAll(vfe.violations());
                    }
                }
                parsedDraftItems.add(new OverrideChange(itemDto.key(), scope, null, newValue));
            }
        }

        if (!violations.isEmpty()) {
            publishRejected.increment();
            throw new ValidationFailedException(violations);
        }

        return executePublishPipeline(
                ChangeKind.NORMAL,
                principal.subject(),
                request.reason(),
                parsedDraftItems,
                idempotencyKey,
                Boolean.TRUE.equals(request.dryRun()),
                null,
                null
        );
    }

    @Transactional
    public PublishResponse executePublishPipeline(
            ChangeKind kind,
            String authorId,
            String reason,
            List<ChangeItem> items,
            String idempotencyKey,
            boolean dryRun,
            UUID rolloutId,
            Long rollbackTo
    ) {
        long startTime = System.nanoTime();

        // 7. Lock head row - serializes all publishes
        ConfigHeadRepository.Head head = headRepository.lockForUpdate();

        // 8. Ensure cache matches DB head
        ConfigSnapshot state = stateCache.head();
        if (state == null || state.version() != head.version()) {
            stateCache.reloadFromDb();
            state = stateCache.head();
        }

        // 9. Fill oldValue and drop no-op items
        List<ChangeItem> finalizedItems = new ArrayList<>();
        for (ChangeItem item : items) {
            if (item instanceof OverrideChange oc) {
                ConfigValue existingVal = state.overrideAt(oc.settingKey(), oc.scope()).orElse(null);
                if (Objects.equals(existingVal, oc.newValue())) {
                    // No-op item, drop
                    continue;
                }
                finalizedItems.add(new OverrideChange(oc.settingKey(), oc.scope(), existingVal, oc.newValue()));
            } else if (item instanceof DefinitionChange dc) {
                SettingDefinition existingDef = state.definitions().get(dc.settingKey());
                if (Objects.equals(existingDef, dc.newDef())) {
                    continue;
                }
                finalizedItems.add(new DefinitionChange(dc.settingKey(), existingDef, dc.newDef()));
            }
        }

        if (finalizedItems.isEmpty()) {
            return PublishResponse.noChange(head.version());
        }

        // 10. Construct draft and validate
        UUID csId = UUID.randomUUID();
        long nextVersion = head.version() + 1;
        ChangeSet draft = new ChangeSet(nextVersion, csId, kind, authorId, reason, Instant.now(), finalizedItems, rolloutId, rollbackTo);

        try {
            validator.validate(state, draft);
        } catch (ValidationFailedException vfe) {
            publishRejected.increment();
            throw vfe;
        }

        ConfigSnapshot candidate = state.withChangeSet(draft);
        List<DiffEntry> diffEntries = SnapshotDiff.diff(state, candidate);
        List<DiffEntryDto> diffDtos = diffEntries.stream().map(DiffEntryDto::from).toList();

        // 11. Handle dry-run
        if (dryRun) {
            return PublishResponse.dryRun(head.version(), diffDtos);
        }

        // 13. Persist change set, items, overrides and outbox
        changeSetRepository.insert(draft, idempotencyKey);

        for (ChangeItem item : finalizedItems) {
            if (item instanceof OverrideChange oc) {
                if (oc.newValue() == null) {
                    overrideRepository.delete(oc.settingKey(), oc.scope());
                } else {
                    overrideRepository.upsert(oc.settingKey(), oc.scope(), oc.newValue(), nextVersion);
                }
            } else if (item instanceof DefinitionChange dc) {
                definitionRepository.upsert(dc.newDef(), nextVersion);
            }
        }

        // 14. Advance head version
        headRepository.advance(nextVersion);

        // 15. Insert outbox row
        outboxRepository.insert(nextVersion, draft.affectedTenants());

        // 17. Register afterCommit hook or execute immediately if no synchronization
        Runnable afterCommitAction = () -> {
            stateCache.advance(candidate);
            changeLog.append(draft);
            outboxPublisher.wake();
            publishSuccess.increment();
            publishTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
            log.info("CONFIG_PUBLISHED version={} changeSetId={} kind={} author={}",
                    nextVersion, csId, kind, authorId);
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    afterCommitAction.run();
                }
            });
        } else {
            afterCommitAction.run();
        }

        return PublishResponse.committed(nextVersion, csId, diffDtos);
    }

    public Optional<ChangeSet> findByVersion(long version) {
        ConfigSnapshot state = stateCache.head();
        return changeSetRepository.findByVersion(version, state.definitions());
    }
}

