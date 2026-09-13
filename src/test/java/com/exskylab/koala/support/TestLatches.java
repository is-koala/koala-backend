package com.exskylab.koala.support;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TestLatches {

    public static final String PROPERTY = "koala.test.latch-timeout";

    private static final Duration LOCAL_DEFAULT = Duration.ofSeconds(5);

    private TestLatches() {
    }

    public static Duration progressTimeout() {
        String raw = System.getProperty(PROPERTY);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("KOALA_TEST_LATCH_TIMEOUT");
        }
        if (raw == null || raw.isBlank()) {
            return LOCAL_DEFAULT;
        }
        return Duration.ofSeconds(Long.parseUnsignedLong(raw.trim()));
    }

    public static void awaitProgress(CountDownLatch latch) {
        try {
            if (!latch.await(progressTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "test latch timed out after " + progressTimeout().toSeconds() + "s");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for a test latch", interrupted);
        }
    }

    public static <T> T getProgress(Future<T> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(progressTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }
}
