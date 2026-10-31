package com.moveinsync.config.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.config.server.dto.PublishItemDto;
import com.moveinsync.config.server.dto.PublishRequest;
import com.moveinsync.config.server.dto.PublishResponse;
import com.moveinsync.config.server.dto.ScopeDto;
import com.moveinsync.config.server.repository.ConfigHeadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentPublishIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ConfigHeadRepository headRepository;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Concurrent publishers yield strictly monotonic, gap-free versions with no duplicates")
    void testConcurrentPublishYieldsMonotonicGapFreeVersions() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        long startingVersion = headRepository.getHead().version();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                startLatch.await(); // ensure maximum contention

                PublishRequest req = new PublishRequest(
                        "Concurrent update " + index,
                        List.of(new PublishItemDto(
                                "booking.cutoff",
                                new ScopeDto("OFFICE", "acme/blr-1"),
                                mapper.valueToTree(10 + (index % 100))
                        )),
                        false
                );

                HttpEntity<PublishRequest> entity = new HttpEntity<>(req, adminHeaders());
                ResponseEntity<PublishResponse> resp = restTemplate.postForEntity(
                        baseUrl() + "/api/v1/changesets",
                        entity,
                        PublishResponse.class
                );

                if (resp.getStatusCode() == HttpStatus.CREATED && resp.getBody() != null) {
                    return resp.getBody().version();
                }
                return -1L;
            }));
        }

        startLatch.countDown(); // FIRE!
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        List<Long> versions = new ArrayList<>();
        for (Future<Long> f : futures) {
            long v = f.get();
            if (v > 0) {
                versions.add(v);
            }
        }

        // All returned versions must be strictly unique (no duplicates!)
        Set<Long> uniqueVersions = new HashSet<>(versions);
        assertThat(uniqueVersions).hasSize(versions.size());

        // Sort versions
        Collections.sort(versions);

        // Gap-free: every version must be exactly previous + 1
        for (int i = 0; i < versions.size(); i++) {
            long expected = startingVersion + 1 + i;
            assertThat(versions.get(i)).isEqualTo(expected);
        }

        // Final head version in DB must equal the max version
        long finalHead = headRepository.getHead().version();
        assertThat(finalHead).isEqualTo(startingVersion + versions.size());
    }
}

