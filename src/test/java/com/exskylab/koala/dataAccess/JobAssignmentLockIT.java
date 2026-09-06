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

class JobAssignmentLockIT extends AbstractIntegrationTest {

    @Autowired
    private JobAssignmentDao jobAssignmentDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(employerId, "lock-employer");
        insertUser(workerId, "lock-worker");
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, creator_id, title, status, employer_type,
                                  experience_required, insurance_required, min_age, max_age,
                                  start_date_time, end_date_time)
                values (?, now(), false, ?, 'Atama kilidi', 'OPEN', 'INDIVIDUAL',
                        false, false, 18, 65, now() + interval '1 day', now() + interval '2 days')
                """, jobId, employerId);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, created_at, is_deleted, job_id, worker_id, status, payment_status, work_hours)
                values (?, now(), false, ?, ?, 'CONFIRMED', 'HELD_IN_ESCROW', 0)
                """, assignmentId, jobId, workerId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from job_assignments where id = ?", assignmentId);
        jdbcTemplate.update("delete from jobs where id = ?", jobId);
        jdbcTemplate.update("delete from users where id in (?, ?)", employerId, workerId);
    }

    @Test
    void aSecondFinancialDecisionWaitsForTheAssignmentRowLock() throws Exception {
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                jobAssignmentDao.findByIdForUpdate(assignmentId).orElseThrow();
                firstHasLock.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstHasLock.await(10, TimeUnit.SECONDS));

            var second = executor.submit(() -> transactions.execute(status -> {
                secondStarted.countDown();
                return jobAssignmentDao.findByIdForUpdate(assignmentId).orElseThrow().getId();
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
                throw new IllegalStateException("assignment lock test timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("assignment lock test interrupted", exception);
        }
    }

    private void insertUser(UUID id, String prefix) {
        jdbcTemplate.update("""
                insert into users (id, created_at, is_deleted, email, password, is_employer,
                                   email_verified, phone_verified, identity_verified)
                values (?, now(), false, ?, 'test', false, true, false, false)
                """, id, prefix + "-" + id + "@koala.test");
    }
}
