package com.moveinsync.config.server.controller;

import com.moveinsync.config.core.model.ChangeSet;
import com.moveinsync.config.server.dto.PublishRequest;
import com.moveinsync.config.server.dto.PublishResponse;
import com.moveinsync.config.server.exception.ForbiddenException;
import com.moveinsync.config.server.exception.NotFoundException;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.service.ChangeSetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/changesets")
public class ChangeSetController {
    private final ChangeSetService changeSetService;

    public ChangeSetController(ChangeSetService changeSetService) {
        this.changeSetService = changeSetService;
    }

    @PostMapping
    public ResponseEntity<PublishResponse> publish(
            @RequestBody PublishRequest request,
            @RequestParam(name = "dryRun", required = false, defaultValue = "false") boolean dryRun,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }

        PublishRequest reqWithDryRun = new PublishRequest(
                request.reason(),
                request.items(),
                dryRun || Boolean.TRUE.equals(request.dryRun())
        );

        PublishResponse response = changeSetService.publish(reqWithDryRun, principal, idempotencyKey);
        HttpStatus status = "DRY_RUN".equals(response.status()) ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{version}")
    public ResponseEntity<ChangeSet> getByVersion(
            @PathVariable("version") long version,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }

        ChangeSet cs = changeSetService.findByVersion(version)
                .orElseThrow(() -> new NotFoundException("ChangeSet version " + version + " not found"));

        return ResponseEntity.ok(cs);
    }
}

