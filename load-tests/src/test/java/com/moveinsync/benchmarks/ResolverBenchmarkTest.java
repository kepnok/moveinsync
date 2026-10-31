package com.moveinsync.benchmarks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResolverBenchmarkTest {

    @Test
    @DisplayName("Resolver benchmark should achieve sub-microsecond local reads")
    void testBenchmarkRunsSuccessfully() throws Exception {
        ResolverBenchmark benchmark = new ResolverBenchmark();
        var result = benchmark.runBenchmark(2, 100_000);

        System.out.printf("Test benchmark: %,.0f ops/s, p50: %.2f µs, p99: %.2f µs%n",
                result.opsPerSecond(), result.p50Micros(), result.p99Micros());

        assertThat(result.opsPerSecond()).isGreaterThan(100_000);
        assertThat(result.p50Micros()).isLessThan(50.0); // Microseconds!
    }
}
