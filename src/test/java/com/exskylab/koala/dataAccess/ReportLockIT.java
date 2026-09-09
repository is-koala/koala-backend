package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportLockIT extends AbstractIntegrationTest {

    @Autowired
    private ReportDao reportDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final UUID reporterId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                insert into users (id, created_at, is_deleted, email, password, is_employer,
                                   email_verified, phone_verified, identity_verified)
                values (?, now(), false, ?, 'test', false, true, false, false)
                """, reporterId, "report-lock-" + reporterId + "@koala.test");
        jdbcTemplate.update("""
                insert into reports (id, created_at, is_deleted, reporter_id, reason, status)
                values (?, now(), false, ?, 'FRAUD', 'OPEN')
                """, reportId, reporterId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from reports where id = ?", reportId);
        jdbcTemplate.update("delete from users where id = ?", reporterId);
    }

    @Test
    void aSecondModerationDecisionWaitsForTheReportRowLock() throws Exception {
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                reportDao.findByIdForUpdate(reportId).orElseThrow();
                firstHasLock.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstHasLock.await(10, TimeUnit.SECONDS));

            var second = executor.submit(() -> transactions.execute(status -> {
                secondStarted.countDown();
                return reportDao.findByIdForUpdate(reportId).orElseThrow().getId();
            }));
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("report lock test timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("report lock test interrupted", exception);
        }
    }
}
