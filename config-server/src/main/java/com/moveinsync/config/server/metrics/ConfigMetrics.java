package com.moveinsync.config.server.metrics;

import com.moveinsync.config.server.propagation.SseConnectionRegistry;
import com.moveinsync.config.server.repository.OutboxRepository;
import com.moveinsync.config.server.state.ServerStateCache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ConfigMetrics {

    public ConfigMetrics(
            MeterRegistry registry,
            ServerStateCache stateCache,
            SseConnectionRegistry sseRegistry,
            OutboxRepository outboxRepository
    ) {
        Gauge.builder("config_server_version", stateCache, cache -> {
            var head = cache.head();
            return head != null ? head.version() : 0.0;
        })
        .description("Current head version of the configuration system")
        .register(registry);

        Gauge.builder("config_sse_active_connections", sseRegistry, SseConnectionRegistry::activeCount)
        .description("Number of active SSE subscriber connections")
        .register(registry);

        Gauge.builder("config_outbox_queue_depth", outboxRepository, repo -> (double) repo.pendingCount())
        .description("Number of pending outbox events awaiting broadcast")
        .register(registry);
    }
}

