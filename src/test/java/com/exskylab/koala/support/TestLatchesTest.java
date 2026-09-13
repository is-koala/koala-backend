package com.exskylab.koala.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLatchesTest {

    @BeforeEach
    @AfterEach
    void isolateFromCiBudget() {
        System.clearProperty(TestLatches.PROPERTY);
    }

    @Test
    void localDefaultIsFiveSeconds() {
        assertEquals(Duration.ofSeconds(5), TestLatches.progressTimeout());
    }

    @Test
    void systemPropertyWidensTheBudget() {
        System.setProperty(TestLatches.PROPERTY, "30");
        assertEquals(Duration.ofSeconds(30), TestLatches.progressTimeout());
    }

    @Test
    void awaitProgressUsesTheConfiguredBudget() throws InterruptedException {
        System.setProperty(TestLatches.PROPERTY, "1");
        CountDownLatch never = new CountDownLatch(1);
        long started = System.nanoTime();
        assertThrows(IllegalStateException.class, () -> TestLatches.awaitProgress(never));
        long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue(waitedMs >= 900, "budget ignored; waited " + waitedMs + " ms");
    }

    @Test
    void awaitHoldIgnoresTheProgressBudget() throws InterruptedException {
        System.setProperty(TestLatches.PROPERTY, "1");
        CountDownLatch hold = new CountDownLatch(1);
        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(1_200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            hold.countDown();
        });
        releaser.start();
        TestLatches.awaitHold(hold);
        releaser.join();
    }

    @Test
    void localVerifyPassesAWiderBudget() throws Exception {
        String localVerify = Files.readString(Path.of(".cursor", "rules", "local-verify.mdc"));
        String claude = Files.readString(Path.of("CLAUDE.md"));
        assertTrue(
                localVerify.contains("-Dkoala.test.latch-timeout=30")
                        && claude.contains("-Dkoala.test.latch-timeout=30"),
                "Mac verify must pass -Dkoala.test.latch-timeout so 5s progress awaits do not flake");
    }

    @Test
    void listedRaceTestsDoNotHardCodeFiveSecondProgressWaits() throws Exception {
        List<Path> sources = List.of(
                Path.of("src/test/java/com/exskylab/koala/business/concretes/PushDeliveryClaimIT.java"),
                Path.of("src/test/java/com/exskylab/koala/dataAccess/ReportLockIT.java"),
                Path.of("src/test/java/com/exskylab/koala/webAPI/IdentityReviewApprovalIT.java"),
                Path.of("src/test/java/com/exskylab/koala/dataAccess/ActivePaymentAttemptConstraintIT.java"),
                Path.of("src/test/java/com/exskylab/koala/dataAccess/PayoutSettlementReconciliationIT.java"),
                Path.of("src/test/java/com/exskylab/koala/support/OverlappingWrites.java"));
        for (Path source : sources) {
            String text = Files.readString(source);
            assertTrue(
                    !text.contains("await(5, TimeUnit.SECONDS)")
                            && !text.contains("get(5, TimeUnit.SECONDS)")
                            && !text.contains("await(10, TimeUnit.SECONDS)")
                            && !text.contains("get(10, TimeUnit.SECONDS)"),
                    source + " still hard-codes a progress wait");
            if (text.contains("private static void await(CountDownLatch latch)")
                    || text.contains("private void await(CountDownLatch latch)")) {
                assertTrue(
                        text.contains("TestLatches.awaitHold(latch)"),
                        source + " still shares the progress budget with a hold latch");
            }
        }
    }
}
