package com.moveinsync.config.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.moveinsync.config.server.dto.PublishItemDto;
import com.moveinsync.config.server.dto.PublishRequest;
import com.moveinsync.config.server.dto.PublishResponse;
import com.moveinsync.config.server.dto.ScopeDto;
import com.moveinsync.config.server.repository.ConfigHeadRepository;
import com.moveinsync.config.server.repository.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublishPipelineIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ConfigHeadRepository headRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Publish override via REST advances head version and returns 201 COMMITTED")
    void testPublishSuccess() {
        long versionBefore = headRepository.getHead().version();

        PublishRequest request = new PublishRequest(
                "Update BLR-1 cut-off to 45",
                List.of(new PublishItemDto("booking.cutoff", new ScopeDto("OFFICE", "acme/blr-1"), mapper.valueToTree(45))),
                false
        );

        HttpEntity<PublishRequest> entity = new HttpEntity<>(request, adminHeaders());
        ResponseEntity<PublishResponse> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/changesets",
                entity,
                PublishResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PublishResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("COMMITTED");
        assertThat(body.version()).isEqualTo(versionBefore + 1);
        assertThat(body.diff()).isNotEmpty();

        long versionAfter = headRepository.getHead().version();
        assertThat(versionAfter).isEqualTo(versionBefore + 1);
    }

    @Test
    @DisplayName("dryRun=true returns diff without advancing head version or writing to DB")
    void testDryRun() {
        long versionBefore = headRepository.getHead().version();

        PublishRequest request = new PublishRequest(
                "Dry run cut-off test",
                List.of(new PublishItemDto("booking.cutoff", new ScopeDto("OFFICE", "acme/blr-1"), mapper.valueToTree(15))),
                true
        );

        HttpEntity<PublishRequest> entity = new HttpEntity<>(request, adminHeaders());
        ResponseEntity<PublishResponse> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/changesets?dryRun=true",
                entity,
                PublishResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PublishResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("DRY_RUN");
        assertThat(body.version()).isEqualTo(versionBefore);
        assertThat(body.diff()).isNotEmpty();

        long versionAfter = headRepository.getHead().version();
        assertThat(versionAfter).isEqualTo(versionBefore);
    }

    @Test
    @DisplayName("Idempotent publish with same key returns stored result without advancing version")
    void testIdempotency() {
        String idempotencyKey = UUID.randomUUID().toString();

        PublishRequest request = new PublishRequest(
                "Idempotent cut-off update",
                List.of(new PublishItemDto("booking.cutoff", new ScopeDto("OFFICE", "acme/blr-1"), mapper.valueToTree(35))),
                false
        );

        HttpHeaders headers = adminHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        HttpEntity<PublishRequest> entity = new HttpEntity<>(request, headers);

        // First call
        ResponseEntity<PublishResponse> resp1 = restTemplate.postForEntity(
                baseUrl() + "/api/v1/changesets",
                entity,
                PublishResponse.class
        );
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long version1 = resp1.getBody().version();

        // Second call with same idempotency key
        ResponseEntity<PublishResponse> resp2 = restTemplate.postForEntity(
                baseUrl() + "/api/v1/changesets",
                entity,
                PublishResponse.class
        );
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long version2 = resp2.getBody().version();

        assertThat(version2).isEqualTo(version1);
    }

    @Test
    @DisplayName("Rejected bad value (-30 for cut-off) leaves head version untouched and nothing in outbox")
    void testRejectedChangeLeavesDbUntouched() {
        long versionBefore = headRepository.getHead().version();

        PublishRequest badRequest = new PublishRequest(
                "Invalid cut-off update",
                List.of(new PublishItemDto("booking.cutoff", new ScopeDto("OFFICE", "acme/blr-1"), mapper.valueToTree(-30))),
                false
        );

        HttpEntity<PublishRequest> entity = new HttpEntity<>(badRequest, adminHeaders());
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/changesets",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("NUMBER_BELOW_MIN");

        long versionAfter = headRepository.getHead().version();
        assertThat(versionAfter).isEqualTo(versionBefore);
    }

    @Test
    @DisplayName("Server atomicity: change set with one valid and one invalid item rolls back completely")
    void testAtomicRollbackOnPartialInvalid() {
        long versionBefore = headRepository.getHead().version();

        PublishRequest mixedRequest = new PublishRequest(
                "Mixed valid and invalid items",
                List.of(
                        new PublishItemDto("booking.cutoff", new ScopeDto("OFFICE", "acme/blr-1"), mapper.valueToTree(25)),
                        new PublishItemDto("booking.cutoff", new ScopeDto("OFFICE", "acme/blr-2"), mapper.valueToTree(-50))
                ),
                false
        );

        HttpEntity<PublishRequest> entity = new HttpEntity<>(mixedRequest, adminHeaders());
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/changesets",
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        long versionAfter = headRepository.getHead().version();
        assertThat(versionAfter).isEqualTo(versionBefore);
    }
}

