package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.support.OverlappingWrites;
import com.exskylab.koala.support.PaymentFixtures;
import com.exskylab.koala.support.RunbookSql;
import com.exskylab.koala.support.TestLatches;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ActivePaymentAttemptConstraintIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JobAssignmentDao jobAssignmentDao;

    @Autowired
    private PaymentDao paymentDao;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private PaymentFixtures fixtures;

    @BeforeEach
    void fixtures() {
        fixtures = new PaymentFixtures(jdbcTemplate);
    }

    @Test
    void databaseAllowsOnlyOneActiveAttemptPerAssignment() {
        String definition = activeAttemptIndexDefinition();

        assertTrue(definition.contains("UNIQUE INDEX"));
        assertTrue(definition.contains("job_assignment_id"));

        String tokenDefinition = jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = 'uk_payments_provider_token'",
                String.class);
        assertTrue(tokenDefinition.contains("UNIQUE INDEX"));
        assertTrue(tokenDefinition.contains("provider_token"));

        assertEquals("YES", jdbcTemplate.queryForObject("""
                select is_nullable from information_schema.columns
                where table_schema = 'public' and table_name = 'payments'
                  and column_name = 'provider_submerchant_key'
                """, String.class));
    }

    @Test
    void theIndexGuardsExactlyTheStatusSetJavaTreatsAsActive() {
        Set<PaymentAttemptStatus> guardedBySql = statusesNamedIn(activeAttemptIndexDefinition());

        assertEquals(EnumSet.copyOf(PaymentAttemptStatus.ACTIVE), guardedBySql,
                "PaymentAttemptStatus.ACTIVE ile uk_payments_one_active_attempt ayni kumeyi tutmali; "
                        + "Java'ya eklenip indekse eklenmeyen bir durum cift tahsilat deligi acar. "
                        + "Java=" + PaymentAttemptStatus.ACTIVE + " SQL=" + guardedBySql);
    }

    @Test
    void everyStatusJavaCallsActiveIsAlsoBlockedByTheDatabaseAtInsertTime() {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        fixtures.seedAssignment(jobId, assignmentId);

        try {
            for (PaymentAttemptStatus active : PaymentAttemptStatus.ACTIVE) {
                UUID sitting = UUID.randomUUID();
                jdbcTemplate.update("""
                        insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id,
                                              status, currency, verification_attempts)
                        values (?, now(), false, ?, ?, ?::varchar, 'TRY', 0)
                        """, sitting, assignmentId, UUID.randomUUID().toString(), active.name());

                assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                        insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id,
                                              status, currency, verification_attempts)
                        values (?, now(), false, ?, ?, 'INITIATED', 'TRY', 0)
                        """, UUID.randomUUID(), assignmentId, UUID.randomUUID().toString()),
                        active + " Java'da aktif sayiliyor ama veritabani ustune ikinci deneme aciyor.");

                jdbcTemplate.update("delete from payments where id = ?", sitting);
            }
        } finally {
            fixtures.cleanUp(jobId, assignmentId);
        }
    }

    @Test
    void twoConcurrentReservationsProduceOneAttemptAndOneConflict() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        fixtures.seedAssignment(jobId, assignmentId);
        widenTheInsertWindow();

        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            CommissionCalculator.Breakdown breakdown = PaymentFixtures.breakdown();
            java.util.concurrent.Callable<String> request = () -> {
                start.await();
                JobAssignment assignment = new JobAssignment();
                assignment.setId(assignmentId);
                assignment.setPricingVersion(PaymentFixtures.PRICING_VERSION);
                assignment.setCurrency("TRY");
                try {
                    paymentService.startAttempt(assignment, breakdown);
                    return "SUCCESS";
                } catch (ConflictException conflict) {
                    assertEquals("PAYMENT_ATTEMPT_IN_PROGRESS", conflict.getErrorCode());
                    return "CONFLICT";
                }
            };

            var first = executor.submit(request);
            var second = executor.submit(request);
            start.countDown();

            assertEquals(List.of("CONFLICT", "SUCCESS"),
                    java.util.stream.Stream.of(TestLatches.getProgress(first),
                                    TestLatches.getProgress(second)).sorted().toList());
            assertEquals(1, jdbcTemplate.queryForObject("""
                    select count(*) from payments
                    where job_assignment_id = ? and status in ('INITIATED', 'VERIFY_PENDING', 'IN_REVIEW')
                    """, Integer.class, assignmentId));
        } finally {
            narrowTheInsertWindow();
            fixtures.cleanUp(jobId, assignmentId);
        }
    }

    @Test
    void reservationWaitsForTheFinancialLockAndRechecksTheCommittedAssignmentState() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        fixtures.seedAssignment(jobId, assignmentId);

        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CommissionCalculator.Breakdown breakdown = PaymentFixtures.breakdown();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var financialDecision = executor.submit(() -> transactions.executeWithoutResult(status -> {
                JobAssignment assignment = jobAssignmentDao.findByIdForUpdate(assignmentId).orElseThrow();
                assignment.setPaymentStatus(com.exskylab.koala.entities.PaymentStatus.HELD_IN_ESCROW);
                jobAssignmentDao.saveAndFlush(assignment);
                firstHasLock.countDown();
                await(releaseFirst);
            }));
            TestLatches.awaitProgress(firstHasLock);

            var reservation = executor.submit(() -> {
                JobAssignment stale = new JobAssignment();
                stale.setId(assignmentId);
                stale.setStatus(com.exskylab.koala.entities.AssignmentStatus.CONFIRMED);
                stale.setPaymentStatus(com.exskylab.koala.entities.PaymentStatus.PENDING_COLLECTION);
                try {
                    paymentService.startAttempt(stale, breakdown);
                    return "SUCCESS";
                } catch (ConflictException conflict) {
                    return conflict.getErrorCode();
                }
            });

            assertThrows(TimeoutException.class, () -> reservation.get(250, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            TestLatches.getProgress(financialDecision);
            assertEquals("JOB_ASSIGNMENT_NOT_READY_FOR_PAYMENT", TestLatches.getProgress(reservation));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "select count(*) from payments where job_assignment_id = ?", Integer.class, assignmentId));
        } finally {
            releaseFirst.countDown();
            fixtures.cleanUp(jobId, assignmentId);
        }
    }

    @Test
    void anAbandonedInitiationKeepsBlockingPaymentAndTheSweepOnlyMakesItVisible() {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        fixtures.seedAssignment(jobId, assignmentId);
        seedStuckInitiation(paymentId, assignmentId);

        try {
            CommissionCalculator.Breakdown breakdown = PaymentFixtures.breakdown();
            JobAssignment assignment = new JobAssignment();
            assignment.setId(assignmentId);
            assignment.setPricingVersion(PaymentFixtures.PRICING_VERSION);
            assignment.setCurrency("TRY");

            ConflictException blocked = assertThrows(ConflictException.class,
                    () -> paymentService.startAttempt(assignment, breakdown));
            assertEquals("PAYMENT_ATTEMPT_IN_PROGRESS", blocked.getErrorCode());

            assertEquals(List.of(paymentId), paymentService.findStuckInitiationIds());
            assertTrue(paymentService.abandonStuckInitiation(paymentId));

            assertEquals("VERIFY_PENDING", statusOf(paymentId));
            assertEquals("{payment.initiation.abandoned}", failureReasonOf(paymentId));
            assertNull(jdbcTemplate.queryForObject(
                    "select next_verification_at from payments where id = ?", Instant.class, paymentId));

            ConflictException stillBlocked = assertThrows(ConflictException.class,
                    () -> paymentService.startAttempt(assignment, breakdown));
            assertEquals("PAYMENT_ATTEMPT_IN_PROGRESS", stillBlocked.getErrorCode());

            assertTrue(paymentService.findDueVerificationAttemptIds().stream().noneMatch(paymentId::equals),
                    "Supurulen satirda token yok; mutabakat isi onu almamali, yoksa sonsuza kadar bosa doner.");
            assertTrue(paymentService.findStuckInitiationIds().stream().noneMatch(paymentId::equals),
                    "Supurme idempotent olmali; satir ikinci turda yeniden secilmemeli.");
            assertTrue(idsReturnedByTheRunbookQuery().contains(paymentId),
                    "Otomatik yol kalmadigi icin satiri gorunur kilan tek sey runbook kuyrugu; orada olmali.");
        } finally {
            fixtures.cleanUp(jobId, assignmentId);
        }
    }

    @Test
    void theRunbookQueryReturnsTheRowTheSweepLeavesBehind() {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        fixtures.seedAssignment(jobId, assignmentId);
        seedStuckInitiation(paymentId, assignmentId);

        try {
            assertFalse(idsReturnedByTheRunbookQuery().contains(paymentId),
                    "Supurulmemis satir kuyrukta gorunmemeli.");

            assertTrue(paymentService.abandonStuckInitiation(paymentId));

            assertTrue(idsReturnedByTheRunbookQuery().contains(paymentId),
                    "docs/runbooks/payments.md icindeki kuyruk sorgusu supurulen satiri dondurmeli; "
                            + "dondurmezse supurme sadece kovayi degistirmis, gorunurluk kurmamis olur.");
        } finally {
            fixtures.cleanUp(jobId, assignmentId);
        }
    }

    @Test
    void theSweptRowIsTellableApartFromAnUncertainInitiationInTheDatabase() {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID swept = UUID.randomUUID();
        UUID uncertain = UUID.randomUUID();
        UUID otherJobId = UUID.randomUUID();
        UUID otherAssignmentId = UUID.randomUUID();
        fixtures.seedAssignment(jobId, assignmentId);
        fixtures.seedAssignment(otherJobId, otherAssignmentId);
        seedStuckInitiation(swept, assignmentId);
        seedStuckInitiation(uncertain, otherAssignmentId);

        try {
            assertTrue(paymentService.abandonStuckInitiation(swept));
            paymentService.markInitiationUncertain(uncertain);

            assertEquals("VERIFY_PENDING", statusOf(swept));
            assertEquals("VERIFY_PENDING", statusOf(uncertain));
            assertNull(tokenOf(swept));
            assertNull(tokenOf(uncertain));

            assertEquals("{payment.initiation.abandoned}", failureReasonOf(swept));
            assertEquals("{payment.verification.pending}", failureReasonOf(uncertain));

            List<UUID> queue = idsReturnedByTheRunbookQuery();
            assertTrue(queue.contains(swept), "Supurulen satir kuyrukta olmali: " + queue);
            assertFalse(queue.contains(uncertain),
                    "Kuyruk sorgusu yalniz supurulen satiri secmeli; sonucu bilinmeyen deneme baska bir "
                            + "triyaj dalidir ve ayni kovaya dusmemeli: " + queue);
        } finally {
            fixtures.cleanUp(jobId, assignmentId);
            fixtures.cleanUp(otherJobId, otherAssignmentId);
        }
    }

    @Test
    void aFailedAttemptDoesNotBlockTheEmployerFromTryingAgain() {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        fixtures.seedAssignment(jobId, assignmentId);
        jdbcTemplate.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id,
                                      status, currency, verification_attempts)
                values (?, now(), false, ?, ?, 'FAILED', 'TRY', 0)
                """, UUID.randomUUID(), assignmentId, UUID.randomUUID().toString());

        try {
            JobAssignment assignment = new JobAssignment();
            assignment.setId(assignmentId);
            assignment.setPricingVersion(PaymentFixtures.PRICING_VERSION);
            assignment.setCurrency("TRY");

            var retry = paymentService.startAttempt(assignment, PaymentFixtures.breakdown());

            assertEquals(PaymentAttemptStatus.INITIATED, retry.getStatus());
            assertEquals(1, jdbcTemplate.queryForObject("""
                    select count(*) from payments
                    where job_assignment_id = ? and status in ('INITIATED', 'VERIFY_PENDING', 'IN_REVIEW')
                    """, Integer.class, assignmentId));
            assertEquals(2, jdbcTemplate.queryForObject(
                    "select count(*) from payments where job_assignment_id = ?", Integer.class, assignmentId));
        } finally {
            fixtures.cleanUp(jobId, assignmentId);
        }
    }

    @Test
    void theIndexRejectsASecondActiveRowWhenTwoInsertsSkipTheAssignmentLock() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        fixtures.seedAssignment(jobId, assignmentId);
        widenTheInsertWindow();

        try {
            OverlappingWrites overlap = new OverlappingWrites();
            TransactionTemplate transactions = new TransactionTemplate(transactionManager);
            java.util.concurrent.Callable<String> insert = () -> {
                try {
                    transactions.execute(status -> {
                        JobAssignment reference = jobAssignmentDao.getReferenceById(assignmentId);
                        Payment payment = new Payment();
                        payment.setJobAssignment(reference);
                        payment.setConversationId(UUID.randomUUID().toString());
                        payment.setStatus(PaymentAttemptStatus.INITIATED);
                        payment.setCurrency("TRY");
                        overlap.meet();
                        return paymentDao.saveAndFlush(payment);
                    });
                    return "SUCCESS";
                } catch (DataIntegrityViolationException violation) {
                    return OverlappingWrites.constraintNameOf(violation);
                }
            };

            List<String> outcomes = overlap.outcomesOf(insert, insert);

            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count(),
                    "exactly one insert must win, got " + outcomes);
            assertEquals(1, outcomes.stream().filter("uk_payments_one_active_attempt"::equals).count(),
                    "the loser must be rejected by the active-attempt index, got " + outcomes);
            assertEquals(1, jdbcTemplate.queryForObject("""
                    select count(*) from payments
                    where job_assignment_id = ? and status in ('INITIATED', 'VERIFY_PENDING', 'IN_REVIEW')
                    """, Integer.class, assignmentId));
        } finally {
            narrowTheInsertWindow();
            fixtures.cleanUp(jobId, assignmentId);
        }
    }

    private void widenTheInsertWindow() {
        jdbcTemplate.execute("""
                create or replace function delay_payment_attempt_insert() returns trigger as $$
                begin
                    perform pg_sleep(0.5);
                    return new;
                end;
                $$ language plpgsql
                """);
        jdbcTemplate.execute("""
                create trigger delay_payment_attempt_insert
                before insert on payments for each row execute function delay_payment_attempt_insert()
                """);
    }

    private void narrowTheInsertWindow() {
        jdbcTemplate.execute("drop trigger if exists delay_payment_attempt_insert on payments");
        jdbcTemplate.execute("drop function if exists delay_payment_attempt_insert()");
    }

    private String activeAttemptIndexDefinition() {
        return jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = 'uk_payments_one_active_attempt'",
                String.class);
    }

    private static Set<PaymentAttemptStatus> statusesNamedIn(String indexDefinition) {
        Set<PaymentAttemptStatus> named = EnumSet.noneOf(PaymentAttemptStatus.class);
        for (PaymentAttemptStatus candidate : PaymentAttemptStatus.values()) {
            if (Pattern.compile("'" + candidate.name() + "'").matcher(indexDefinition).find()) {
                named.add(candidate);
            }
        }
        return named;
    }

    private void seedStuckInitiation(UUID paymentId, UUID assignmentId) {
        jdbcTemplate.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id,
                                      status, currency, verification_attempts)
                values (?, now() - interval '1 hour', false, ?, ?, 'INITIATED', 'TRY', 0)
                """, paymentId, assignmentId, UUID.randomUUID().toString());
    }

    private String statusOf(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "select status from payments where id = ?", String.class, paymentId);
    }

    private String tokenOf(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "select provider_token from payments where id = ?", String.class, paymentId);
    }

    private String failureReasonOf(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "select failure_reason from payments where id = ?", String.class, paymentId);
    }

    private List<UUID> idsReturnedByTheRunbookQuery() {
        return jdbcTemplate.queryForList(abandonedQueueQueryFromTheRunbook(), UUID.class);
    }

    private static String abandonedQueueQueryFromTheRunbook() {
        return RunbookSql.theOnlyBlock(RunbookSql.PAYMENTS,
                        block -> block.contains("{payment.initiation.abandoned}"), "supurulen satirlarin kuyrugunu")
                .replace("select id, job_assignment_id, conversation_id, created_at", "select id");
    }

    private static void await(CountDownLatch latch) {
        TestLatches.awaitHold(latch);
    }
}
