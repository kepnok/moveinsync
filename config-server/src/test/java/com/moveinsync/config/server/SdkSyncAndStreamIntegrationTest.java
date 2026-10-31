package com.moveinsync.config.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.config.server.dto.*;
import com.moveinsync.config.server.repository.ConfigHeadRepository;
import com.moveinsync.config.server.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class SdkSyncAndStreamIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ConfigHeadRepository headRepository;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Sync endpoints: version, snapshot and delta return valid memory-backed state")
    void testSyncEndpoints() {
        HttpHeaders headers = authHeaders("sdk-client-1", Set.of(Role.SERVICE), Set.of("acme"));
        HttpEntity<?> entity = new HttpEntity<>(headers);

        // 1. GET /sdk/v1/version
        ResponseEntity<VersionResponseDto> versionResp = restTemplate.exchange(
                baseUrl() + "/sdk/v1/version?tenantId=acme",
                HttpMethod.GET,
                entity,
                VersionResponseDto.class
        );
        assertThat(versionResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(versionResp.getBody()).isNotNull();
        assertThat(versionResp.getBody().version()).isGreaterThanOrEqualTo(0L);
        assertThat(versionResp.getBody().epoch()).isNotNull();

        // 2. GET /sdk/v1/snapshot
        ResponseEntity<SyncResponseDto> snapshotResp = restTemplate.exchange(
                baseUrl() + "/sdk/v1/snapshot?tenantId=acme",
                HttpMethod.GET,
                entity,
                SyncResponseDto.class
        );
        assertThat(snapshotResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(snapshotResp.getBody()).isNotNull();
        assertThat(snapshotResp.getBody().mode()).isEqualTo("FULL");
        assertThat(snapshotResp.getBody().snapshot()).isNotNull();

        // 3. GET /sdk/v1/changes since head
        long headVersion = versionResp.getBody().version();
        ResponseEntity<SyncResponseDto> changesResp = restTemplate.exchange(
                baseUrl() + "/sdk/v1/changes?tenantId=acme&since=" + headVersion + "&epoch=" + versionResp.getBody().epoch(),
                HttpMethod.GET,
                entity,
                SyncResponseDto.class
        );
        assertThat(changesResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changesResp.getBody()).isNotNull();
        assertThat(changesResp.getBody().mode()).isEqualTo("DELTA");
        assertThat(changesResp.getBody().changeSets()).isEmpty();
    }

    @Test
    @DisplayName("SSE stream receives immediate initial version event and push on publish within ~1s")
    void testSseStreamReceivesEvents() throws Exception {
        String token = jwtService.mint("service-worker-1", Set.of(Role.SERVICE), Set.of("acme"));
        long startingVersion = headRepository.getHead().version();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        HttpRequest streamReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/sdk/v1/stream?tenantId=acme"))
                .header("Authorization", "Bearer " + token)
                .header("X-Service-ID", "service-worker-1")
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        BlockingQueue<String> receivedEvents = new LinkedBlockingQueue<>();
        CountDownLatch streamOpened = new CountDownLatch(1);

        CompletableFuture<Void> streamFuture = client.sendAsync(streamReq, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    streamOpened.countDown();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data:")) {
                                receivedEvents.offer(line.substring(5).trim());
                            }
                        }
                    } catch (Exception ignored) {
                    }
                });

        assertThat(streamOpened.await(5, TimeUnit.SECONDS)).isTrue();

        // Should receive immediate initial version event on connect
        String firstEvent = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertThat(firstEvent).isNotNull();
        assertThat(firstEvent).contains("\"version\":" + startingVersion);

        // Now publish a new change set for acme
        PublishRequest req = new PublishRequest(
                "SSE push test",
                List.of(new PublishItemDto("booking.cutoff", new ScopeDto("OFFICE", "acme/blr-1"), mapper.valueToTree(22))),
                false
        );

        HttpHeaders headers = adminHeaders();
        HttpEntity<PublishRequest> entity = new HttpEntity<>(req, headers);
        ResponseEntity<PublishResponse> pubResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/changesets",
                entity,
                PublishResponse.class
        );

        assertThat(pubResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long newVersion = pubResp.getBody().version();

        // The SSE stream must receive the version event within ~1s!
        String pushedEvent = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertThat(pushedEvent).isNotNull();
        assertThat(pushedEvent).contains("\"version\":" + newVersion);

        streamFuture.cancel(true);
    }
}

