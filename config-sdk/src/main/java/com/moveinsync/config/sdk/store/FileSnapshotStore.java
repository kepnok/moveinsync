package com.moveinsync.config.sdk.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moveinsync.config.core.codec.SnapshotCodec;
import com.moveinsync.config.core.model.ConfigSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

public class FileSnapshotStore implements SnapshotStore {
    private static final int FORMAT_VERSION = 1;

    private final Path directory;
    private final SnapshotCodec codec;
    private final ObjectMapper mapper;

    public FileSnapshotStore(Path directory) {
        this(directory, new SnapshotCodec());
    }

    public FileSnapshotStore(Path directory, SnapshotCodec codec) {
        this.directory = Objects.requireNonNull(directory, "directory cannot be null");
        this.codec = Objects.requireNonNull(codec, "codec cannot be null");
        this.mapper = new ObjectMapper();
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException("Could not create snapshot directory: " + directory, e);
        }
    }

    private Path targetPath(String tenantId) {
        String safeName = (tenantId != null && !tenantId.isBlank()) ? tenantId : "GLOBAL";
        return directory.resolve(safeName + ".json");
    }

    private Path tmpPath(String tenantId) {
        String safeName = (tenantId != null && !tenantId.isBlank()) ? tenantId : "GLOBAL";
        return directory.resolve(safeName + ".json.tmp");
    }

    @Override
    public synchronized void save(String tenantId, ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        Path tmp = tmpPath(tenantId);
        Path target = targetPath(tenantId);

        try {
            byte[] payloadBytes = codec.encode(snapshot);
            String sha256 = computeSha256(payloadBytes);

            ObjectNode envelope = mapper.createObjectNode();
            envelope.put("formatVersion", FORMAT_VERSION);
            envelope.put("sha256", sha256);
            envelope.set("payload", mapper.readTree(payloadBytes));

            byte[] envelopeBytes = mapper.writeValueAsBytes(envelope);

            // Write to tmp file, fsync, and atomic rename
            try (FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
                fos.write(envelopeBytes);
                fos.flush();
                fos.getFD().sync(); // fsync
            }

            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {}
            throw new RuntimeException("Failed to persist snapshot to disk for tenant: " + tenantId, e);
        }
    }

    @Override
    public synchronized Optional<ConfigSnapshot> loadLatestValid(String tenantId) {
        Path target = targetPath(tenantId);
        if (!Files.exists(target)) {
            return Optional.empty();
        }

        try {
            byte[] fileBytes = Files.readAllBytes(target);
            JsonNode envelope = mapper.readTree(fileBytes);

            if (!envelope.hasNonNull("formatVersion") || envelope.get("formatVersion").asInt() != FORMAT_VERSION) {
                return Optional.empty();
            }

            String expectedSha = envelope.hasNonNull("sha256") ? envelope.get("sha256").asText() : "";
            JsonNode payloadNode = envelope.get("payload");
            if (payloadNode == null || payloadNode.isNull()) {
                return Optional.empty();
            }

            byte[] payloadBytes = mapper.writeValueAsBytes(payloadNode);
            String actualSha = computeSha256(payloadBytes);

            if (!expectedSha.equalsIgnoreCase(actualSha)) {
                // Checksum mismatch -> corrupted file!
                return Optional.empty();
            }

            ConfigSnapshot snapshot = codec.decode(payloadNode);
            return Optional.of(snapshot);
        } catch (Exception e) {
            // Malformed JSON / read error
            return Optional.empty();
        }
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

