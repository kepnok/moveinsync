package com.moveinsync.config.server.controller;

import com.moveinsync.config.server.exception.ForbiddenException;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.security.Role;
import com.moveinsync.config.server.security.TenantGuard;
import com.moveinsync.config.server.service.ResolveService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/config")
public class ResolveController {
    private final ResolveService resolveService;
    private final TenantGuard tenantGuard;

    public ResolveController(ResolveService resolveService, TenantGuard tenantGuard) {
        this.resolveService = resolveService;
        this.tenantGuard = tenantGuard;
    }

    @GetMapping("/resolve")
    public ResponseEntity<ResolveService.ResolveResponse> resolve(
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @RequestParam(name = "officeId", required = false) String officeId,
            @RequestParam(name = "shiftId", required = false) String shiftId,
            @RequestParam(name = "keys", required = false) List<String> keys,
            @RequestParam(name = "explain", required = false, defaultValue = "false") boolean explain,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        tenantGuard.assertRole(principal, Role.EMPLOYEE, Role.OPERATOR, Role.ADMIN);
        if (tenantId != null && !tenantId.isBlank()) {
            tenantGuard.assertTenant(principal, tenantId);
        }

        ResolveService.ResolveResponse response = resolveService.resolve(tenantId, officeId, shiftId, keys, explain);
        return ResponseEntity.ok(response);
    }
}

