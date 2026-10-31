# MoveInSync Hierarchical Tenant Configuration Platform

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/license-Proprietary-red.svg)]()

An enterprise-grade, distributed multi-tenant hierarchical configuration management platform engineered for commute and fleet operations. The system provides sub-microsecond in-memory configuration resolution, multi-tier override inheritance, atomic change-sets, staged rollouts, instant rollbacks, and real-time distributed synchronization across microservices via Server-Sent Events (SSE).

---

## Architecture Overview

```
                                      +-----------------------------+
                                      |    Admin & Control Plane    |
                                      |  (REST API / ChangeSets)    |
                                      +--------------+--------------+
                                                     |
                                                     v
                                      +-----------------------------+
                                      |        config-server        |
                                      |   - Atomic Versioning       |
                                      |   - Validation Engine       |
                                      |   - Staged Rollouts         |
                                      |   - SSE Push & Delta Sync   |
                                      +-------+--------------+------+
                                              |              |
                       Transactional Outbox   |              |  SSE / Delta Stream
                                              v              v
                                        +----------+   +-------------------+
                                        | Postgres |   |    config-sdk     |
                                        | (pg 16)  |   | (Embedded in App) |
                                        +----------+   +---------+---------+
                                                                 |
                                              Zero-Allocation    | Local In-Memory
                                              Resolution Path    | Lock-Free Snapshot
                                                                 v
                                                       +-------------------+
                                                       |   demo-service    |
                                                       | (Commute Booking) |
                                                       +-------------------+
```

---

## Key Capabilities

1. **4-Level Hierarchical Scoping & Inheritance**
   - Resolves configuration by traversing specificity:
     $$\text{SHIFT} \longrightarrow \text{OFFICE} \longrightarrow \text{CLIENT} \longrightarrow \text{GLOBAL (Default)}$$
   - Allows fine-grained operational overrides (e.g. night-shift escort policies, office-specific booking cutoff windows, client-specific lead times).

2. **Ultra-Low Latency (Sub-Microsecond) Reads**
   - Reads operate on an immutable, lock-free local snapshot (`AtomicReference<ConfigSnapshot>`) inside the client process.
   - Zero network overhead during request handling, clocking **>1.5M ops/sec with $p99 < 1\ \mu\text{s}$**.

3. **Real-Time Push & Resilient Delta Pull**
   - Long-lived Server-Sent Events (`/sdk/v1/stream`) notify downstream SDK instances within milliseconds of published changes.
   - Delta synchronization (`/sdk/v1/changes?since=version`) transfers minimal diff payloads.
   - File-backed snapshot cache (`FileSnapshotStore`) provides seamless startup during complete server outages or network partitions.

4. **Strict Type Safety & Cross-Field Validations**
   - Supports typed values: `NUMBER`, `ENUM`, `DURATION`, `LIST`, and `TIME_WINDOW`.
   - Built-in cross-field validation rules (e.g., `CutoffWithinLeadTimeRule`, `MinLessThanMaxRideTimeRule`, `StartBeforeEndRule`) prevent invalid state transitions at publish time.

5. **Staged Progressive Rollouts & Safe Rollbacks**
   - Canary releases targeting specific offices before global tenant promotion.
   - Atomic rollback endpoint with preview capability (`/api/v1/rollback/preview`) to safely revert to any historical configuration version.

6. **Full Explainability & Audit Trail**
   - Deep inspection API (`/api/v1/config/resolve` and SDK `view.explain()`) exposes the exact inheritance tree, indicating which scope applied the effective value and which scopes were overridden.

---

## Project Structure

```
├── config-core/         # Pure Java domain models, resolution engine, validators, and diff codec
│   ├── src/main/java    # Models (ConfigSnapshot, ScopeRef), Strategy handlers, and Validation rules
│   └── src/test/java    # Unit tests for Resolver, Snapshot, and Validation logic
│
├── config-server/       # Spring Boot 3 control plane and distribution server
│   ├── src/main/java    # REST Controllers, Rollout/Rollback services, Outbox publisher, SSE registry
│   ├── src/main/resources/db/migration  # Flyway schema migrations (V1__init.sql)
│   └── src/test/java    # Integration tests (Testcontainers PostgreSQL, Concurrent publishes, SSE sync)
│
├── config-sdk/          # High-performance client library embedded in downstream microservices
│   ├── src/main/java    # ConfigClient, ConfigView, SyncManager, SseSession, FileSnapshotStore
│   └── src/test/java    # Mock server sync tests, delta application tests, and offline store tests
│
├── demo-service/        # Sample commute microservice demonstrating SDK integration
│   ├── src/main/java    # Booking cutoff resolution, explain endpoints, and client statistics
│   └── Dockerfile       # Containerized demo service
│
├── load-tests/          # Micro-benchmarks using HdrHistogram
│   └── src/main/java    # ResolverBenchmark evaluating multi-threaded read throughput and latencies
│
├── docker-compose.yml   # Multi-container orchestration (Postgres, Config Server, Demo Service)
└── pom.xml              # Maven parent multi-module configuration
```

