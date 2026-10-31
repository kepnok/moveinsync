package com.moveinsync.config.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.moveinsync.config.core.model.ConfigContext;
import com.moveinsync.config.core.model.ScopeRef;
import com.moveinsync.config.sdk.ConfigClient;
import com.moveinsync.config.server.dto.PublishItemDto;
import com.moveinsync.config.server.dto.PublishRequest;
import com.moveinsync.config.server.dto.PublishResponse;
import com.moveinsync.config.server.dto.ScopeDto;
import com.moveinsync.config.server.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SdkLivePropagationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("DoD #4: Live propagation end-to-end: office blr-1 updates to 30 in <5s, blr-2 remains 60")
    void testLivePropagationEndToEnd() throws Exception {
        String token = jwtService.mint("demo-service-1", Set.of(Role.SERVICE), Set.of("acme"));

        // 1. Initialize real ConfigClient
        ConfigClient client = ConfigClient.builder()
                .serverUrl(baseUrl())
                .serviceId("demo-service-live-test")
                .tenantId("acme")
                .tokenProvider(() -> token)
                .pollInterval(Duration.ofSeconds(1))
                .failFast(true)
                .build();

        try {
            assertThat(client.isReady()).isTrue();

            String office1 = "acme/blr-" + UUID.randomUUID().toString().substring(0, 8);
            String office2 = "acme/blr-" + UUID.randomUUID().toString().substring(0, 8);

            ConfigContext ctxBlr1 = ConfigContext.of("acme", office1, null);
            ConfigContext ctxBlr2 = ConfigContext.of("acme", office2, null);

            // Initial check: default is 60 for both un-overridden offices
            assertThat(client.getLong("booking.cutoff", ctxBlr1)).isEqualTo(60L);
            assertThat(client.getLong("booking.cutoff", ctxBlr2)).isEqualTo(60L);

            CountDownLatch updateLatch = new CountDownLatch(1);
            client.addListener(snapshot -> {
                snapshot.overrideAt("booking.cutoff", ScopeRef.parse("OFFICE:" + office1)).ifPresent(v -> {
                    if (v.toJson().asLong() == 30L) {
                        updateLatch.countDown();
                    }
                });
            });

            // 2. Publish change set: override cutoff for office1 from 60 to 30
            PublishItemDto item = new PublishItemDto(
                    "booking.cutoff",
                    new ScopeDto("OFFICE", office1),
                    new IntNode(30)
            );
            PublishRequest draft = new PublishRequest(
                    "Update cutoff for " + office1 + " to 30",
                    List.of(item),
                    false
            );

            HttpHeaders pubHeaders = authHeaders("publisher-1", Set.of(Role.OPERATOR), Set.of("acme"));
            HttpEntity<PublishRequest> pubEntity = new HttpEntity<>(draft, pubHeaders);

            long publishStart = System.currentTimeMillis();
            ResponseEntity<PublishResponse> pubResp = restTemplate.postForEntity(
                    baseUrl() + "/api/v1/changesets",
                    pubEntity,
                    PublishResponse.class
            );

            assertThat(pubResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(pubResp.getBody()).isNotNull();

            // 3. Wait for propagation (< 5 seconds)
            boolean updatedInTime = updateLatch.await(5, TimeUnit.SECONDS);
            long propagationDurationMs = System.currentTimeMillis() - publishStart;

            assertThat(updatedInTime)
                    .withFailMessage("Propagation exceeded 5 seconds limit! Took " + propagationDurationMs + " ms")
                    .isTrue();

            // 4. Verify resolved values: office1 shows 30, office2 still shows 60
            assertThat(client.getLong("booking.cutoff", ctxBlr1)).isEqualTo(30L);
            assertThat(client.getLong("booking.cutoff", ctxBlr2)).isEqualTo(60L);

            // Verify explain output reflects the override source for office1
            var explain1 = client.explain("booking.cutoff", ctxBlr1);
            assertThat(explain1.effective().fromDefault()).isFalse();
            assertThat(explain1.effective().source().key()).isEqualTo("OFFICE:" + office1);

            var explain2 = client.explain("booking.cutoff", ctxBlr2);
            assertThat(explain2.effective().fromDefault()).isTrue();

        } finally {
            client.close();
        }
    }
}
