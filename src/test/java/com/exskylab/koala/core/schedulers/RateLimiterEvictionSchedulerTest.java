package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.core.helpers.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RateLimiterEvictionSchedulerTest {

    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final RateLimiterEvictionScheduler scheduler = new RateLimiterEvictionScheduler(rateLimiter);

    @Test
    void everyRunEvictsTheWindowsThatHaveExpired() {
        scheduler.evictExpiredWindows();

        verify(rateLimiter).evictExpired();
    }

    @Test
    void aFailingRunDoesNotEscapeTheScheduler() {
        doThrow(new IllegalStateException("boom")).when(rateLimiter).evictExpired();

        assertDoesNotThrow(scheduler::evictExpiredWindows);
    }
}
