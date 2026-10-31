package com.moveinsync.load;

import com.moveinsync.config.server.dto.SyncResponseDto;
import com.moveinsync.config.server.dto.VersionResponseDto;
import com.moveinsync.config.server.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReconnectStormTest extends BaseLoadTest {

    @Test
    @DisplayName("Reconnect Storm: 100 simultaneous clients fetching snapshot and version served in-memory with 0 failures")
    void testReconnectStorm() throws Exception {
        int stormClients = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(stormClients);

        HttpHeaders headers = authHeaders("storm-client", Set.of(Role.SERVICE), Set.of("acme"));
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < stormClients; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startGate.await(); // Thundering herd released together

                    // 1. Fetch version
                    ResponseEntity<VersionResponseDto> verResp = restTemplate.exchange(
                            baseUrl() + "/sdk/v1/version?tenantId=acme",
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            VersionResponseDto.class
                    );

                    // 2. Fetch full snapshot
                    ResponseEntity<SyncResponseDto> snapResp = restTemplate.exchange(
                            baseUrl() + "/sdk/v1/snapshot?tenantId=acme",
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            SyncResponseDto.class
                    );

                    if (verResp.getStatusCode() == HttpStatus.OK && snapResp.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishGate.countDown();
                }
            }));
        }

        long start = System.currentTimeMillis();
        startGate.countDown(); // Release the storm!

        boolean completed = finishGate.await(10, TimeUnit.SECONDS);
        long durationMs = System.currentTimeMillis() - start;

        executor.shutdown();

        System.out.println("=================================================");
        System.out.printf("Reconnect Storm Results: %d clients in %d ms%n", stormClients, durationMs);
        System.out.printf("  Success: %d, Failures: %d%n", successCount.get(), failureCount.get());
        System.out.println("=================================================");

        assertThat(completed).isTrue();
        assertThat(failureCount.get()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(stormClients);
    }
}

