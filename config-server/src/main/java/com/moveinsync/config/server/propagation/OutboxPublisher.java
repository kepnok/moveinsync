package com.moveinsync.config.server.propagation;

import com.moveinsync.config.server.repository.OutboxRepository;
import com.moveinsync.config.server.state.ServerStateCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final ServerStateCache stateCache;
    private final ConfigEventBus eventBus;
    private final Counter publishFailures;
    private final ExecutorService wakeExecutor = Executors.newSingleThreadExecutor();

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            ServerStateCache stateCache,
            ConfigEventBus eventBus,
            MeterRegistry meterRegistry
    ) {
        this.outboxRepository = outboxRepository;
        this.stateCache = stateCache;
        this.eventBus = eventBus;
        this.publishFailures = Counter.builder("config_outbox_publish_failures_total")
                .description("Total outbox publish failures")
                .register(meterRegistry);

        meterRegistry.gauge("config_outbox_pending", outboxRepository, OutboxRepository::pendingCount);
    }

    public void wake() {
        wakeExecutor.submit(this::drain);
    }

    @Scheduled(fixedDelayString = "${config.propagation.outbox-delay-ms:200}")
    public synchronized void drain() {
        try {
            long headVersion = stateCache.head() != null ? stateCache.head().version() : 0L;
            List<OutboxRepository.OutboxRow> batch = outboxRepository.lockPending(100);

            for (OutboxRepository.OutboxRow row : batch) {
                // Skip rows ahead of the cache until next tick
                if (row.version() > headVersion) {
                    continue;
                }

                try {
                    eventBus.publish(new VersionAdvanced(row.version(), row.tenants()));
                    outboxRepository.markPublished(row.version());
                } catch (Exception e) {
                    publishFailures.increment();
                    log.error("OUTBOX_PUBLISH_FAILED version={}: {}", row.version(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Error draining outbox: {}", e.getMessage());
        }
    }
}

