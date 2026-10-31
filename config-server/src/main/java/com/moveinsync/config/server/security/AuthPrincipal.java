package com.moveinsync.config.server.security;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public record AuthPrincipal(
        String subject,
        Set<Role> roles,
        Set<String> tenants
) {
    public AuthPrincipal {
        Objects.requireNonNull(subject, "subject cannot be null");
        roles = roles != null ? Set.copyOf(roles) : Collections.emptySet();
        tenants = tenants != null ? Set.copyOf(tenants) : Collections.emptySet();
    }

    public boolean canAccessTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || "*".equals(tenantId)) {
            return tenants.contains("*");
        }
        return tenants.contains("*") || tenants.contains(tenantId);
    }

    public boolean isGlobalAdmin() {
        return (roles.contains(Role.ADMIN) || roles.contains(Role.OPERATOR)) && tenants.contains("*");
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}

