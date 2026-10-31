package com.moveinsync.config.server.service;

import com.moveinsync.config.core.model.*;
import com.moveinsync.config.server.exception.NotFoundException;
import com.moveinsync.config.server.repository.ChangeSetRepository;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.security.Role;
import com.moveinsync.config.server.security.TenantGuard;
import com.moveinsync.config.server.state.ServerStateCache;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class HistoryService {
    private final ChangeSetRepository changeSetRepository;
    private final ServerStateCache stateCache;
    private final TenantGuard tenantGuard;

    public HistoryService(ChangeSetRepository changeSetRepository, ServerStateCache stateCache, TenantGuard tenantGuard) {
        this.changeSetRepository = changeSetRepository;
        this.stateCache = stateCache;
        this.tenantGuard = tenantGuard;
    }

    public List<ChangeSet> history(String tenantId, Long fromVersion, int limit, AuthPrincipal principal) {
        tenantGuard.assertRole(principal, Role.OPERATOR, Role.ADMIN);
        if (tenantId != null && !tenantId.isBlank() && !"*".equals(tenantId)) {
            tenantGuard.assertTenant(principal, tenantId);
        } else {
            if (!principal.tenants().contains("*")) {
                throw new NotFoundException("Tenant not found");
            }
        }

        ConfigSnapshot state = stateCache.head();
        return changeSetRepository.history(tenantId, fromVersion, limit, state.definitions());
    }

    public ConfigSnapshot stateAt(long targetVersion) {
        ConfigSnapshot head = stateCache.head();
        if (targetVersion == head.version()) {
            return head;
        }
        if (targetVersion > head.version() || targetVersion < 0) {
            throw new NotFoundException("Version " + targetVersion + " is out of range (head=" + head.version() + ")");
        }

        // Fetch change sets between targetVersion + 1 and head.version()
        List<ChangeSet> changeSets = changeSetRepository.findRange(targetVersion + 1, head.version(), head.definitions());

        // Reverse to apply backwards from head
        Collections.reverse(changeSets);

        Map<String, SettingDefinition> defs = new HashMap<>(head.definitions());
        Map<String, Map<ScopeRef, ConfigValue>> overrides = new HashMap<>();
        for (Map.Entry<String, Map<ScopeRef, ConfigValue>> e : head.overrides().entrySet()) {
            overrides.put(e.getKey(), new HashMap<>(e.getValue()));
        }

        for (ChangeSet cs : changeSets) {
            List<ChangeItem> items = new ArrayList<>(cs.items());
            Collections.reverse(items);

            for (ChangeItem item : items) {
                if (item instanceof OverrideChange oc) {
                    Map<ScopeRef, ConfigValue> scopeMap = overrides.get(oc.settingKey());
                    if (scopeMap == null) {
                        scopeMap = new HashMap<>();
                        overrides.put(oc.settingKey(), scopeMap);
                    }
                    if (oc.oldValue() == null) {
                        scopeMap.remove(oc.scope());
                    } else {
                        scopeMap.put(oc.scope(), oc.oldValue());
                    }
                    if (scopeMap.isEmpty()) {
                        overrides.remove(oc.settingKey());
                    }
                } else if (item instanceof DefinitionChange dc) {
                    if (dc.oldDef() != null) {
                        defs.put(dc.settingKey(), dc.oldDef());
                    } else {
                        defs.remove(dc.settingKey());
                    }
                }
            }
        }

        return new ConfigSnapshot(head.epoch(), targetVersion, defs, overrides);
    }
}

