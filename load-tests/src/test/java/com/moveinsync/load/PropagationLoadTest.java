package com.moveinsync.load;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.moveinsync.config.core.model.ConfigContext;
import com.moveinsync.config.core.model.ScopeRef;
import com.moveinsync.config.sdk.ConfigClient;
import com.moveinsync.config.server.dto.PublishItemDto;
import com.moveinsync.config.server.dto.PublishRequest;
import com.moveinsync.config.server.dto.ScopeDto;
import com.moveinsync.config.server.security.Role;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PropagationLoadTest extends BaseLoadTest {

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Propagation Load: 50 concurrent SDK clients receive live updates in < 5 seconds")
    void testPropagationAcross50Clients() throws Exception {
        int clientCount = 50;
        List<ConfigClient> clients = new ArrayList<>(clientCount);
        CountDownLatch propagationLatch = new CountDownLatch(clientCount);
        Histogram histogram = new Histogram(3600000000L, 3); // up to 1 hr in millis, 3 sig figs

        String office = "acme/blr-prop-" + UUID.randomUUID().toString().substring(0, 6);
        ConfigContext ctx = ConfigContext.of("acme", office, null);
        AtomicLong publishStart = new AtomicLong(0);

        String token = jwtService.mint("load-client", Set.of(Role.SERVICE), Set.of("acme"));

        try {
            // 1. Initialize 50 in-process SDK clients
            for (int i = 0; i < clientCount; i++) {
                ConfigClient client = ConfigClient.builder()
                        .serverUrl(baseUrl())
                        .serviceId("load-subscriber-" + i)
                        .tenantId("acme")
                        .tokenProvider(() -> token)
                        .pollInterval(Duration.ofSeconds(1))
                        .failFast(true)
                        .build();

                client.addListener(snapshot -> {
                    long start = publishStart.get();
                    if (start > 0) {
                        snapshot.overrideAt("booking.cutoff", ScopeRef.parse("OFFICE:" + office)).ifPresent(val -> {
                            if (val.toJson().asLong() == 18L) {
                                long latencyMs = Math.max(1, System.currentTimeMillis() - start);
                                synchronized (histogram) {
                                    histogram.recordValue(latencyMs);
                                }
                                propagationLatch.countDown();
                            }
                        });
                    }
                });

                clients.add(client);
            }

            // Ensure all 50 clients are ready
            for (ConfigClient c : clients) {
                assertThat(c.isReady()).isTrue();
            }

            // 2. Publish update: set cutoff for office to 18
            PublishRequest pubReq = new PublishRequest(
                    "Load test 50 clients propagation",
                    List.of(new PublishItemDto("booking.cutoff", new ScopeDto("OFFICE", office), new IntNode(18))),
                    false
            );

            HttpHeaders adminHeaders = authHeaders("pub-admin", Set.of(Role.ADMIN), Set.of("acme"));
            publishStart.set(System.currentTimeMillis());

            ResponseEntity<String> pubResp = restTemplate.postForEntity(
                    baseUrl() + "/api/v1/changesets",
                    new HttpEntity<>(pubReq, adminHeaders),
                    String.class
            );
            assertThat(pubResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // 3. Wait for all 50 clients to receive the push notification (< 5s limit)
            boolean allReceived = propagationLatch.await(5, TimeUnit.SECONDS);

            System.out.println("=================================================");
            System.out.printf("Propagation Latency across %d clients:%n", clientCount);
            System.out.printf("  p50: %d ms%n", histogram.getValueAtPercentile(50));
            System.out.printf("  p95: %d ms%n", histogram.getValueAtPercentile(95));
            System.out.printf("  p99: %d ms%n", histogram.getValueAtPercentile(99));
            System.out.printf("  max: %d ms%n", histogram.getMaxValue());
            System.out.println("=================================================");

            assertThat(allReceived)
                    .withFailMessage("Not all clients received update within 5 seconds! Remaining = " + propagationLatch.getCount())
                    .isTrue();

            assertThat(histogram.getMaxValue())
                    .withFailMessage("Max propagation latency exceeded 5,000 ms!")
                    .isLessThan(5000L);

            // Verify local reads on each client now reflect 18
            for (ConfigClient c : clients) {
                assertThat(c.getLong("booking.cutoff", ctx)).isEqualTo(18L);
            }

        } finally {
            for (ConfigClient c : clients) {
                try { c.close(); } catch (Exception ignored) {}
            }
        }
    }
}
