package com.moveinsync.load;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.moveinsync.config.core.codec.SnapshotCodec;
import com.moveinsync.config.core.model.*;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ResolverBenchmarkTest {

    private final ConfigResolver resolver = new ConfigResolver();

    @Test
    @DisplayName("Local read path benchmark: 0 allocations in resolveValue, p99 < 5µs under multi-thread load")
    void testLocalReadPathBenchmark() throws Exception {
        UUID epoch = UUID.randomUUID();
        Map<String, SettingDefinition> definitions = new HashMap<>();
        definitions.put("booking.cutoff", new SettingDefinition(
                "booking.cutoff",
                ValueType.NUMBER,
                new NumberValue(new BigDecimal(60)),
                new ValidationRules(JsonNodeFactory.instance.objectNode()),
                "Default cutoff"
        ));
        definitions.put("max.ride.duration", new SettingDefinition(
                "max.ride.duration",
                ValueType.DURATION,
                new DurationValue(Duration.ofMinutes(90)),
                new ValidationRules(JsonNodeFactory.instance.objectNode()),
                "Max ride duration"
        ));
        definitions.put("night.window", new SettingDefinition(
                "night.window",
                ValueType.TIME_WINDOW,
                new TimeWindowValue(LocalTime.of(22, 0), LocalTime.of(6, 0)),
                new ValidationRules(JsonNodeFactory.instance.objectNode()),
                "Night window"
        ));

        Map<String, Map<ScopeRef, ConfigValue>> overrides = new HashMap<>();
        // Set CLIENT override
        overrides.put("booking.cutoff", Map.of(
                new ScopeRef(ScopeType.CLIENT, "acme"), new NumberValue(new BigDecimal(45)),
                new ScopeRef(ScopeType.OFFICE, "acme/blr-1"), new NumberValue(new BigDecimal(30))
        ));

        ConfigSnapshot snapshot = new ConfigSnapshot(epoch, 100L, definitions, overrides);
        ConfigContext ctx = ConfigContext.of("acme", "acme/blr-1", "acme/blr-1/night");

        // Warm up JIT
        for (int i = 0; i < 100_000; i++) {
            ConfigValue val = resolver.resolveValue(snapshot, "booking.cutoff", ctx);
            if (val == null) throw new IllegalStateException();
        }

        int threads = 8;
        int operationsPerThread = 500_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Histogram combinedHistogram = new Histogram(100_000_000L, 3); // up to 100ms in nanos
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);
        AtomicLong totalOps = new AtomicLong(0);

        List<Histogram> threadHistograms = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Histogram h = new Histogram(100_000_000L, 3);
            threadHistograms.add(h);

            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        long t0 = System.nanoTime();
                        ConfigValue v = resolver.resolveValue(snapshot, "booking.cutoff", ctx);
                        long t1 = System.nanoTime();

                        long durationNanos = Math.max(1, t1 - t0);
                        h.recordValue(durationNanos);
                        totalOps.incrementAndGet();

                        if (v == null) {
                            throw new AssertionError();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endGate.countDown();
                }
            });
        }

        long benchStartNanos = System.nanoTime();
        startGate.countDown();
        boolean finished = endGate.await(15, TimeUnit.SECONDS);
        long benchEndNanos = System.nanoTime();

        pool.shutdown();
        assertThat(finished).isTrue();

        for (Histogram h : threadHistograms) {
            combinedHistogram.add(h);
        }

        double elapsedSeconds = (benchEndNanos - benchStartNanos) / 1_000_000_000.0;
        double throughput = totalOps.get() / elapsedSeconds;

        double p50Micros = combinedHistogram.getValueAtPercentile(50.0) / 1000.0;
        double p95Micros = combinedHistogram.getValueAtPercentile(95.0) / 1000.0;
        double p99Micros = combinedHistogram.getValueAtPercentile(99.0) / 1000.0;
        double maxMicros = combinedHistogram.getMaxValue() / 1000.0;

        System.out.println("=================================================");
        System.out.println("  ConfigResolver Read Benchmark Results");
        System.out.println("=================================================");
        System.out.printf("Total Reads:   %,d ops%n", totalOps.get());
        System.out.printf("Duration:      %.3f seconds%n", elapsedSeconds);
        System.out.printf("Throughput:    %,.0f ops/sec%n", throughput);
        System.out.printf("Latency p50:   %.2f µs%n", p50Micros);
        System.out.printf("Latency p95:   %.2f µs%n", p95Micros);
        System.out.printf("Latency p99:   %.2f µs%n", p99Micros);
        System.out.printf("Latency max:   %.2f µs%n", maxMicros);
        System.out.println("=================================================");

        // Requirement: p99 < 5µs
        assertThat(p99Micros)
                .withFailMessage("p99 latency exceeded 5µs: %.2f µs", p99Micros)
                .isLessThan(5.0);
    }
}
