package com.exskylab.koala.dataAccess;

import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.core.dtos.payout.request.ConfirmPayoutSettlementDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.PayoutStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutSettlementReconciliationIT extends AbstractIntegrationTest {

    private static final UUID WORKER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PROVIDER_REFERENCE = "iyz-transaction-4711";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PayoutService payoutService;

    @Autowired
    private PayoutDao payoutDao;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> jobs = new ArrayList<>();
    private final List<UUID> assignments = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID assignmentId : assignments) {
            jdbcTemplate.update("delete from payouts where job_assignment_id = ?", assignmentId);
            jdbcTemplate.update("delete from job_assignments where id = ?", assignmentId);
        }
        for (UUID jobId : jobs) {
            jdbcTemplate.update("delete from jobs where id = ?", jobId);
        }
        assignments.clear();
        jobs.clear();
    }

    @Test
    void theIndexGuardsExactlyTheStatusSetJavaTreatsAsAwaitingSettlement() {
        Set<PayoutStatus> guardedBySql = statusesNamedIn(awaitingSettlementIndexDefinition());

        assertEquals(EnumSet.copyOf(PayoutStatus.AWAITING_SETTLEMENT), guardedBySql,
                "PayoutStatus.AWAITING_SETTLEMENT ile idx_payouts_awaiting_settlement ayni kumeyi tutmali; "
                        + "Java'ya eklenip indekse eklenmeyen bir durum mutabakat kuyrugundan sessizce dusardi. "
                        + "Java=" + PayoutStatus.AWAITING_SETTLEMENT + " SQL=" + guardedBySql);
    }

    @Test
    void theDatabaseRefusesACompletedPayoutThatCarriesNoSettlementEvidence() {
        UUID payoutId = seedPayout("PROCESSING", Instant.now(), PROVIDER_REFERENCE);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                        "update payouts set status = 'COMPLETED', completed_at = now() where id = ?", payoutId),
                "Ekstre satiri referansi olmadan COMPLETED yazilabiliyorsa kanit kapisi yok demektir.");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                        update payouts set status = 'COMPLETED', settlement_reference = 'X-1'
                        where id = ?
                        """, payoutId),
                "completed_at olmadan COMPLETED yazilamamali.");

        UUID withoutProviderReference = seedPayout("PROCESSING", Instant.now(), null);
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                        update payouts set status = 'COMPLETED', settlement_reference = 'X-2',
                                           completed_at = now()
                        where id = ?
                        """, withoutProviderReference),
                "Saglayici referansi olmadan COMPLETED yazilamamali; eslestirilecek korelasyon anahtari yok.");

        assertEquals("PROCESSING", statusOf(payoutId));
    }

    @Test
    void matchingStatementEvidenceCompletesThePayoutInPostgres() {
        Instant releasedAt = Instant.now().minus(2, ChronoUnit.DAYS);
        UUID payoutId = seedPayout("PROCESSING", releasedAt, PROVIDER_REFERENCE);
        Instant settledAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);

        Payout settled = payoutService.confirmSettlement(payoutId, evidence("TRF-1", settledAt));

        assertEquals(PayoutStatus.COMPLETED, settled.getStatus());
        assertEquals("COMPLETED", statusOf(payoutId));
        assertEquals("TRF-1", jdbcTemplate.queryForObject(
                "select settlement_reference from payouts where id = ?", String.class, payoutId));
        assertEquals(settledAt, jdbcTemplate.queryForObject(
                "select completed_at from payouts where id = ?", Instant.class, payoutId));
    }

    @Test
    void theSameStatementLineReportedTwiceProducesOneFinancialOutcome() {
        UUID payoutId = seedPayout("PROCESSING", Instant.now().minus(2, ChronoUnit.DAYS), PROVIDER_REFERENCE);
        Instant settledAt = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);

        payoutService.confirmSettlement(payoutId, evidence("TRF-2", settledAt));

        ConfirmPayoutSettlementDto replay = evidence("TRF-2", Instant.now());
        payoutService.confirmSettlement(payoutId, replay);

        assertEquals(settledAt, jdbcTemplate.queryForObject(
                "select completed_at from payouts where id = ?", Instant.class, payoutId),
                "Ikinci bildirim completedAt'i yeniden damgalamamali.");
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from payouts where settlement_reference = 'TRF-2'", Integer.class));
    }

    @Test
    void mismatchedEvidenceLeavesThePayoutAwaitingSettlementInsteadOfFailingIt() {
        UUID payoutId = seedPayout("PROCESSING", Instant.now().minus(2, ChronoUnit.DAYS), PROVIDER_REFERENCE);

        ConfirmPayoutSettlementDto wrongAmount = evidence("TRF-3", Instant.now());
        wrongAmount.setAmount(new BigDecimal("999.99"));

        assertThrows(ValidationException.class, () -> payoutService.confirmSettlement(payoutId, wrongAmount));

        assertEquals("PROCESSING", statusOf(payoutId));
        assertNull(jdbcTemplate.queryForObject(
                "select settlement_reference from payouts where id = ?", String.class, payoutId));
        assertNull(jdbcTemplate.queryForObject(
                "select completed_at from payouts where id = ?", Instant.class, payoutId));
    }

    @Test
    void aPayoutWithoutAProviderReferenceIsEscalatedRatherThanSettled() {
        UUID payoutId = seedPayout("PROCESSING", Instant.now().minus(2, ChronoUnit.DAYS), null);

        ConflictException refused = assertThrows(ConflictException.class,
                () -> payoutService.confirmSettlement(payoutId, evidence("TRF-4", Instant.now())));

        assertEquals("PAYOUT_NO_PROVIDER_REFERENCE", refused.getErrorCode());
        assertEquals("PROCESSING", statusOf(payoutId));
    }

    @Test
    void oneStatementLineCannotSettleTwoPayoutsEvenWhenTheWritesTrulyOverlap() throws Exception {
        Instant releasedAt = Instant.now().minus(2, ChronoUnit.DAYS);
        UUID first = seedPayout("PROCESSING", releasedAt, PROVIDER_REFERENCE);
        UUID second = seedPayout("PROCESSING", releasedAt, PROVIDER_REFERENCE);
        raceSettledAt = Instant.now().minus(1, ChronoUnit.HOURS);
        raceBarrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> settleWith(first));
            var two = executor.submit(() -> settleWith(second));

            List<String> outcomes = List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));

            assertTrue(raceBarrierFailures.isEmpty(),
                    "Iki yazma ayni anda acik transaction icinde olmaliydi; ortusmediyse test 'indeks unique mi' "
                            + "seviyesine dusmus olur: " + raceBarrierFailures);
            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count(),
                    "Tek bir ekstre satiri tek bir hakedisi tamamlamali, sonuclar: " + outcomes);
            assertEquals(1, outcomes.stream().filter("uk_payouts_settlement_reference"::equals).count(),
                    "Kaybeden yazma settlement reference indeksi tarafindan reddedilmeli, sonuclar: " + outcomes);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from payouts where settlement_reference = 'TRF-RACE'", Integer.class));
        }
    }

    @Test
    void theRunbookQueueReturnsExactlyThePayoutsStuckWaitingForEvidence() {
        UUID overdue = seedPayout("PROCESSING", Instant.now().minus(10, ChronoUnit.DAYS), PROVIDER_REFERENCE);
        UUID fresh = seedPayout("PROCESSING", Instant.now(), PROVIDER_REFERENCE);
        UUID settled = seedPayout("PROCESSING", Instant.now().minus(10, ChronoUnit.DAYS), PROVIDER_REFERENCE);
        UUID failed = seedPayout("FAILED", Instant.now().minus(10, ChronoUnit.DAYS), PROVIDER_REFERENCE);

        payoutService.confirmSettlement(settled,
                evidence("TRF-5", Instant.now().minus(9, ChronoUnit.DAYS)));

        List<UUID> queue = idsReturnedByTheRunbookQuery();

        assertTrue(queue.contains(overdue),
                "Otomatik yol kalmadigi icin satiri gorunur kilan tek sey runbook kuyrugu; orada olmali: " + queue);
        assertFalse(queue.contains(fresh), "Henuz vadesi gelmemis hakedis kuyruga girmemeli: " + queue);
        assertFalse(queue.contains(settled), "Mutabakat edilmis hakedis kuyrukta kalmamali: " + queue);
        assertFalse(queue.contains(failed), "FAILED hakedis bu kuyrugun triyaj dali degil: " + queue);
    }

    @Test
    void theOverdueGaugeCountsTheSameRowsAsTheRunbookQueue() {
        UUID overdue = seedPayout("PROCESSING", Instant.now().minus(10, ChronoUnit.DAYS), PROVIDER_REFERENCE);
        seedPayout("PROCESSING", Instant.now(), PROVIDER_REFERENCE);

        long counted = payoutService.countPayoutsAwaitingSettlementLongerThan(Duration.ofDays(3));
        List<UUID> queue = idsReturnedByTheRunbookQuery();

        assertEquals(queue.size(), counted,
                "Alarmin saydigi kume ile runbook sorgusunun dondurdugu kume ayrisirsa alarm gelir ama "
                        + "operatorun kosacagi sorgu bos doner. Sayilan=" + counted + " kuyruk=" + queue);
        assertTrue(queue.contains(overdue));
    }

    private String settleWith(UUID payoutId) {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        try {
            transactions.execute(status -> {
                Payout payout = payoutDao.findById(payoutId).orElseThrow();
                payout.setStatus(PayoutStatus.COMPLETED);
                payout.setSettlementReference("TRF-RACE");
                payout.setCompletedAt(raceSettledAt);
                awaitRaceBarrier();
                return payoutDao.saveAndFlush(payout);
            });
            return "SUCCESS";
        } catch (DataIntegrityViolationException violation) {
            return constraintName(violation);
        }
    }

    private volatile Instant raceSettledAt;
    private volatile CyclicBarrier raceBarrier;
    private final List<String> raceBarrierFailures = Collections.synchronizedList(new ArrayList<>());

    private void awaitRaceBarrier() {
        try {
            raceBarrier.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException | BrokenBarrierException | TimeoutException waiting) {
            raceBarrierFailures.add(waiting.getClass().getSimpleName());
            throw new IllegalStateException("the two settlements never overlapped", waiting);
        }
    }

    private ConfirmPayoutSettlementDto evidence(String statementLine, Instant settledAt) {
        return new ConfirmPayoutSettlementDto(statementLine, PROVIDER_REFERENCE,
                new BigDecimal("1000.00"), "TRY", settledAt);
    }

    private String statusOf(UUID payoutId) {
        return jdbcTemplate.queryForObject("select status from payouts where id = ?", String.class, payoutId);
    }

    private String awaitingSettlementIndexDefinition() {
        return jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = 'idx_payouts_awaiting_settlement'",
                String.class);
    }

    private static Set<PayoutStatus> statusesNamedIn(String indexDefinition) {
        Set<PayoutStatus> named = EnumSet.noneOf(PayoutStatus.class);
        for (PayoutStatus candidate : PayoutStatus.values()) {
            if (Pattern.compile("'" + candidate.name() + "'").matcher(indexDefinition).find()) {
                named.add(candidate);
            }
        }
        return named;
    }

    private List<UUID> idsReturnedByTheRunbookQuery() {
        return jdbcTemplate.queryForList(settlementQueueQueryFromTheRunbook(), UUID.class);
    }

    private static String settlementQueueQueryFromTheRunbook() {
        String runbook;
        try {
            runbook = Files.readString(Path.of("docs/runbooks/payments.md"), StandardCharsets.UTF_8);
        } catch (java.io.IOException unreadable) {
            throw new IllegalStateException("docs/runbooks/payments.md okunamadi", unreadable);
        }
        Matcher blocks = Pattern.compile("```sql\\n(.*?)```", Pattern.DOTALL).matcher(runbook);
        List<String> matching = new ArrayList<>();
        while (blocks.find()) {
            String block = blocks.group(1);
            if (block.contains("from payouts") && block.contains("settlement_reference is null")) {
                matching.add(block);
            }
        }
        assertEquals(1, matching.size(),
                "Runbook, mutabakat bekleyen hakedislerin kuyrugunu tam olarak bir SQL blogunda tarif etmeli, "
                        + "bulunan: " + matching.size());
        return matching.getFirst().replace(
                "select id, job_assignment_id, provider_reference, amount, currency, created_at", "select id");
    }

    private static String constraintName(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            current = current.getCause();
        }
        return "no-constraint";
    }

    private UUID seedPayout(String status, Instant createdAt, String providerReference) {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();

        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, title, status, employer_type,
                                  experience_required, insurance_required, min_age, max_age,
                                  start_date_time, end_date_time)
                values (?, now(), false, 'Payout settlement', 'OPEN', 'INDIVIDUAL',
                        false, false, 18, 65, now() + interval '1 day', now() + interval '2 days')
                """, jobId);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                     pricing_version, currency,
                     worker_payout, commission_rate, commission_amount, total_charged, work_hours)
                values (?, now(), false, ?, ?, 'COMPLETED', 'RELEASED_TO_WORKER', '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00, 0)
                """, assignmentId, jobId, WORKER);
        jdbcTemplate.update("""
                insert into payouts (id, created_at, is_deleted, job_assignment_id, worker_id,
                                     amount, currency, status, provider_reference)
                values (?, ?, false, ?, ?, 1000.00, 'TRY', ?::varchar, ?)
                """, payoutId, java.sql.Timestamp.from(createdAt), assignmentId, WORKER, status,
                providerReference);

        jobs.add(jobId);
        assignments.add(assignmentId);
        return payoutId;
    }
}
