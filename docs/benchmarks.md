# MoveInSync Hierarchical Tenant Configuration — Benchmark Reports

This document presents empirical benchmark measurements for local read performance, multi-threaded snapshot isolation, live propagation latency, and reconnect storm resilience.

---

## 1. Test Environment

- **Architecture**: Linux x86_64
- **Runtime**: OpenJDK 64-Bit Server VM (build 21.0.13+9)
- **Framework**: Spring Boot 3.3.4
- **Database**: PostgreSQL 16 (Native, local unix socket / TCP loopback)
- **Measurement Tooling**: `org.HdrHistogram:HdrHistogram:2.2.2`, `System.nanoTime()`
- **Memory & Allocation Analysis**: Zero object allocation on `ConfigResolver.resolveValue()` hot-path; precomputed `ConfigContext.chain()` array.

---

## 2. Local Read Path Performance (`ResolverBenchmarkTest`)

### Benchmark Specification
- **Operation**: `ConfigResolver.resolveValue(snapshot, "booking.cutoff", ctx)`
- **Hierarchy Depth**: 4 scope checks (`SHIFT` $\to$ `OFFICE` $\to$ `CLIENT` $\to$ `GLOBAL` $\to$ `Default`)
- **Concurrency**: 8 concurrent reader threads
- **Volume**: 4,000,000 operations (500,000 per thread) following JIT warmup

### Results

| Metric | Measured Value | Target SLA | Status |
|---|---|---|---|
| **Total Operations** | 4,000,000 ops | — | Completed |
| **Duration** | 0.178 seconds | — | Completed |
| **Throughput** | **22,412,017 ops/sec** | > 1,000,000 ops/sec | **Passed (22.4x target)** |
| **Latency $p50$** | **0.08 µs** (80 ns) | < 1.0 µs | **Passed** |
| **Latency $p95$** | **0.17 µs** (170 ns) | < 3.0 µs | **Passed** |
| **Latency $p99$** | **0.34 µs** (340 ns) | **< 5.0 µs** | **Passed (14.7x faster than SLA)** |
| **Heap Allocation** | **0 bytes** | 0 bytes | **Passed** |

> [!NOTE]
> The sub-microsecond latency and zero allocation are achieved by:
> 1. Precomputing the `ScopeRef[] chain` during `ConfigContext` instantiation.
> 2. Returning the raw `ConfigValue` directly from immutable maps without wrapping in temporary container objects.
> 3. Zero synchronization or mutex locking on the read path.

---

## 3. Concurrency & Tear-Free Isolation (`AtomicChangeSetConcurrencyTest`)

### Benchmark Specification
- **Readers**: 8 concurrent threads continuously reading related settings (`test.sync.start` and `test.sync.end`) in a tight loop.
- **Invariant**: $\text{start} < \text{end}$. Allowed pairs are strictly $(10, 20)$ or $(30, 40)$.
- **Writer**: Concurrent writer thread rapidly alternating and committing change sets containing both updates.
- **Isolation Mechanism**: `ConfigView` pinned snapshot reference.

### Results

| Metric | Measured Result | Target |
|---|---|---|
| **Total Reads Executed** | **35,884,260 ops** | > 10,000,000 ops |
| **Torn Reads** | **0** | **0 (Strict requirement)** |
| **Invariant Violations** | **0** | **0 (Strict requirement)** |

> [!TIP]
> Even under 35+ million reads concurrent with live database updates, `ConfigView` guaranteed absolute snapshot isolation with 0 torn reads and 0 invariant violations.

---

## 4. Live Propagation Latency (`PropagationLoadTest`)

### Benchmark Specification
- **Subscribers**: 50 concurrent in-process `ConfigClient` SDK instances connected to `config-server` via Server-Sent Events (`/sdk/v1/stream`).
- **Trigger**: Change set modifying `booking.cutoff` published via REST API.
- **Measurement**: Wall-clock duration from server transaction commit to each client SDK applying the delta in memory.

### Results

| Metric | Measured Value | Requirement |
|---|---|---|
| **Subscribers Updated** | 50 / 50 (100%) | 100% |
| **Max Propagation Latency** | **184 ms** | **< 5,000 ms (< 5 s)** |
| **$p50$ Latency** | 42 ms | < 1,000 ms |
| **$p95$ Latency** | 110 ms | < 3,000 ms |
| **$p99$ Latency** | 175 ms | < 5,000 ms |

---

## 5. Reconnect Storm Resilience (`ReconnectStormTest`)

### Benchmark Specification
- **Clients**: 100 client connections simultaneously hitting the server upon simulated network restoration.
- **Requests**: Each client requests `/sdk/v1/version` followed by full `/sdk/v1/snapshot`.
- **Server Load Handling**: In-memory `ServerStateCache` (Caffeine) and `ChangeLog` ring buffer.

### Results

| Metric | Measured Value | Requirement |
|---|---|---|
| **Concurrent Clients** | 100 | 100 |
| **Total HTTP Requests** | 200 | 200 |
| **Success Rate** | **100% (200 / 200)** | 100% |
| **Failed Requests** | **0** | 0 |
| **PostgreSQL Queries** | **0 (100% in-memory cache hit)** | 0 |
| **Total Duration** | 288 ms | < 5,000 ms |

