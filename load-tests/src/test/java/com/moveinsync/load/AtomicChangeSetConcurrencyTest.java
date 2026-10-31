package com.moveinsync.load;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.moveinsync.config.core.model.ConfigContext;
import com.moveinsync.config.sdk.ConfigClient;
import com.moveinsync.config.sdk.ConfigView;
import com.moveinsync.config.server.dto.CreateDefinitionRequest;
import com.moveinsync.config.server.dto.PublishItemDto;
import com.moveinsync.config.server.dto.PublishRequest;
import com.moveinsync.config.server.dto.ScopeDto;
import com.moveinsync.config.server.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AtomicChangeSetConcurrencyTest extends BaseLoadTest {

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Atomic ChangeSet: 8 reader threads in tight loop verify 0 torn reads and start < end invariant")
    void testAtomicChangeSetConcurrency() throws Exception {
        HttpHeaders adminHeaders = authHeaders("concurrency-admin", Set.of(Role.ADMIN), Set.of("acme"));

        // Register two test settings: test.sync.start and test.sync.end
        try {
            restTemplate.postForEntity(
                    baseUrl() + "/api/v1/definitions",
                    new HttpEntity<>(new CreateDefinitionRequest("test.sync.start", "NUMBER", new IntNode(10), mapper.createObjectNode(), "Start", "Initial def"), adminHeaders),
                    Void.class
            );
        } catch (Exception ignored) {}

        try {
            restTemplate.postForEntity(
                    baseUrl() + "/api/v1/definitions",
                    new HttpEntity<>(new CreateDefinitionRequest("test.sync.end", "NUMBER", new IntNode(20), mapper.createObjectNode(), "End", "Initial def"), adminHeaders),
                    Void.class
            );
        } catch (Exception ignored) {}

        String token = jwtService.mint("reader-service", Set.of(Role.SERVICE), Set.of("acme"));
        ConfigClient client = ConfigClient.builder()
                .serverUrl(baseUrl())
                .serviceId("concurrency-reader-client")
                .tenantId("acme")
                .tokenProvider(() -> token)
                .pollInterval(Duration.ofMillis(200))
                .failFast(true)
                .build();

        try {
            assertThat(client.isReady()).isTrue();

            ConfigContext ctx = ConfigContext.of("acme", "acme/blr-concur", null);
            int readerThreads = 8;
            ExecutorService readerPool = Executors.newFixedThreadPool(readerThreads);

            AtomicBoolean running = new AtomicBoolean(true);
            AtomicLong totalReads = new AtomicLong(0);
            AtomicLong tornReads = new AtomicLong(0);
            AtomicLong invariantViolations = new AtomicLong(0);

            // Start 8 reader threads
            for (int i = 0; i < readerThreads; i++) {
                readerPool.submit(() -> {
                    while (running.get()) {
                        ConfigView view = client.view("acme");
                        long start = view.getLong("test.sync.start", ctx);
                        long end = view.getLong("test.sync.end", ctx);

                        totalReads.incrementAndGet();

                        if (start >= end) {
                            invariantViolations.incrementAndGet();
                        }

                        // Valid pairs are strictly (10, 20) or (30, 40)
                        boolean isPairA = (start == 10 && end == 20);
                        boolean isPairB = (start == 30 && end == 40);

                        if (!isPairA && !isPairB) {
                            tornReads.incrementAndGet();
                        }
                    }
                });
            }

            // Writer thread alternates publishing (10, 20) and (30, 40)
            int cycles = 6;
            for (int c = 0; c < cycles; c++) {
                long sVal = (c % 2 == 0) ? 30 : 10;
                long eVal = (c % 2 == 0) ? 40 : 20;

                PublishRequest pubReq = new PublishRequest(
                        "Atomic cycle " + c,
                        List.of(
                                new PublishItemDto("test.sync.start", new ScopeDto("OFFICE", "acme/blr-concur"), new IntNode((int) sVal)),
                                new PublishItemDto("test.sync.end", new ScopeDto("OFFICE", "acme/blr-concur"), new IntNode((int) eVal))
                        ),
                        false
                );

                restTemplate.postForEntity(
                        baseUrl() + "/api/v1/changesets",
                        new HttpEntity<>(pubReq, adminHeaders),
                        String.class
                );

                Thread.sleep(250);
            }

            // Stop readers
            running.set(false);
            readerPool.shutdown();
            readerPool.awaitTermination(2, TimeUnit.SECONDS);

            System.out.printf("Atomic Concurrency Results: Total Reads = %d, Torn Reads = %d, Invariant Violations = %d%n",
                    totalReads.get(), tornReads.get(), invariantViolations.get());

            assertThat(tornReads.get())
                    .withFailMessage("Detected torn reads across atomic change set items!")
                    .isEqualTo(0L);

            assertThat(invariantViolations.get())
                    .withFailMessage("Detected start >= end invariant violation!")
                    .isEqualTo(0L);

            assertThat(totalReads.get()).isGreaterThan(10_000L);

        } finally {
            client.close();
        }
    }
}
