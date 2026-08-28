package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.RateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private final Instant start = Instant.parse("2026-08-27T09:00:00Z");

    private RateLimitProperties properties(int maxAttempts, boolean enabled) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setMaxAttempts(maxAttempts);
        properties.setWindow(Duration.ofMinutes(1));
        properties.setEnabled(enabled);
        return properties;
    }

    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void attemptsUpToTheLimitAreAllowed() {
        RateLimiter limiter = new RateLimiter(properties(3, true), new MovableClock(start));

        assertTrue(limiter.tryAcquire("k"));
        assertTrue(limiter.tryAcquire("k"));
        assertTrue(limiter.tryAcquire("k"));
    }

    @Test
    void theAttemptAfterTheLimitIsRejected() {
        RateLimiter limiter = new RateLimiter(properties(3, true), new MovableClock(start));

        limiter.tryAcquire("k");
        limiter.tryAcquire("k");
        limiter.tryAcquire("k");

        assertFalse(limiter.tryAcquire("k"));
        assertFalse(limiter.tryAcquire("k"));
    }

    @Test
    void keysAreCountedIndependently() {
        RateLimiter limiter = new RateLimiter(properties(1, true), new MovableClock(start));

        assertTrue(limiter.tryAcquire("first"));
        assertFalse(limiter.tryAcquire("first"));
        assertTrue(limiter.tryAcquire("second"));
    }

    @Test
    void theCounterResetsOnceTheWindowPasses() {
        MovableClock clock = new MovableClock(start);
        RateLimiter limiter = new RateLimiter(properties(1, true), clock);

        assertTrue(limiter.tryAcquire("k"));
        assertFalse(limiter.tryAcquire("k"));

        clock.advance(Duration.ofMinutes(2));

        assertTrue(limiter.tryAcquire("k"), "Pencere gectikten sonra sayac sifirlanmaliydi.");
    }

    @Test
    void aDisabledLimiterNeverRejects() {
        RateLimiter limiter = new RateLimiter(properties(1, false), new MovableClock(start));

        for (int i = 0; i < 50; i++) {
            assertTrue(limiter.tryAcquire("k"));
        }
    }

    @Test
    void expiredWindowsCanBeEvictedSoTheMapDoesNotGrowForever() {
        MovableClock clock = new MovableClock(start);
        RateLimiter limiter = new RateLimiter(properties(5, true), clock);

        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        clock.advance(Duration.ofMinutes(2));
        limiter.evictExpired();

        assertTrue(limiter.trackedKeys() == 0);
    }
}