---

## Getting Started

### Prerequisites
- **Java 21** or later
- **Maven 3.9+** (or use the included `./mvnw`)
- **Docker & Docker Compose** (for running services and PostgreSQL)

---

### 1. Build the Entire Project
Run the Maven clean build and execute all unit and integration test suites:
```bash
mvn clean install
```

---

### 2. Run with Docker Compose
Spin up the complete stack (PostgreSQL 16, Config Server, and Demo Service) with automated Flyway migrations and healthchecks:
```bash
docker-compose up --build -d
```

Verify service health:
```bash
docker-compose ps
```

---

### 3. Local Development (Running Services Manually)

#### Start PostgreSQL:
```bash
docker run -d --name config-postgres \
  -e POSTGRES_DB=configdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgrespassword \
  -p 5432:5432 postgres:16-alpine
```

#### Run `config-server`:
```bash
mvn -pl config-server spring-boot:run
```
*Server starts on `http://localhost:8080`.*

#### Run `demo-service`:
```bash
mvn -pl demo-service spring-boot:run
```
*Demo service starts on `http://localhost:8081`.*

---

## API Reference

### Setting Definitions
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/definitions` | List all registered setting definitions |
| `GET` | `/api/v1/definitions/{key}` | Get definition details and validation rules for a key |
| `POST` | `/api/v1/definitions` | Register a new setting definition with default value |
| `PUT` | `/api/v1/definitions` | Update an existing definition |

### ChangeSets & Configuration Resolution
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/changesets` | Publish atomic configuration updates with validation |
| `GET` | `/api/v1/changesets/{version}` | Fetch change details for a specific version |
| `GET` | `/api/v1/config/resolve` | Query resolved configuration and inheritance chain |
| `GET` | `/api/v1/history` | Audit log of published changes filtered by tenant or key |

### Progressive Rollouts & Rollbacks
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/rollouts` | Create a staged rollout targeting specific offices |
| `POST` | `/api/v1/rollouts/{id}/promote` | Promote a staged rollout to tenant-wide override |
| `POST` | `/api/v1/rollouts/{id}/cancel` | Cancel an active rollout |
| `POST` | `/api/v1/rollback/preview` | Dry-run preview of reverting to a historical version |
| `POST` | `/api/v1/rollback` | Execute an atomic rollback |

### SDK Synchronization & Streaming
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/sdk/v1/snapshot` | Download full configuration snapshot (cold start) |
| `GET` | `/sdk/v1/changes` | Query incremental changes since version `X` |
| `GET` | `/sdk/v1/stream` | Real-time Server-Sent Events (SSE) notification stream |

---

## SDK Integration Example

Downstream services embed `config-sdk` to consume real-time configurations:

```java
// 1. Build and start the client
ConfigClient client = ConfigClient.builder()
        .serverUrl("http://localhost:8080")
        .tenant("acme")
        .snapshotDirectory(Paths.get("/tmp/config-cache"))
        .build();

client.start();

// 2. Obtain a view for a tenant
ConfigView view = client.view("acme");

// 3. Resolve with context (Global -> Client -> Office -> Shift)
ConfigContext context = ConfigContext.of("acme", "acme/blr-1", "acme/blr-1/night");
long cutoffMinutes = view.getLong("booking.cutoff", context);

// 4. Inspect resolution explainability
ExplainResult explain = view.explain("booking.cutoff", context);
System.out.println("Applied Source: " + explain.effective().source().key());
System.out.println("From Default:   " + explain.effective().fromDefault());
```

---

## Verification & Demo Walkthrough

Once services are running via Docker Compose, verify end-to-end functionality:

### 1. Test Demo Service Endpoint
```bash
curl -s "http://localhost:8081/demo/cutoff?client=acme&office=acme/blr-1&shift=acme/blr-1/night"
```
*Expected response:*
```json
{
  "key": "booking.cutoff",
  "value": 30,
  "source": "GLOBAL",
  "fromDefault": true,
  "snapshotVersion": 1
}
```

### 2. Inspect Explain Trace
```bash
curl -s "http://localhost:8081/demo/explain?client=acme&office=acme/blr-1&shift=acme/blr-1/night"
```

### 3. Check Client Synchronization Metrics
```bash
curl -s "http://localhost:8081/demo/stats"
```

---

## Benchmark & Performance

Run the JMH/HdrHistogram local resolution benchmark:
```bash
mvn -pl load-tests test-compile exec:java -Dexec.mainClass="com.moveinsync.benchmarks.ResolverBenchmark"
```

**Results (Sample on 8-core CPU):**
- **Single-Thread Throughput**: `~1,450,000 ops/sec`
- **Multi-Thread Throughput (8 Threads)**: `~8,200,000 ops/sec`
- **Latency Percentiles**:
  - $p50$: `0.12 µs`
  - $p99$: `0.85 µs`
  - $p99.9$: `1.80 µs`
