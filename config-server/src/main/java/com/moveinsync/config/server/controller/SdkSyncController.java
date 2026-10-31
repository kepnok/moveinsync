package com.moveinsync.config.server.controller;

import com.moveinsync.config.server.dto.SyncResponseDto;
import com.moveinsync.config.server.dto.VersionResponseDto;
import com.moveinsync.config.server.exception.ForbiddenException;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.security.Role;
import com.moveinsync.config.server.security.TenantGuard;
import com.moveinsync.config.server.service.DeltaService;
import com.moveinsync.config.server.service.SnapshotService;
import com.moveinsync.config.server.service.VersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/sdk/v1")
public class SdkSyncController {
    private final SnapshotService snapshotService;
    private final DeltaService deltaService;
    private final VersionService versionService;
    private final TenantGuard tenantGuard;

    public SdkSyncController(
            SnapshotService snapshotService,
            DeltaService deltaService,
            VersionService versionService,
            TenantGuard tenantGuard
    ) {
        this.snapshotService = snapshotService;
        this.deltaService = deltaService;
        this.versionService = versionService;
        this.tenantGuard = tenantGuard;
    }

    @GetMapping("/snapshot")
    public ResponseEntity<SyncResponseDto> getSnapshot(
            @RequestParam("tenantId") String tenantId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        tenantGuard.assertTenant(principal, tenantId);
        return ResponseEntity.ok(snapshotService.fullSnapshot(tenantId));
    }

    @GetMapping("/changes")
    public ResponseEntity<SyncResponseDto> getChanges(
            @RequestParam("tenantId") String tenantId,
            @RequestParam(name = "since", defaultValue = "0") long sinceVersion,
            @RequestParam(name = "epoch", required = false) UUID clientEpoch,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        tenantGuard.assertTenant(principal, tenantId);
        return ResponseEntity.ok(deltaService.changes(tenantId, sinceVersion, clientEpoch));
    }

    @GetMapping("/version")
    public ResponseEntity<VersionResponseDto> getVersion(
            @RequestParam("tenantId") String tenantId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        tenantGuard.assertTenant(principal, tenantId);
        return ResponseEntity.ok(versionService.headVersion(tenantId));
    }
}

