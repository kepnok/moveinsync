package com.moveinsync.config.server.controller;

import com.moveinsync.config.server.exception.ForbiddenException;
import com.moveinsync.config.server.propagation.SseConnectionRegistry;
import com.moveinsync.config.server.propagation.SseSession;
import com.moveinsync.config.server.security.AuthPrincipal;
import com.moveinsync.config.server.security.TenantGuard;
import com.moveinsync.config.server.state.ServerStateCache;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/sdk/v1")
public class SdkStreamController {
    private final SseConnectionRegistry connectionRegistry;
    private final ServerStateCache stateCache;
    private final TenantGuard tenantGuard;

    public SdkStreamController(
            SseConnectionRegistry connectionRegistry,
            ServerStateCache stateCache,
            TenantGuard tenantGuard
    ) {
        this.connectionRegistry = connectionRegistry;
        this.stateCache = stateCache;
        this.tenantGuard = tenantGuard;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam("tenantId") String tenantId,
            @RequestHeader(name = "X-Service-ID", required = false) String serviceIdHeader,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        tenantGuard.assertTenant(principal, tenantId);

        String serviceId = (serviceIdHeader != null && !serviceIdHeader.isBlank())
                ? serviceIdHeader
                : principal.subject();

        SseSession session = connectionRegistry.register(tenantId, serviceId);

        // On connect: immediately send current head version so missed events heal instantly
        long currentHead = stateCache.head().version();
        session.send("version", "{\"version\":" + currentHead + "}");

        return session.emitter();
    }
}

