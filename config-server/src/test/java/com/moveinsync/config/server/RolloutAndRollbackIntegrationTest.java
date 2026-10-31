package com.moveinsync.config.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.moveinsync.config.server.dto.*;
import com.moveinsync.config.server.security.Role;
import com.moveinsync.config.server.service.ResolveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RolloutAndRollbackIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ResolveService resolveService;

    @Autowired
    private ObjectMapper mapper;

    private long resolveLong(String tenantId, String officeId, String key) {
        return resolveService.resolve(tenantId, officeId, null, List.of(key), false)
                .configs().get(key).value().toJson().asLong();
    }

    @Test
    @DisplayName("Staged rollout: stage across offices, promote to client, verify fallback cleanup")
    void testStagedRolloutLifecycle() {
        HttpHeaders headers = authHeaders("rollout-admin", Set.of(Role.ADMIN), Set.of("acme"));

        String office1 = "acme/blr-roll-" + UUID.randomUUID().toString().substring(0, 6);
        String office2 = "acme/blr-roll-" + UUID.randomUUID().toString().substring(0, 6);
        String officeOther = "acme/blr-other-" + UUID.randomUUID().toString().substring(0, 6);

        // 1. Stage rollout: set booking.cutoff to 25 on office1 and office2
        CreateRolloutRequest createReq = new CreateRolloutRequest(
                "booking.cutoff",
                new IntNode(25),
                List.of(office1, office2),
                "Stage test rollout"
        );

        ResponseEntity<RolloutResponse> createResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/rollouts",
                new HttpEntity<>(createReq, headers),
                RolloutResponse.class
        );

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RolloutResponse rollout = createResp.getBody();
        assertThat(rollout).isNotNull();
        assertThat(rollout.status()).isEqualTo("ACTIVE");
        assertThat(rollout.targetOffices()).containsExactlyInAnyOrder(office1, office2);

        // Verify state after stage: office1 and office2 have 25, officeOther still has default 60
        assertThat(resolveLong("acme", office1, "booking.cutoff")).isEqualTo(25L);
        assertThat(resolveLong("acme", office2, "booking.cutoff")).isEqualTo(25L);
        assertThat(resolveLong("acme", officeOther, "booking.cutoff")).isEqualTo(60L);

        // 2. Promote rollout: promote to CLIENT:acme
        ResponseEntity<RolloutResponse> promoteResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/rollouts/" + rollout.id() + "/promote",
                new HttpEntity<>(new PromoteRolloutRequest("Promote to client acme", new ScopeDto("CLIENT", "acme")), headers),
                RolloutResponse.class
        );

        assertThat(promoteResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        RolloutResponse promoted = promoteResp.getBody();
        assertThat(promoted).isNotNull();
        assertThat(promoted.status()).isEqualTo("PROMOTED");

        // Verify state after promote: client-level override is 25, office overrides are deleted,
        // so officeOther now also resolves to 25!
        assertThat(resolveLong("acme", officeOther, "booking.cutoff")).isEqualTo(25L);
        assertThat(resolveLong("acme", office1, "booking.cutoff")).isEqualTo(25L);
        assertThat(resolveLong("acme", office2, "booking.cutoff")).isEqualTo(25L);
    }

    @Test
    @DisplayName("Staged rollout: cancel removes office overrides and reverts to parent scope")
    void testCancelRollout() {
        HttpHeaders headers = authHeaders("rollout-admin", Set.of(Role.ADMIN), Set.of("acme"));

        String office1 = "acme/blr-canc-" + UUID.randomUUID().toString().substring(0, 6);

        // 1. Stage rollout
        CreateRolloutRequest createReq = new CreateRolloutRequest(
                "booking.cutoff",
                new IntNode(15),
                List.of(office1),
                "Staging for cancel test"
        );
        ResponseEntity<RolloutResponse> createResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/rollouts",
                new HttpEntity<>(createReq, headers),
                RolloutResponse.class
        );
        RolloutResponse rollout = createResp.getBody();
        assertThat(rollout).isNotNull();
        assertThat(resolveLong("acme", office1, "booking.cutoff")).isEqualTo(15L);

        // 2. Cancel rollout
        ResponseEntity<RolloutResponse> cancelResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/rollouts/" + rollout.id() + "/cancel",
                new HttpEntity<>(new CancelRolloutRequest("Aborting rollout"), headers),
                RolloutResponse.class
        );
        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResp.getBody().status()).isEqualTo("CANCELLED");

        // Verify office override was removed and falls back to client/global (not 15)
        assertThat(resolveLong("acme", office1, "booking.cutoff")).isNotEqualTo(15L);
    }

    @Test
    @DisplayName("Rollback: preview diff matches applied changes, rollback increments monotonic version")
    void testRollbackPreviewAndExecution() {
        HttpHeaders headers = authHeaders("rollback-admin", Set.of(Role.ADMIN), Set.of("acme"));

        String office = "acme/blr-rb-" + UUID.randomUUID().toString().substring(0, 6);

        // 1. Publish baseline override at 45
        PublishRequest pub1 = new PublishRequest("Set to 45", List.of(
                new PublishItemDto("booking.cutoff", new ScopeDto("OFFICE", office), new IntNode(45))
        ), false);
        ResponseEntity<PublishResponse> resp1 = restTemplate.postForEntity(
                baseUrl() + "/api/v1/changesets",
                new HttpEntity<>(pub1, headers),
                PublishResponse.class
        );
        long targetVersion = resp1.getBody().version();
        assertThat(resolveLong("acme", office, "booking.cutoff")).isEqualTo(45L);

        // 2. Publish second override modifying it to 10
        PublishRequest pub2 = new PublishRequest("Change to 10", List.of(
                new PublishItemDto("booking.cutoff", new ScopeDto("OFFICE", office), new IntNode(10))
        ), false);
        ResponseEntity<PublishResponse> resp2 = restTemplate.postForEntity(
                baseUrl() + "/api/v1/changesets",
                new HttpEntity<>(pub2, headers),
                PublishResponse.class
        );
        assertThat(resolveLong("acme", office, "booking.cutoff")).isEqualTo(10L);

        // 3. POST /api/v1/rollback/preview
        RollbackRequest rbReq = new RollbackRequest(targetVersion, "Preview reverting to 45", "acme");
        ResponseEntity<List<DiffEntryDto>> previewResp = restTemplate.exchange(
                baseUrl() + "/api/v1/rollback/preview",
                HttpMethod.POST,
                new HttpEntity<>(rbReq, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(previewResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<DiffEntryDto> diff = previewResp.getBody();
        assertThat(diff).isNotNull();
        assertThat(diff).anyMatch(d -> d.settingKey().equals("booking.cutoff")
                && d.scope().equals("OFFICE:" + office)
                && d.before().asLong() == 10L
                && d.after().asLong() == 45L);

        // 4. POST /api/v1/rollback
        ResponseEntity<PublishResponse> rbResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/rollback",
                new HttpEntity<>(rbReq, headers),
                PublishResponse.class
        );

        assertThat(rbResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        PublishResponse applied = rbResp.getBody();
        assertThat(applied).isNotNull();
        assertThat(applied.version()).isGreaterThan(resp2.getBody().version());

        // Value is restored to 45!
        assertThat(resolveLong("acme", office, "booking.cutoff")).isEqualTo(45L);
    }
}
