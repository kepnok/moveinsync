package com.moveinsync.config.server.controller;

import com.moveinsync.config.server.dto.*;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.service.RolloutService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rollouts")
public class RolloutController {

    private final RolloutService rolloutService;

    public RolloutController(RolloutService rolloutService) {
        this.rolloutService = rolloutService;
    }

    @PostMapping
    public ResponseEntity<RolloutResponse> create(
            @RequestBody CreateRolloutRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        RolloutResponse response = rolloutService.create(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/promote")
    public ResponseEntity<RolloutResponse> promote(
            @PathVariable UUID id,
            @RequestBody(required = false) PromoteRolloutRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(rolloutService.promote(id, request, principal));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<RolloutResponse> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelRolloutRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(rolloutService.cancel(id, request, principal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RolloutResponse> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(rolloutService.get(id, principal));
    }

    @GetMapping
    public ResponseEntity<List<RolloutResponse>> list(
            @RequestParam String tenantId,
            @RequestParam(required = false, defaultValue = "ACTIVE") String status,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(rolloutService.listActive(tenantId, principal));
    }
}

