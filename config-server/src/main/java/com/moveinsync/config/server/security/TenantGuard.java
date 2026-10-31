package com.moveinsync.config.server.security;

import com.moveinsync.config.core.model.ScopeRef;
import com.moveinsync.config.core.model.ScopeType;
import com.moveinsync.config.server.exception.ForbiddenException;
import com.moveinsync.config.server.exception.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class TenantGuard {

    public void assertTenant(AuthPrincipal principal, String tenantId) {
        if (principal == null) {
            throw new NotFoundException("Tenant not found");
        }
        if (!principal.canAccessTenant(tenantId)) {
            // Throw NotFoundException (not 403) so tenant existence is not leaked
            throw new NotFoundException("Tenant not found");
        }
    }

    public void assertScopes(AuthPrincipal principal, Collection<ScopeRef> scopes) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        for (ScopeRef scope : scopes) {
            if (scope.type() == ScopeType.GLOBAL) {
                if (!principal.tenants().contains("*")) {
                    throw new ForbiddenException("Modifying or accessing GLOBAL scope requires wildcard ('*') tenant authority");
                }
            } else {
                String tenant = scope.tenantId();
                if (!principal.canAccessTenant(tenant)) {
                    throw new NotFoundException("Scope not found: " + scope.key());
                }
            }
        }
    }

    public void assertRole(AuthPrincipal principal, Role... allowedRoles) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        for (Role r : allowedRoles) {
            if (principal.hasRole(r)) {
                return;
            }
        }
        throw new ForbiddenException("Insufficient permissions for this operation");
    }
}

