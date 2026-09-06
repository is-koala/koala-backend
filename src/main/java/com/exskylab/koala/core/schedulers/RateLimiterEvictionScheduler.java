package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.core.helpers.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RateLimiterEvictionScheduler {

    private final RateLimiter rateLimiter;

    public RateLimiterEvictionScheduler(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Scheduled(cron = "${rate-limit.eviction-cron:0 */10 * * * *}")
    public void evictExpiredWindows() {
        try {
            rateLimiter.evictExpired();
        } catch (RuntimeException e) {
            log.warn("Rate limit window eviction failed: {}", e.getMessage());
        }
    }
}
