package com.moveinsync.config.core.model;

import java.time.Instant;
import java.util.*;

public record ChangeSet(
        long version,
        UUID id,
        ChangeKind kind,
        String authorId,
        String reason,
        Instant createdAt,
        List<ChangeItem> items,
        UUID rolloutId,
        Long rollbackTo
) {
    public ChangeSet {
        Objects.requireNonNull(id, "ChangeSet id cannot be null");
        Objects.requireNonNull(kind, "ChangeKind cannot be null");
        Objects.requireNonNull(authorId, "authorId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        items = items != null ? List.copyOf(items) : Collections.emptyList();
    }

    public Set<String> affectedTenants() {
        Set<String> tenants = new HashSet<>();
        for (ChangeItem item : items) {
            if (item instanceof DefinitionChange) {
                return Collections.emptySet(); // Empty means ALL tenants
            } else if (item instanceof OverrideChange oc) {
                if (oc.scope().type() == ScopeType.GLOBAL) {
                    return Collections.emptySet(); // Global affects ALL tenants
                }
                String t = oc.scope().tenantId();
                if (t != null) {
                    tenants.add(t);
                }
            }
        }
        return Collections.unmodifiableSet(tenants);
    }

    public ChangeSet filteredForTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return this;
        }
        List<ChangeItem> filtered = new ArrayList<>();
        for (ChangeItem item : items) {
            if (item instanceof DefinitionChange) {
                filtered.add(item);
            } else if (item instanceof OverrideChange oc) {
                if (oc.scope().type() == ScopeType.GLOBAL || tenantId.equals(oc.scope().tenantId())) {
                    filtered.add(item);
                }
            }
        }
        return new ChangeSet(version, id, kind, authorId, reason, createdAt, filtered, rolloutId, rollbackTo);
    }
}

