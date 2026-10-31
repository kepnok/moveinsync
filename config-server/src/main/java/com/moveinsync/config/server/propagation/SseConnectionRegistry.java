package com.moveinsync.config.server.propagation;

import com.moveinsync.config.server.exception.RateLimitedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SseConnectionRegistry {
    private static final Logger log = LoggerFactory.getLogger(SseConnectionRegistry.class);

    private final int maxConnectionsPerService;
    private final Set<SseSession> sessions = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> connectionsPerService = new ConcurrentHashMap<>();

    private final Counter droppedSlowConsumers;

    public SseConnectionRegistry(
            @Value("${config.propagation.max-connections-per-service:50}") int maxConnectionsPerService,
            MeterRegistry meterRegistry
    ) {
        this.maxConnectionsPerService = maxConnectionsPerService;
        this.droppedSlowConsumers = Counter.builder("config_sse_dropped_slow_consumers_total")
                .description("Total dropped slow SSE consumers")
                .register(meterRegistry);

        meterRegistry.gauge("config_sse_active_connections", this, SseConnectionRegistry::activeCount);
    }

    public SseSession register(String tenantId, String serviceId) {
        AtomicInteger count = connectionsPerService.computeIfAbsent(serviceId, k -> new AtomicInteger(0));
        if (count.incrementAndGet() > maxConnectionsPerService) {
            count.decrementAndGet();
            throw new RateLimitedException("Connection limit exceeded for service: " + serviceId);
        }

        SseEmitter emitter = new SseEmitter(0L); // Infinite timeout for long-lived push
        SseSession session = new SseSession(tenantId, serviceId, emitter, droppedSlowConsumers::increment);

        Runnable cleanup = () -> {
            sessions.remove(session);
            count.decrementAndGet();
            session.close();
            log.info("SSE_DISCONNECTED serviceId={} tenantId={}", serviceId, tenantId);
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());

        sessions.add(session);
        log.info("SSE_CONNECTED serviceId={} tenantId={}", serviceId, tenantId);
        return session;
    }

    public void broadcast(VersionAdvanced event) {
        String data = "{\"version\":" + event.version() + "}";
        boolean allTenants = event.tenants().isEmpty();

        for (SseSession session : sessions) {
            if (session.isClosed()) continue;

            if (allTenants || event.tenants().contains(session.tenantId())) {
                session.send("version", data);
            }
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        Iterator<SseSession> it = sessions.iterator();
        while (it.hasNext()) {
            SseSession s = it.next();
            if (s.isClosed()) {
                it.remove();
                continue;
            }
            try {
                s.emitter().send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                s.close();
                it.remove();
            }
        }
    }

    public int activeCount() {
        return sessions.size();
    }
}

