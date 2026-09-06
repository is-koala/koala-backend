package com.exskylab.koala.core.logging;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScheduledJobLogContextIT extends AbstractIntegrationTest {

    @Autowired
    @Qualifier("taskScheduler")
    private TaskScheduler taskScheduler;

    @Autowired
    @Qualifier("messageBrokerTaskScheduler")
    private TaskScheduler messageBrokerTaskScheduler;

    @Test
    void scheduledJobsDoNotShareTheStompHeartbeatScheduler() {
        assertNotSame(messageBrokerTaskScheduler, taskScheduler,
                "Zamanlanmis isler STOMP kalp atisi zamanlayicisiyla ayni tek is parcacigini paylasmamali");
    }

    @Test
    void aScheduledRunGetsItsOwnRequestIdInTheMdc() throws Exception {
        String first = requestIdOfAScheduledRun();
        String second = requestIdOfAScheduledRun();

        assertNotNull(first, "Zamanlayici calismasi MDC'de requestId tasimiyor");
        assertNotEquals(first, second, "Her zamanlayici calismasi kendi requestId'sini almali");
    }

    @Test
    void oneScheduledRunNeverInheritsTheContextOfThePreviousOne() throws Exception {
        CompletableFuture<String> polluted = new CompletableFuture<>();
        taskScheduler.schedule(() -> {
            MDC.put("path", "onceki-calisma");
            polluted.complete("bitti");
        }, Instant.now());
        polluted.get(10, TimeUnit.SECONDS);

        CompletableFuture<String> inherited = new CompletableFuture<>();
        taskScheduler.schedule(() -> inherited.complete(MDC.get("path")), Instant.now());

        assertNull(inherited.get(10, TimeUnit.SECONDS),
                "Onceki calismanin log baglami sonraki calismaya sizmamali");
    }

    private String requestIdOfAScheduledRun() throws Exception {
        CompletableFuture<String> captured = new CompletableFuture<>();
        taskScheduler.schedule(() -> captured.complete(MDC.get("requestId")), Instant.now());
        return captured.get(10, TimeUnit.SECONDS);
    }
}
