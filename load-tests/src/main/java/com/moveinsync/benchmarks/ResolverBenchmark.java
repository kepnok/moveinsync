package com.moveinsync.benchmarks;

import com.moveinsync.config.core.model.*;
import org.HdrHistogram.Histogram;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class ResolverBenchmark {

    public record BenchmarkResult(int threads, long totalOps, double opsPerSecond, double p50Micros, double p99Micros, double maxMicros) {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== MoveInSync Local Read Performance Benchmark ===");
        ResolverBenchmark bench = new ResolverBenchmark();
        BenchmarkResult singleThread = bench.runBenchmark(1, 1_000_000);
        System.out.printf("1 Thread:  %,.0f ops/s | p50: %.2f µs | p99: %.2f µs | max: %.2f µs%n",
                singleThread.opsPerSecond(), singleThread.p50Micros(), singleThread.p99Micros(), singleThread.maxMicros());

        BenchmarkResult multiThread = bench.runBenchmark(8, 2_000_000);
        System.out.printf("8 Threads: %,.0f ops/s | p50: %.2f µs | p99: %.2f µs | max: %.2f µs%n",
                multiThread.opsPerSecond(), multiThread.p50Micros(), multiThread.p99Micros(), multiThread.maxMicros());
    }

    public BenchmarkResult runBenchmark(int threadCount, int totalOperations) throws Exception {
        UUID epoch = UUID.randomUUID();
        ConfigResolver resolver = new ConfigResolver();

        // Build a realistic snapshot with 100 settings and multi-level overrides
        Map<String, SettingDefinition> defs = new HashMap<>();
        Map<String, Map<ScopeRef, ConfigValue>> overrides = new HashMap<>();

        ScopeRef global = ScopeRef.global();
        ScopeRef client = ScopeRef.client("acme");
        ScopeRef office = ScopeRef.office("acme/blr-1");
        ScopeRef shift = ScopeRef.shift("acme/blr-1/night");

        for (int i = 0; i < 100; i++) {
            String key = "setting." + i;
            SettingDefinition def = new SettingDefinition(key, ValueType.NUMBER, NumberValue.of(i), ValidationRules.empty(), "Setting " + i);
            defs.put(key, def);

            Map<ScopeRef, ConfigValue> scopeMap = new HashMap<>();
            scopeMap.put(global, NumberValue.of(i * 10));
            scopeMap.put(client, NumberValue.of(i * 10 + 1));
            scopeMap.put(office, NumberValue.of(i * 10 + 2));
            if (i % 2 == 0) {
                scopeMap.put(shift, NumberValue.of(i * 10 + 3));
            }
            overrides.put(key, scopeMap);
        }

        ConfigSnapshot snapshot = new ConfigSnapshot(epoch, 1L, defs, overrides);
        ConfigContext ctx = ConfigContext.of("acme", "acme/blr-1", "acme/blr-1/night");

        // Warmup JVM
        for (int i = 0; i < 50_000; i++) {
            resolver.resolveValue(snapshot, "setting." + (i % 100), ctx);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        int opsPerThread = totalOperations / threadCount;
        List<Future<Histogram>> futures = new ArrayList<>();

        long startTime = System.nanoTime();
        for (int t = 0; t < threadCount; t++) {
            futures.add(executor.submit(() -> {
                Histogram histogram = new Histogram(3600000000L, 3);
                for (int i = 0; i < opsPerThread; i++) {
                    String key = "setting." + (i % 100);
                    long start = System.nanoTime();
                    ConfigValue val = resolver.resolveValue(snapshot, key, ctx);
                    long elapsedNanos = System.nanoTime() - start;
                    histogram.recordValue(Math.max(1, elapsedNanos));
                }
                return histogram;
            }));
        }

        Histogram aggregate = new Histogram(3600000000L, 3);
        for (Future<Histogram> f : futures) {
            aggregate.add(f.get());
        }
        long durationNanos = System.nanoTime() - startTime;
        executor.shutdown();

        double totalSeconds = durationNanos / 1_000_000_000.0;
        double opsPerSec = totalOperations / totalSeconds;
        double p50Micros = aggregate.getValueAtPercentile(50.0) / 1000.0;
        double p99Micros = aggregate.getValueAtPercentile(99.0) / 1000.0;
        double maxMicros = aggregate.getMaxValue() / 1000.0;

        return new BenchmarkResult(threadCount, totalOperations, opsPerSec, p50Micros, p99Micros, maxMicros);
    }
}
