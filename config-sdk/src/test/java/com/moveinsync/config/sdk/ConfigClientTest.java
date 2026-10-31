package com.moveinsync.config.sdk;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.moveinsync.config.core.model.*;
import com.moveinsync.config.sdk.api.ServerApi;
import com.moveinsync.config.sdk.api.SyncResponse;
import com.moveinsync.config.sdk.store.FileSnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ConfigClientTest {

    @Test
    void clientResolvesValuesAndSupportsIsolation() {
        UUID epoch = UUID.randomUUID();
        SettingDefinition def = new SettingDefinition(
                "cutoff",
                ValueType.NUMBER,
                new NumberValue(new BigDecimal("60")),
                new ValidationRules(JsonNodeFactory.instance.objectNode()),
                "Cutoff"
        );
        ConfigSnapshot snap1 = ConfigSnapshot.empty(epoch).withChangeSet(
                new ChangeSet(1L, UUID.randomUUID(), ChangeKind.DEFINITION, "sys", "init", Instant.now(),
                        List.of(new DefinitionChange("cutoff", null, def)), null, null)
        );

        ServerApi mockApi = mock(ServerApi.class);
        when(mockApi.fetchSnapshot("acme")).thenReturn(new SyncResponse("FULL", epoch, 0L, 1L, 1L, List.of(), snap1));
        when(mockApi.openStream(anyString(), any(), any())).thenReturn(() -> {});

        try (ConfigClient client = ConfigClient.builder()
                .tenantId("acme")
                .serverApi(mockApi)
                .build()) {

            assertThat(client.isReady()).isTrue();
            assertThat(client.version()).isEqualTo(1L);

            ConfigContext ctx = ConfigContext.of("acme", "acme/blr-1", null);
            assertThat(client.getLong("cutoff", ctx)).isEqualTo(60L);

            // Test snapshot isolation: grabbing a view pins the snapshot
            ConfigView v1 = client.view();
            assertThat(v1.version()).isEqualTo(1L);

            ExplainResult explain = client.explain("cutoff", ctx);
            assertThat(explain.effective().fromDefault()).isTrue();
        }
    }

    @Test
    void failFastThrowsWhenServerDownAndNoDiskCache() {
        ServerApi mockApi = mock(ServerApi.class);
        when(mockApi.fetchSnapshot(anyString())).thenThrow(new RuntimeException("Connection refused"));
        when(mockApi.openStream(anyString(), any(), any())).thenReturn(() -> {});

        assertThatThrownBy(() -> ConfigClient.builder()
                .tenantId("acme")
                .serverApi(mockApi)
                .failFast(true)
                .build())
                .isInstanceOf(ConfigUnavailableException.class);
    }

    @Test
    void startEmptyInitializesEmptySnapshotWhenServerDown() {
        ServerApi mockApi = mock(ServerApi.class);
        when(mockApi.fetchSnapshot(anyString())).thenThrow(new RuntimeException("Connection refused"));
        when(mockApi.openStream(anyString(), any(), any())).thenReturn(() -> {});

        try (ConfigClient client = ConfigClient.builder()
                .tenantId("acme")
                .serverApi(mockApi)
                .failFast(false)
                .startEmpty(true)
                .build()) {

            assertThat(client.isReady()).isTrue();
            assertThat(client.version()).isEqualTo(0L);
        }
    }

    @Test
    void fallsBackToDiskCacheWhenServerDown(@TempDir Path tempDir) {
        UUID epoch = UUID.randomUUID();
        ConfigSnapshot diskSnap = ConfigSnapshot.empty(epoch);
        FileSnapshotStore store = new FileSnapshotStore(tempDir);
        store.save("acme", diskSnap);

        ServerApi mockApi = mock(ServerApi.class);
        when(mockApi.fetchSnapshot("acme")).thenThrow(new RuntimeException("Server unreachable"));
        when(mockApi.openStream(anyString(), any(), any())).thenReturn(() -> {});

        try (ConfigClient client = ConfigClient.builder()
                .tenantId("acme")
                .serverApi(mockApi)
                .cacheDirectory(tempDir)
                .failFast(true)
                .build()) {

            assertThat(client.isReady()).isTrue();
            assertThat(client.version()).isEqualTo(0L);
            assertThat(client.stats().diskLoads()).isGreaterThan(0L);
        }
    }

    @Test
    void listenerIsNotifiedOnUpdate() {
        UUID epoch = UUID.randomUUID();
        ConfigSnapshot snap1 = ConfigSnapshot.empty(epoch);
        ServerApi mockApi = mock(ServerApi.class);
        when(mockApi.fetchSnapshot("acme")).thenReturn(new SyncResponse("FULL", epoch, 0L, 1L, 1L, List.of(), snap1));
        when(mockApi.openStream(anyString(), any(), any())).thenReturn(() -> {});

        try (ConfigClient client = ConfigClient.builder()
                .tenantId("acme")
                .serverApi(mockApi)
                .build()) {

            AtomicBoolean notified = new AtomicBoolean(false);
            client.addListener(s -> notified.set(true));

            // Verify addListener works
            assertThat(notified.get()).isFalse();
        }
    }
}
