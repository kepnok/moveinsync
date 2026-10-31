package com.moveinsync.config.server;

import com.fasterxml.jackson.databind.node.IntNode;
import com.moveinsync.config.server.dto.PublishItemDto;
import com.moveinsync.config.server.dto.PublishRequest;
import com.moveinsync.config.server.dto.PublishResponse;
import com.moveinsync.config.server.dto.ScopeDto;
import com.moveinsync.config.server.logging.LogSanitizer;
import com.moveinsync.config.server.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAndObservabilityTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Tenant Isolation: Cross-tenant snapshot and publish return 404 NOT_FOUND, never 403")
    void testTenantIsolationReturns404() {
        // Authenticated strictly for tenant "acme"
        HttpHeaders acmeHeaders = authHeaders("acme-user", Set.of(Role.OPERATOR, Role.SERVICE), Set.of("acme"));

        // 1. Cross-tenant GET /sdk/v1/snapshot for globex
        ResponseEntity<String> snapshotResp = restTemplate.exchange(
                baseUrl() + "/sdk/v1/snapshot?tenantId=globex",
                HttpMethod.GET,
                new HttpEntity<>(acmeHeaders),
                String.class
        );
        assertThat(snapshotResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 2. Cross-tenant GET /sdk/v1/version for globex
        ResponseEntity<String> versionResp = restTemplate.exchange(
                baseUrl() + "/sdk/v1/version?tenantId=globex",
                HttpMethod.GET,
                new HttpEntity<>(acmeHeaders),
                String.class
        );
        assertThat(versionResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 3. Cross-tenant Publish override on globex office
        PublishRequest crossPublish = new PublishRequest(
                "Cross tenant attack attempt",
                List.of(new PublishItemDto("booking.cutoff", new ScopeDto("OFFICE", "globex/del-1"), new IntNode(30))),
                false
        );
        ResponseEntity<String> pubResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/changesets",
                new HttpEntity<>(crossPublish, acmeHeaders),
                String.class
        );
        assertThat(pubResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Role Authorization: Service/Readonly role cannot publish change sets (403 FORBIDDEN)")
    void testRoleAuthorization() {
        // Authenticated as SERVICE (not OPERATOR or ADMIN)
        HttpHeaders serviceHeaders = authHeaders("service-agent", Set.of(Role.SERVICE), Set.of("acme"));

        PublishRequest pubReq = new PublishRequest(
                "Unauthorized publish",
                List.of(new PublishItemDto("booking.cutoff", new ScopeDto("OFFICE", "acme/blr-1"), new IntNode(30))),
                false
        );

        ResponseEntity<String> pubResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/changesets",
                new HttpEntity<>(pubReq, serviceHeaders),
                String.class
        );
        assertThat(pubResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Observability: Prometheus endpoint exposes config metrics and health details")
    void testMetricsAndObservability() {
        HttpHeaders headers = authHeaders("monitor-user", Set.of(Role.ADMIN), Set.of("*"));
        headers.set("X-Correlation-ID", "trace-id-abc-123");

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/actuator/prometheus",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = resp.getBody();
        assertThat(body).isNotNull();

        // Verify required metrics from Section 9
        assertThat(body).contains("config_server_version");
        assertThat(body).contains("config_sse_active_connections");
        assertThat(body).contains("config_outbox_queue_depth");
        assertThat(body).contains("config_publish_total");

        // Verify Correlation ID header echo
        assertThat(resp.getHeaders().getFirst("X-Correlation-ID")).isEqualTo("trace-id-abc-123");
    }

    @Test
    @DisplayName("Hygiene: LogSanitizer scrubs emails and phone numbers")
    void testLogSanitization() {
        String input = "User alice.smith@corp.com updated setting for +919876543210 and 555-123-4567";
        String sanitized = LogSanitizer.sanitize(input);

        assertThat(sanitized).doesNotContain("alice.smith@corp.com");
        assertThat(sanitized).doesNotContain("+919876543210");
        assertThat(sanitized).doesNotContain("555-123-4567");
        assertThat(sanitized).contains("***@***.***");
    }
}

