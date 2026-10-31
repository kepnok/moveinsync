package com.moveinsync.config.sdk.sync;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class BackoffPolicy {
    private final long baseMillis;
    private final long maxMillis;
    private final AtomicInteger attempt = new AtomicInteger(0);

    public BackoffPolicy() {
        this(500, 30_000);
    }

    public BackoffPolicy(long baseMillis, long maxMillis) {
        this.baseMillis = baseMillis;
        this.maxMillis = maxMillis;
    }

    public Duration nextDelay() {
        int n = attempt.getAndIncrement();
        // Prevent overflow with bit-shift: cap n at 30
        int safeN = Math.min(n, 30);
        long exponential = baseMillis * (1L << safeN);
        long ceiling = Math.min(maxMillis, exponential);
        long delay = ThreadLocalRandom.current().nextLong(0, ceiling + 1);
        return Duration.ofMillis(delay);
    }

    public void reset() {
        attempt.set(0);
    }

    public int currentAttempt() {
        return attempt.get();
    }
}

