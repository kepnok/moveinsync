package com.moveinsync.config.sdk.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.config.core.codec.SnapshotCodec;
import com.moveinsync.config.core.model.ChangeSet;
import com.moveinsync.config.core.model.ConfigSnapshot;
import com.moveinsync.config.sdk.TokenProvider;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class HttpServerApi implements ServerApi {
    private final String serverUrl;
    private final String serviceId;
    private final TokenProvider tokenProvider;
    private final Duration httpTimeout;
    private final HttpClient httpClient;
    private final SnapshotCodec codec;
    private final ObjectMapper mapper;

    public HttpServerApi(String serverUrl, String serviceId, TokenProvider tokenProvider, Duration httpTimeout) {
        this(serverUrl, serviceId, tokenProvider, httpTimeout, null);
    }

    public HttpServerApi(String serverUrl, String serviceId, TokenProvider tokenProvider, Duration httpTimeout, HttpClient httpClient) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.serviceId = serviceId;
        this.tokenProvider = tokenProvider;
        this.httpTimeout = httpTimeout != null ? httpTimeout : Duration.ofSeconds(2);
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .connectTimeout(this.httpTimeout)
                .build();
        this.codec = new SnapshotCodec();
        this.mapper = new ObjectMapper();
    }

    private HttpRequest.Builder requestBuilder(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + path))
                .timeout(httpTimeout)
                .header("X-Service-ID", serviceId)
                .header("Accept", "application/json");

        if (tokenProvider != null) {
            String token = tokenProvider.token();
            if (token != null && !token.isBlank()) {
                b.header("Authorization", "Bearer " + token);
            }
        }
        return b;
    }

    @Override
    public SyncResponse fetchSnapshot(String tenantId) {
        try {
            HttpRequest req = requestBuilder("/sdk/v1/snapshot?tenantId=" + tenantId).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw new RuntimeException("Server returned HTTP " + resp.statusCode() + ": " + resp.body());
            }

            JsonNode root = mapper.readTree(resp.body());
            UUID epoch = UUID.fromString(root.get("epoch").asText());
            long version = root.get("version").asLong();
            ConfigSnapshot snapshot = codec.decode(root.get("snapshot"));

            return new SyncResponse("FULL", epoch, null, null, version, null, snapshot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch snapshot for tenant " + tenantId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public SyncResponse fetchChanges(String tenantId, long sinceVersion, UUID epoch) {
        try {
            String path = "/sdk/v1/changes?tenantId=" + tenantId + "&since=" + sinceVersion;
            if (epoch != null) {
                path += "&epoch=" + epoch;
            }

            HttpRequest req = requestBuilder(path).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw new RuntimeException("Server returned HTTP " + resp.statusCode() + ": " + resp.body());
            }

            JsonNode root = mapper.readTree(resp.body());
            String mode = root.get("mode").asText();
            UUID respEpoch = UUID.fromString(root.get("epoch").asText());

            if ("FULL".equalsIgnoreCase(mode)) {
                long version = root.get("version").asLong();
                ConfigSnapshot snapshot = codec.decode(root.get("snapshot"));
                return new SyncResponse("FULL", respEpoch, null, null, version, null, snapshot);
            } else {
                long from = root.get("fromVersion").asLong();
                long to = root.get("toVersion").asLong();
                List<ChangeSet> changeSets = new ArrayList<>();
                JsonNode csArr = root.get("changeSets");
                if (csArr != null && csArr.isArray()) {
                    for (JsonNode csNode : csArr) {
                        changeSets.add(codec.decodeChangeSet(csNode));
                    }
                }
                return new SyncResponse("DELTA", respEpoch, from, to, to, changeSets, null);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch changes for tenant " + tenantId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public VersionResponse fetchVersion(String tenantId) {
        try {
            HttpRequest req = requestBuilder("/sdk/v1/version?tenantId=" + tenantId).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw new RuntimeException("Server returned HTTP " + resp.statusCode() + ": " + resp.body());
            }

            JsonNode root = mapper.readTree(resp.body());
            UUID epoch = UUID.fromString(root.get("epoch").asText());
            long version = root.get("version").asLong();
            return new VersionResponse(epoch, version);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch version for tenant " + tenantId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public AutoCloseable openStream(String tenantId, Consumer<Long> onVersionNotification, Runnable onDisconnect) {
        AtomicBoolean closed = new AtomicBoolean(false);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/sdk/v1/stream?tenantId=" + tenantId))
                .header("X-Service-ID", serviceId)
                .header("Accept", "text/event-stream")
                .header("Authorization", tokenProvider != null && tokenProvider.token() != null ? "Bearer " + tokenProvider.token() : "")
                .GET()
                .build();

        CompletableFuture<Void> future = httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        if (onDisconnect != null) onDisconnect.run();
                        return;
                    }

                    try (InputStream is = response.body();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                        String line;
                        while (!closed.get() && (line = reader.readLine()) != null) {
                            if (line.startsWith("data:")) {
                                String json = line.substring(5).trim();
                                try {
                                    JsonNode node = mapper.readTree(json);
                                    if (node.hasNonNull("version")) {
                                        long v = node.get("version").asLong();
                                        onVersionNotification.accept(v);
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception e) {
                        if (!closed.get() && onDisconnect != null) {
                            onDisconnect.run();
                        }
                    }
                })
                .exceptionally(t -> {
                    if (!closed.get() && onDisconnect != null) {
                        onDisconnect.run();
                    }
                    return null;
                });

        return () -> {
            closed.set(true);
            future.cancel(true);
        };
    }
}
