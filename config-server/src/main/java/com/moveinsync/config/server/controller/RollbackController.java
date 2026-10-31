package com.moveinsync.config.server.controller;

import com.moveinsync.config.server.dto.DiffEntryDto;
import com.moveinsync.config.server.dto.PublishResponse;
import com.moveinsync.config.server.dto.RollbackRequest;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.service.RollbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rollback")
public class RollbackController {

    private final RollbackService rollbackService;

    public RollbackController(RollbackService rollbackService) {
        this.rollbackService = rollbackService;
    }

    @PostMapping("/preview")
    public ResponseEntity<List<DiffEntryDto>> preview(
            @RequestBody RollbackRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(rollbackService.preview(request.targetVersion(), request.tenantId(), principal));
    }

    @PostMapping
    public ResponseEntity<PublishResponse> rollback(
            @RequestBody RollbackRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(rollbackService.rollback(request, idempotencyKey, principal));
    }
}
