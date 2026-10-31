# MoveInSync Hierarchical Tenant Configuration — Failure Modes & Mitigations

This document outlines the failure modes, blast radii, automated defenses, and disaster recovery procedures of the MoveInSync Configuration System.

---

## 1. Failure Modes Matrix

| Fault Scenario | Blast Radius | Automated Mitigation | Recovery Verification |
|---|---|---|---|
| **Config Server Crash / Outage** | Write pipeline halted; no live propagation | SDK continues answering reads locally from memory snapshot; disk cache loaded on cold restarts | Verified in `FailureInjectionTest` and `kill-server-test.sh` |
| **PostgreSQL DB Down** | Write pipeline returns 500; publish blocked | Server read endpoints (`/sdk/v1/snapshot`, `/delta`, `/version`) served 100% in-memory from `ServerStateCache` | Clients continue reading and syncing existing state without disruption |
| **Network Partition (SDK ↔ Server)** | Affected client SDKs stop receiving live updates | SDK serves last-known good snapshot from memory; `SyncManager` enters exponential backoff with jitter | Upon reconnection, delta watermark fetches missed versions |
| **SSE Stream Disconnect** | Push notifications paused | SDK automatically falls back to periodic polling backstop (10–30s jitter); background reconnect loop | SSE reconnect re-establishes push channel with zero data loss |
| **Disk Cache Corruption / Partial Write** | Local disk file on client node corrupted | Atomic write to temp file (`.tmp`) + `fsync` + SHA-256 checksum check before atomic replace; invalid checksum triggers fresh download | Verified in `FileSnapshotStoreTest` |
| **Reconnect Storm (Thundering Herd)** | Server flooded by hundreds of reconnecting clients | Delta/Snapshot/Version endpoints never touch database; served lock-free from memory; HTTP keep-alive | Verified in `ReconnectStormTest` (100 simultaneous clients, 0 errors) |
| **Slow SSE Consumer** | One slow client could consume server memory buffers | Bounded SSE queue (256 events); if queue overflows, slowest consumer session is disconnected | Client reconnects and syncs latest delta watermark |
| **Bad Rollout Introduced** | Affected offices have invalid or degraded settings | Emergency cancellation via `POST /api/v1/rollouts/{id}/cancel` immediately removes staged office overrides | Verified in `RolloutAndRollbackIntegrationTest` |
| **Cross-Tenant Probe Attack** | Malicious actor guessing tenant IDs | Control plane responds with `404 Not Found` (never 403), completely concealing whether the tenant exists | Verified in `SecurityAndObservabilityTest` |
| **Server DB Wiped / Reset** | Server version reset to 0; client watermark higher | Server generates new `epoch` UUID; client detects epoch mismatch, resets watermark, and fetches full snapshot | Verified in `DeltaApplierTest` and `ConfigClientTest` |

---

## 2. Detailed Failure Analyses & Mitigations

### 2.1 Config Server Process Crash
**Scenario**: The `config-server` JVM process crashes or is killed (`SIGKILL`).
- **Behavior of Active Services**: Embedded SDK instances retain the active `ConfigSnapshot` in memory (`AtomicReference`). Read operations (`client.getLong(...)`, `client.view(...)`) experience **zero downtime, zero latency degradation, and zero exceptions**.
- **Behavior of Restarting Services**: If a service restarts while `config-server` is down, `ConfigClient` loads the cached configuration from `snapshot-{tenantId}.json` on disk, verifies its SHA-256 checksum, and marks itself ready.
- **Automated Reconnect**: The background `SyncManager` retries the server connection using exponential backoff with random jitter ($1\text{s} \to 2\text{s} \to 4\text{s} \to \dots \to 30\text{s}$).

### 2.2 PostgreSQL Outage
**Scenario**: PostgreSQL runs out of disk space, restarts, or loses connectivity to `config-server`.
- **Control Plane**: Publishing new change sets fails cleanly with an HTTP 500 error and a sanitized error message (no SQL exceptions or database credentials leaked).
- **Client Plane**: Read/sync requests from SDK clients (`/sdk/v1/snapshot`, `/sdk/v1/delta`, `/sdk/v1/version`) are served entirely from `ServerStateCache` (Caffeine cache) and the in-memory `ChangeLog` ring buffer. **The database is never queried on the sync path**.

### 2.3 Disk Cache Corruption Prevention
**Scenario**: Operating system crashes or node loses power while the SDK is writing a snapshot update to disk.
- **Mitigation Architecture**:
  1. SDK writes the JSON snapshot to a temporary file: `snapshot-{tenant}.json.tmp.{UUID}`.
  2. SDK computes the SHA-256 cryptographic digest of the written bytes and appends a header/footer envelope.
  3. SDK issues a forced disk sync via `FileChannel.force(true)` (`fsync`).
  4. SDK executes an atomic filesystem rename:
     ```java
     Files.move(tempFile, finalFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
     ```
  5. On startup, if the SHA-256 hash fails validation, the corrupted file is discarded and logged as a warning; the SDK then fetches a fresh snapshot from the server.

### 2.4 Slow SSE Consumers & Memory Exhaustion
**Scenario**: A client network connection becomes congested or a client process pauses for garbage collection, causing server SSE output buffers to accumulate events.
- **Mitigation Architecture**:
  - Each `SseSession` maintains a bounded concurrent queue with a maximum capacity of 256 events.
  - If a slow client's queue fills up, `offer()` fails:
    ```java
    if (!eventQueue.offer(event)) {
        logger.warn("SSE slow consumer detected for serviceId={}, dropping connection", serviceId);
        complete();
    }
    ```
  - The server immediately terminates the connection, reclaiming memory buffers. The client then reconnects via normal backoff and pulls missed deltas using its version watermark.

---

## 3. Disaster Recovery Walkthrough

### Emergency Rollback of a Faulty Production Change
If an unintended configuration change is published:
1. Preview the inverse diff:
   ```bash
   curl -X POST http://localhost:8080/api/v1/rollback/preview \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"targetVersion": 41, "reason": "Preview undo", "tenantId": "acme"}'
   ```
2. Execute atomic rollback:
   ```bash
   curl -X POST http://localhost:8080/api/v1/rollback \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"targetVersion": 41, "reason": "Revert to v41", "tenantId": "acme"}'
   ```
3. The server computes the exact inverse change items, advances the monotonic version to $V + 1$, persists to DB, and broadcasts the new version to all connected SDK clients via SSE in $<5$ seconds.

