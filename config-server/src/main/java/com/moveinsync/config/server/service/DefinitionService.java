package com.moveinsync.config.server.service;

import com.moveinsync.config.core.model.*;
import com.moveinsync.config.server.dto.PublishResponse;
import com.moveinsync.config.server.exception.NotFoundException;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.security.Role;
import com.moveinsync.config.server.security.TenantGuard;
import com.moveinsync.config.server.state.ServerStateCache;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class DefinitionService {
    private final ServerStateCache stateCache;
    private final ChangeSetService changeSetService;
    private final TenantGuard tenantGuard;

    public DefinitionService(
            ServerStateCache stateCache,
            ChangeSetService changeSetService,
            TenantGuard tenantGuard
    ) {
        this.stateCache = stateCache;
        this.changeSetService = changeSetService;
        this.tenantGuard = tenantGuard;
    }

    public Collection<SettingDefinition> findAll() {
        return stateCache.head().definitions().values();
    }

    public SettingDefinition findByKey(String key) {
        SettingDefinition def = stateCache.head().definitions().get(key);
        if (def == null) {
            throw new NotFoundException("Setting definition not found: " + key);
        }
        return def;
    }

    public PublishResponse upsertDefinition(SettingDefinition newDef, String reason, AuthPrincipal principal) {
        tenantGuard.assertRole(principal, Role.ADMIN);

        ConfigSnapshot state = stateCache.head();
        SettingDefinition existingDef = state.definitions().get(newDef.key());

        DefinitionChange change = new DefinitionChange(newDef.key(), existingDef, newDef);
        return changeSetService.executePublishPipeline(
                ChangeKind.DEFINITION,
                principal.subject(),
                reason != null ? reason : "Create/update definition: " + newDef.key(),
                List.of(change),
                null,
                false,
                null,
                null
        );
    }
}

