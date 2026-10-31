package com.moveinsync.load;

import com.moveinsync.config.core.model.ConfigContext;
import com.moveinsync.config.sdk.ConfigClient;
import com.moveinsync.config.server.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FailureInjectionTest extends BaseLoadTest {

    @Test
    @DisplayName("Failure Injection: SDK serves from memory and disk cache when server is offline")
    void testServerOutageResilience(@TempDir Path tempCacheDir) throws Exception {
        String token = jwtService.mint("resilience-client", Set.of(Role.SERVICE), Set.of("acme"));

        // 1. Initial healthy sync: populates disk cache
        try (ConfigClient onlineClient = ConfigClient.builder()
                .serverUrl(baseUrl())
                .serviceId("resilience-client-1")
                .tenantId("acme")
                .tokenProvider(() -> token)
                .cacheDirectory(tempCacheDir)
                .failFast(true)
                .build()) {

            assertThat(onlineClient.isReady()).isTrue();
            ConfigContext ctx = ConfigContext.of("acme", "acme/blr-1", null);
            assertThat(onlineClient.getLong("booking.cutoff", ctx)).isGreaterThan(0L);
        }

        // 2. Simulate server down: connect a new client to an invalid unreachable port
        String deadServerUrl = "http://127.0.0.1:49999";

        try (ConfigClient offlineClient = ConfigClient.builder()
                .serverUrl(deadServerUrl)
                .serviceId("resilience-client-offline")
                .tenantId("acme")
                .tokenProvider(() -> token)
                .cacheDirectory(tempCacheDir)
                .failFast(true) // failFast is true, but disk cache is present, so it MUST start up successfully!
                .build()) {

            // Must be ready immediately from disk cache
            assertThat(offlineClient.isReady()).isTrue();
            assertThat(offlineClient.stats().diskLoads()).isGreaterThan(0L);

            // Reads continue from local in-memory snapshot with zero I/O and zero errors
            ConfigContext ctx = ConfigContext.of("acme", "acme/blr-1", null);
            for (int i = 0; i < 1000; i++) {
                long cutoff = offlineClient.getLong("booking.cutoff", ctx);
                assertThat(cutoff).isGreaterThan(0L);
            }
        }
    }
}
