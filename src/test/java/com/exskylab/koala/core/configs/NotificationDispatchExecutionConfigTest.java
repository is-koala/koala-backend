package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.NotificationDispatchExecutorProperties;
import com.exskylab.koala.core.schedulers.NotificationDispatchScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationDispatchExecutionConfigTest {

    @Test
    void theDedicatedDispatchExecutorIsBoundedAndAppliesBackpressureWithoutDroppingWork() throws Exception {
        NotificationDispatchExecutorProperties properties = new NotificationDispatchExecutorProperties();
        properties.setThreads(1);
        properties.setQueueCapacity(1);
        var executor = new NotificationDispatchExecutionConfig().notificationDispatchExecutor(properties);
        executor.afterPropertiesSet();
        CountDownLatch workerEntered = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch queuedTaskRan = new CountDownLatch(1);

        try {
            executor.execute(() -> {
                workerEntered.countDown();
                await(releaseWorker);
            });
            assertTrue(workerEntered.await(5, TimeUnit.SECONDS));
            executor.execute(queuedTaskRan::countDown);
            AtomicReference<Thread> overflowThread = new AtomicReference<>();

            executor.execute(() -> overflowThread.set(Thread.currentThread()));

            assertSame(Thread.currentThread(), overflowThread.get());
            assertEquals(1, queuedTaskRan.getCount());
        } finally {
            releaseWorker.countDown();
            executor.shutdown();
        }
    }

    @Test
    void theDispatchTriggerUsesItsDedicatedScheduler() throws Exception {
        Scheduled scheduled = NotificationDispatchScheduler.class.getMethod("processDueTasks")
                .getAnnotation(Scheduled.class);

        assertEquals("notificationDispatchTriggerScheduler", scheduled.scheduler());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("release timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
