package com.moveinsync.config.sdk.store;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.moveinsync.config.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileSnapshotStoreTest {

    @Test
    void saveAndLoadValidSnapshot(@TempDir Path tempDir) {
        FileSnapshotStore store = new FileSnapshotStore(tempDir);
        UUID epoch = UUID.randomUUID();

        SettingDefinition def = new SettingDefinition(
                "cutoff",
                ValueType.NUMBER,
                new NumberValue(new BigDecimal("60")),
                new ValidationRules(JsonNodeFactory.instance.objectNode()),
                "Cutoff"
        );
        ConfigSnapshot snapshot = ConfigSnapshot.empty(epoch).withChangeSet(
                new ChangeSet(1L, UUID.randomUUID(), ChangeKind.DEFINITION, "sys", "init", Instant.now(),
                        List.of(new DefinitionChange("cutoff", null, def)), null, null)
        );

        store.save("acme", snapshot);

        Optional<ConfigSnapshot> loaded = store.loadLatestValid("acme");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().version()).isEqualTo(1L);
        assertThat(loaded.get().epoch()).isEqualTo(epoch);
        assertThat(loaded.get().definitions()).containsKey("cutoff");
    }

    @Test
    void loadReturnsEmptyWhenFileNotFound(@TempDir Path tempDir) {
        FileSnapshotStore store = new FileSnapshotStore(tempDir);
        assertThat(store.loadLatestValid("nonexistent")).isEmpty();
    }

    @Test
    void corruptedPayloadFailsChecksumVerification(@TempDir Path tempDir) throws IOException {
        FileSnapshotStore store = new FileSnapshotStore(tempDir);
        UUID epoch = UUID.randomUUID();
        ConfigSnapshot snapshot = ConfigSnapshot.empty(epoch);

        store.save("acme", snapshot);

        // Tamper with the saved file by corrupting the payload inside
        Path file = tempDir.resolve("acme.json");
        String content = Files.readString(file);
        // Change one character in the payload
        String corrupted = content.replace(epoch.toString(), UUID.randomUUID().toString());
        Files.writeString(file, corrupted);

        // Loading should now detect checksum mismatch and return Optional.empty()
        Optional<ConfigSnapshot> result = store.loadLatestValid("acme");
        assertThat(result).isEmpty();
    }
}
