package com.exskylab.koala.dataAccess;

import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.core.dtos.payout.request.ConfirmPayoutSettlementDto;
import com.exskylab.koala.core.dtos.payout.response.StaffPayoutDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.PayoutStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.support.OverlappingWrites;
import com.exskylab.koala.support.PaymentFixtures;
import com.exskylab.koala.support.RunbookSql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutSettlementReconciliationIT extends AbstractIntegrationTest {

    private static final UUID WORKER = PaymentFixtures.SEEDED_WORKER;
    private static final String PROVIDER_REFERENCE = "iyz-transaction-4711";
    private static final UUID RECONCILER = null;

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
    private final List<UUID> workers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        PaymentFixtures fixtures = new PaymentFixtures(jdbcTemplate);
        for (int seeded = 0; seeded < assignments.size(); seeded++) {
            fixtures.cleanUp(jobs.get(seeded), assignments.get(seeded));
        }
        for (UUID workerId : workers) {
            jdbcTemplate.update("delete from users where id = ?", workerId);
        }
        assignments.clear();
        jobs.clear();
        workers.clear();
    }

    @Test
    void everyPlaceThatNamesTheAwaitingSettlementSetNamesTheSameSet() {
        Set<PayoutStatus> java = EnumSet.copyOf(PayoutStatus.AWAITING_SETTLEMENT);
        Set<PayoutStatus> index = statusesNamedIn(awaitingSettlementIndexDefinition());
        Set<PayoutStatus> runbook = statusesNamedIn(runbookSettlementQueueBlock());
        Set<PayoutStatus> processingClockCheck = statusesNamedIn(
                constraintDefinition("chk_payouts_processing_requires_clock"));

        assertEquals(java, index,
                "PayoutStatus.AWAITING_SETTLEMENT ile idx_payouts_awaiting_settlement ayni kumeyi tutmali; "
                        + "Java'ya eklenip indekse eklenmeyen bir durum mutabakat kuyrugundan sessizce dusardi. "
                        + "Java=" + java + " indeks=" + index);
        assertEquals(java, runbook,
                "docs/runbooks/payments.md 4. bolum 7. adimdaki sorgu ayni kumeyi tutmali; ayrisirsa gosterge "
                        + "yeni satirlari sayar, alarm yanar ve operatorun kosacagi sorgu bos doner. "
                        + "Java=" + java + " runbook=" + runbook);
        assertEquals(java, processingClockCheck,
                "chk_payouts_processing_requires_clock ayni kumeyi tutmali; tutmazsa beklemeyi baslatan damgasi "
                        + "olmayan bir durum kuyruga gorunmez girer. Java=" + java
                        + " kisit=" + processingClockCheck);
    }

    @Test
    void theEvidenceCheckNamesOnlyTheStatusThatCarriesAStatementLine() {
        assertEquals(EnumSet.of(PayoutStatus.COMPLETED),
                statusesNamedIn(constraintDefinition("chk_payouts_completed_requires_evidence")),
                "Kanit kapisi yalnizca COMPLETED'i baglamali; baska bir durum eklenirse kanitsiz "
                        + "terminal satir yazilabilir hale gelir.");
    }

    @Test
    void aPayoutCannotEnterProcessingWithoutTheClockThatStartsTheWait() {
        UUID payoutId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        seedAssignment(jobId, assignmentId, WORKER);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                        insert into payouts (id, created_at, is_deleted, job_assignment_id, worker_id,
                                             amount, currency, status, provider_reference)
                        values (?, now(), false, ?, ?, 1000.00, 'TRY', 'PROCESSING', ?)
                        """, payoutId, assignmentId, WORKER, PROVIDER_REFERENCE),
                "processing_at damgasi olmadan PROCESSING yazilabiliyorsa gecikme saati eksik baslar ve "
                        + "satir kuyruga hic girmez.");
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

        Payout settled = payoutService.confirmSettlement(payoutId, evidence("TRF-1", settledAt), RECONCILER);

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

        payoutService.confirmSettlement(payoutId, evidence("TRF-2", settledAt), RECONCILER);

        ConfirmPayoutSettlementDto replay = evidence("TRF-2", Instant.now());
        payoutService.confirmSettlement(payoutId, replay, RECONCILER);

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

        assertThrows(ValidationException.class, () -> payoutService.confirmSettlement(payoutId, wrongAmount, RECONCILER));

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
                () -> payoutService.confirmSettlement(payoutId, evidence("TRF-4", Instant.now()), RECONCILER));

        assertEquals("PAYOUT_NO_PROVIDER_REFERENCE", refused.getErrorCode());
        assertEquals("PROCESSING", statusOf(payoutId));
    }

    @Test
    void oneStatementLineCannotSettleTwoPayoutsEvenWhenTheWritesTrulyOverlap() throws Exception {
        Instant releasedAt = Instant.now().minus(2, ChronoUnit.DAYS);
        UUID first = seedPayout("PROCESSING", releasedAt, PROVIDER_REFERENCE);
        UUID second = seedPayout("PROCESSING", releasedAt, PROVIDER_REFERENCE);
        raceSettledAt = Instant.now().minus(1, ChronoUnit.HOURS);
        OverlappingWrites overlap = new OverlappingWrites();

        List<String> outcomes = overlap.outcomesOf(
                () -> settleThroughTheService(first, overlap),
                () -> settleThroughTheService(second, overlap));

        assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count(),
                "Tek bir ekstre satiri tek bir hakedisi tamamlamali, sonuclar: " + outcomes);
        assertEquals(1, outcomes.stream().filter("PAYOUT_SETTLEMENT_REFERENCE_TAKEN"::equals).count(),
                "Kaybeden yazmayi indeks reddediyor, ama kullanicinin gordugu cevabi confirmSettlement'in "
                        + "eslemesi uretir; o esleme kosmazsa 500 doner. Sonuclar: " + outcomes);
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from payouts where settlement_reference = 'TRF-RACE'", Integer.class));
    }

    @Test
    void aSoftDeletedPayoutDoesNotReleaseItsStatementLineForASecondPayout() {
        Instant releasedAt = Instant.now().minus(2, ChronoUnit.DAYS);
        UUID retired = seedPayout("PROCESSING", releasedAt, PROVIDER_REFERENCE);
        UUID second = seedPayout("PROCESSING", releasedAt, PROVIDER_REFERENCE);

        payoutService.confirmSettlement(retired, evidence("TRF-SOFT", Instant.now().minus(1, ChronoUnit.HOURS)), RECONCILER);
        jdbcTemplate.update("update payouts set is_deleted = true, deleted_at = now() where id = ?", retired);

        ConflictException taken = assertThrows(ConflictException.class,
                () -> payoutService.confirmSettlement(second, evidence("TRF-SOFT", Instant.now().minus(1, ChronoUnit.HOURS)), RECONCILER));

        assertEquals("PAYOUT_SETTLEMENT_REFERENCE_TAKEN", taken.getErrorCode(),
                "Ayni banka gonderim satiri iki hakedisi kapatamaz; soft delete parayi geri getirmez, "
                        + "sadece satiri gizler.");
        assertEquals("PROCESSING", statusOf(second));
    }

    @Test
    void theRunbookQueueReturnsExactlyThePayoutsStuckWaitingForEvidence() {
        UUID overdue = seedPayout("PROCESSING", Instant.now().minus(10, ChronoUnit.DAYS), PROVIDER_REFERENCE);
        UUID fresh = seedPayout("PROCESSING", Instant.now(), PROVIDER_REFERENCE);
        UUID settled = seedPayout("PROCESSING", Instant.now().minus(10, ChronoUnit.DAYS), PROVIDER_REFERENCE);
        UUID failed = seedPayout("FAILED", Instant.now().minus(10, ChronoUnit.DAYS), PROVIDER_REFERENCE);

        payoutService.confirmSettlement(settled, evidence("TRF-5", Instant.now().minus(9, ChronoUnit.DAYS)), RECONCILER);

        List<UUID> queue = idsReturnedByTheRunbookQuery();

        assertTrue(queue.contains(overdue),
                "Otomatik yol kalmadigi icin satiri gorunur kilan tek sey runbook kuyrugu; orada olmali: " + queue);
        assertFalse(queue.contains(fresh), "Henuz vadesi gelmemis hakedis kuyruga girmemeli: " + queue);
        assertFalse(queue.contains(settled), "Mutabakat edilmis hakedis kuyrukta kalmamali: " + queue);
        assertFalse(queue.contains(failed), "FAILED hakedis bu kuyrugun triyaj dali degil: " + queue);
    }

    @Test
    void theOverdueGaugeSelectsTheSameRowsAsTheRunbookQueue() {
        UUID overdue = seedPayout("PROCESSING", Instant.now().minus(10, ChronoUnit.DAYS), PROVIDER_REFERENCE);
        UUID alsoOverdue = seedPayout("PROCESSING", Instant.now().minus(4, ChronoUnit.DAYS), PROVIDER_REFERENCE);
        seedPayout("PROCESSING", Instant.now(), PROVIDER_REFERENCE);
        UUID reconciled = seedPayout("PROCESSING", Instant.now().minus(9, ChronoUnit.DAYS), PROVIDER_REFERENCE);
        payoutService.confirmSettlement(reconciled, evidence("TRF-GAUGE", Instant.now().minus(8, ChronoUnit.DAYS)), RECONCILER);
        UUID carryingAStatementLine = seedPayout("PROCESSING", Instant.now().minus(9, ChronoUnit.DAYS),
                PROVIDER_REFERENCE);
        jdbcTemplate.update("update payouts set settlement_reference = 'TRF-GAUGE-STUCK' where id = ?",
                carryingAStatementLine);

        Set<UUID> gauged = Set.copyOf(payoutService.findPayoutsAwaitingSettlementLongerThan(Duration.ofDays(3)));
        Set<UUID> queue = Set.copyOf(idsReturnedByTheRunbookQuery());

        assertEquals(queue, gauged,
                "Alarmin saydigi kume ile runbook sorgusunun dondurdugu kume ayni satirlar olmali; iki sorgu "
                        + "farkli satirlari ayni sayida dondurse de alarm gelir ve operatorun kosacagi sorgu "
                        + "baska bir sey gosterir. Gosterge=" + gauged + " kuyruk=" + queue);
        assertTrue(gauged.containsAll(List.of(overdue, alsoOverdue)));
        assertFalse(gauged.contains(reconciled),
                "Mutabakat edilmis satir gostergede kalmamali.");
        assertFalse(gauged.contains(carryingAStatementLine),
                "Ekstre satirini tasiyan bir PROCESSING satiri bugun confirmSettlement uzerinden olusmuyor, ama "
                        + "gosterge sorgusu runbook sorgusuyla ayni sozlesmeyi tasimali: settlement_reference is "
                        + "null iki tarafta da yazili olmali, yoksa ikinci bir yazma yolu geldigi gun ayrisirlar.");
    }

    @Test
    void theStaffQueueListsEveryRowTheGaugeAndTheRunbookCountEvenWhenItsNeighboursAreGone() {
        UUID overdue = seedPayout("PROCESSING", Instant.now().minus(10, ChronoUnit.DAYS), PROVIDER_REFERENCE);
        UUID departedWorker = seedWorker();
        UUID orphaned = seedPayout("PROCESSING", Instant.now().minus(4, ChronoUnit.DAYS), PROVIDER_REFERENCE,
                departedWorker);
        jdbcTemplate.update("update users set is_deleted = true, deleted_at = now() where id = ?", departedWorker);
        UUID withoutAssignment = seedPayout("PROCESSING", Instant.now().minus(5, ChronoUnit.DAYS),
                PROVIDER_REFERENCE);
        jdbcTemplate.update("update job_assignments set is_deleted = true, deleted_at = now() "
                + "where id = (select job_assignment_id from payouts where id = ?)", withoutAssignment);
        Set<UUID> seeded = Set.of(overdue, orphaned, withoutAssignment);

        Set<UUID> listed = onlySeeded(payoutService
                .listForStaff(List.of(PayoutStatus.PROCESSING), null, PageRequest.of(0, 50))
                .content().stream()
                .map(StaffPayoutDto::id)
                .toList(), seeded);
        Set<UUID> queue = onlySeeded(idsReturnedByTheRunbookQuery(), seeded);
        Set<UUID> gauged = onlySeeded(payoutService.findPayoutsAwaitingSettlementLongerThan(Duration.ofDays(3)),
                seeded);

        assertEquals(seeded, queue, "runbook kuyrugu iki satiri da gostermeli");
        assertEquals(seeded, gauged, "gosterge iki satiri da saymali");
        assertEquals(queue, listed,
                "Personel kuyrugu, alarmin saydigi ve runbook sorgusunun dondurdugu satirlarin aynisini "
                        + "gostermeli; hesabini silen iscinin hakedisi bankaya cikmis parayi beklemeye devam eder "
                        + "ve kuyruktan dusemez. kuyruk=" + queue + " liste=" + listed);
        assertTrue(listed.contains(orphaned), "isci soft-delete edilince satir listeden kayboldu: " + listed);
        assertTrue(listed.contains(withoutAssignment),
                "atama soft-delete edilince satir listeden kayboldu: " + listed);
    }

    @Test
    void theSecondTierSelectsOnlyTheRowsTheFirstEscalationDidNotClose() {
        UUID stalled = seedPayout("PROCESSING", Instant.now().minus(20, ChronoUnit.DAYS), PROVIDER_REFERENCE);
        UUID onlyOverdue = seedPayout("PROCESSING", Instant.now().minus(4, ChronoUnit.DAYS), PROVIDER_REFERENCE);

        List<UUID> firstTier = payoutService.findPayoutsAwaitingSettlementLongerThan(Duration.ofDays(3));
        List<UUID> secondTier = payoutService.findPayoutsAwaitingSettlementLongerThan(Duration.ofDays(10));

        assertTrue(firstTier.containsAll(List.of(stalled, onlyOverdue)));
        assertTrue(secondTier.contains(stalled));
        assertFalse(secondTier.contains(onlyOverdue),
                "Ikinci kademe ilk kademeyle ayni satirlari gosterirse yukseltme bir sey soylemiyor demektir.");
    }

    private String settleThroughTheService(UUID payoutId, OverlappingWrites overlap) {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        try {
            transactions.execute(status -> {
                payoutDao.findByIdForUpdate(payoutId).orElseThrow();
                overlap.meet();
                return payoutService.confirmSettlement(payoutId, evidence("TRF-RACE", raceSettledAt), RECONCILER);
            });
            return "SUCCESS";
        } catch (ConflictException conflict) {
            return conflict.getErrorCode();
        }
    }

    private volatile Instant raceSettledAt;

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

    private String constraintDefinition(String constraintName) {
        return jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = ?",
                String.class, constraintName);
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
        return runbookSettlementQueueBlock().replace(
                "select id, job_assignment_id, provider_reference, amount, currency, processing_at",
                "select id");
    }

    private static String runbookSettlementQueueBlock() {
        return RunbookSql.theOnlyBlock(RunbookSql.PAYMENTS,
                block -> block.contains("from payouts") && block.contains("settlement_reference is null"),
                "mutabakat bekleyen hakedislerin kuyrugunu");
    }

    private static Set<UUID> onlySeeded(List<UUID> ids, Set<UUID> seeded) {
        Set<UUID> retained = new HashSet<>(ids);
        retained.retainAll(seeded);
        return retained;
    }

    private UUID seedWorker() {
        UUID workerId = UUID.randomUUID();
        jdbcTemplate.update("insert into users (id, created_at, is_deleted, email, password, first_name, last_name, "
                        + "is_employer, email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', 'Giden', 'Isci', false, true, false, true)",
                workerId, workerId + "@example.com");
        workers.add(workerId);
        return workerId;
    }

    private UUID seedPayout(String status, Instant processingAt, String providerReference) {
        return seedPayout(status, processingAt, providerReference, WORKER);
    }

    private UUID seedPayout(String status, Instant processingAt, String providerReference, UUID workerId) {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        seedAssignment(jobId, assignmentId, workerId);

        jdbcTemplate.update("""
                insert into payouts (id, created_at, is_deleted, job_assignment_id, worker_id,
                                     amount, currency, status, provider_reference, processing_at)
                values (?, ?, false, ?, ?, 1000.00, 'TRY', ?::varchar, ?, ?)
                """, payoutId, java.sql.Timestamp.from(processingAt), assignmentId, workerId, status,
                providerReference,
                "PROCESSING".equals(status) ? java.sql.Timestamp.from(processingAt) : null);

        return payoutId;
    }

    private void seedAssignment(UUID jobId, UUID assignmentId, UUID workerId) {
        new PaymentFixtures(jdbcTemplate)
                .seedAssignment(jobId, assignmentId, workerId, "COMPLETED", "RELEASED_TO_WORKER");
        jobs.add(jobId);
        assignments.add(assignmentId);
    }
}
