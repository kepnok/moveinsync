package com.moveinsync.config.server.controller;

import com.moveinsync.config.core.model.ChangeSet;
import com.moveinsync.config.server.exception.ForbiddenException;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.service.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {
    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ResponseEntity<List<ChangeSet>> getHistory(
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @RequestParam(name = "from", required = false) Long fromVersion,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }

        List<ChangeSet> history = historyService.history(tenantId, fromVersion, limit, principal);
        return ResponseEntity.ok(history);
    }
}

