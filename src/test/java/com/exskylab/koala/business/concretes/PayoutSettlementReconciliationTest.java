package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.business.abstracts.SettlementService;
import com.exskylab.koala.core.dtos.payout.request.ConfirmPayoutSettlementDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.monitoring.PaymentMetrics;
import com.exskylab.koala.dataAccess.DisputeDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.PayoutDao;
import com.exskylab.koala.dataAccess.RefundDao;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.PayoutStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutSettlementReconciliationTest {

    private static final String PROVIDER_REFERENCE = "iyz-transaction-4711";
    private static final String STATEMENT_LINE = "TRF-2026-09-08-000913";

    private PayoutDao payoutDao;
    private SimpleMeterRegistry registry;
    private PayoutManager payoutManager;

    private final UUID payoutId = UUID.randomUUID();
    private final Instant releasedAt = Instant.parse("2026-09-01T09:00:00Z");
    private final Instant settledAt = Instant.parse("2026-09-03T11:30:00Z");

    @BeforeEach
    void setUp() {
        payoutDao = mock(PayoutDao.class);
        registry = new SimpleMeterRegistry();

        payoutManager = new PayoutManager(payoutDao, mock(RefundDao.class), mock(PaymentService.class),
                mock(JobAssignmentDao.class), mock(BankAccountService.class), mock(DisputeDao.class),
                new PaymentStateMachine(), mock(SettlementService.class),
                mock(ActivityNotificationService.class), new PaymentMetrics(registry),
                Validation.buildDefaultValidatorFactory().getValidator());

        when(payoutDao.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Payout payout(PayoutStatus status) {
        Payout payout = new Payout();
        payout.setId(payoutId);
        payout.setStatus(status);
        payout.setAmount(new BigDecimal("1000.00"));
        payout.setCurrency("TRY");
        payout.setProviderReference(PROVIDER_REFERENCE);
        payout.setCreatedAt(releasedAt);
        when(payoutDao.findByIdForUpdate(payoutId)).thenReturn(Optional.of(payout));
        return payout;
    }

    private ConfirmPayoutSettlementDto evidence() {
        return new ConfirmPayoutSettlementDto(STATEMENT_LINE, PROVIDER_REFERENCE,
                new BigDecimal("1000.00"), "TRY", settledAt);
    }

    private double counted(String outcome) {
        return registry.find("koala.payment.operations")
                .tags("operation", "payout_settlement", "outcome", outcome)
                .counter().count();
    }

    @Test
    void matchingStatementEvidenceCompletesThePayoutAndStampsTheTransferTime() {
        payout(PayoutStatus.PROCESSING);

        Payout settled = payoutManager.confirmSettlement(payoutId, evidence());

        assertEquals(PayoutStatus.COMPLETED, settled.getStatus());
        assertEquals(STATEMENT_LINE, settled.getSettlementReference());
        assertEquals(settledAt, settled.getCompletedAt());
        assertEquals(1.0, counted("settled"));
    }

    @Test
    void theSameStatementLineTwiceLeavesTheFirstFinancialOutcomeUntouched() {
        Payout completed = payout(PayoutStatus.COMPLETED);
        completed.setSettlementReference(STATEMENT_LINE);
        completed.setCompletedAt(settledAt);

        ConfirmPayoutSettlementDto replay = evidence();
        replay.setSettledAt(settledAt.plus(2, ChronoUnit.DAYS));

        Payout again = payoutManager.confirmSettlement(payoutId, replay);

        assertSame(completed, again);
        assertEquals(settledAt, again.getCompletedAt(),
                "Tekrar gelen ayni mutabakat completedAt'i yeniden damgalayamaz; tek finansal sonuc olmali.");
        assertEquals(1.0, counted("already_settled"));
        verify(payoutDao, never()).saveAndFlush(any());
    }

    @Test
    void aSecondDifferentStatementLineOnACompletedPayoutIsAConflictNotIdempotency() {
        Payout completed = payout(PayoutStatus.COMPLETED);
        completed.setSettlementReference(STATEMENT_LINE);
        completed.setCompletedAt(settledAt);

        ConfirmPayoutSettlementDto other = evidence();
        other.setSettlementReference("TRF-2026-09-08-000914");

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> payoutManager.confirmSettlement(payoutId, other));

        assertEquals("PAYOUT_SETTLEMENT_REFERENCE_CONFLICT", conflict.getErrorCode());
        verify(payoutDao, never()).saveAndFlush(any());
    }

    @Test
    void aPayoutThatTheProviderHasNotAcceptedYetCannotBeSettled() {
        payout(PayoutStatus.PENDING);

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> payoutManager.confirmSettlement(payoutId, evidence()));

        assertEquals("PAYOUT_NOT_AWAITING_SETTLEMENT", conflict.getErrorCode());
        verify(payoutDao, never()).saveAndFlush(any());
    }

    @Test
    void aFailedPayoutIsNotResurrectedByAStatementLine() {
        payout(PayoutStatus.FAILED);

        assertThrows(ConflictException.class, () -> payoutManager.confirmSettlement(payoutId, evidence()));
        verify(payoutDao, never()).saveAndFlush(any());
    }

    @Test
    void withoutAProviderReferenceThereIsNothingToMatchTheStatementLineAgainst() {
        Payout payout = payout(PayoutStatus.PROCESSING);
        payout.setProviderReference(null);

        ConflictException conflict = assertThrows(ConflictException.class,
                () -> payoutManager.confirmSettlement(payoutId, evidence()));

        assertEquals("PAYOUT_NO_PROVIDER_REFERENCE", conflict.getErrorCode());
        assertEquals(PayoutStatus.PROCESSING, payout.getStatus());
    }

    @Test
    void aMismatchedProviderReferenceLeavesThePayoutProcessingInsteadOfFailingIt() {
        Payout payout = payout(PayoutStatus.PROCESSING);
        ConfirmPayoutSettlementDto wrong = evidence();
        wrong.setProviderReference("iyz-transaction-9999");

        ValidationException rejected = assertThrows(ValidationException.class,
                () -> payoutManager.confirmSettlement(payoutId, wrong));

        assertEquals("PAYOUT_SETTLEMENT_EVIDENCE_MISMATCH", rejected.getErrorCode());
        assertEquals(PayoutStatus.PROCESSING, payout.getStatus());
        assertNull(payout.getCompletedAt());
        assertNull(payout.getSettlementReference());
        assertEquals(1.0, counted("evidence_mismatch"));
        verify(payoutDao, never()).saveAndFlush(any());
    }

    @Test
    void aMismatchedAmountLeavesThePayoutProcessing() {
        Payout payout = payout(PayoutStatus.PROCESSING);
        ConfirmPayoutSettlementDto short_ = evidence();
        short_.setAmount(new BigDecimal("999.99"));

        assertThrows(ValidationException.class, () -> payoutManager.confirmSettlement(payoutId, short_));

        assertEquals(PayoutStatus.PROCESSING, payout.getStatus());
        assertEquals(1.0, counted("evidence_mismatch"));
    }

    @Test
    void theSameAmountWrittenWithADifferentScaleStillMatches() {
        payout(PayoutStatus.PROCESSING);
        ConfirmPayoutSettlementDto sameMoney = evidence();
        sameMoney.setAmount(new BigDecimal("1000.0"));

        Payout settled = payoutManager.confirmSettlement(payoutId, sameMoney);

        assertEquals(PayoutStatus.COMPLETED, settled.getStatus(),
                "Para karsilastirmasi compareTo ile yapilmali; 1000.0 ile 1000.00 ayni tutardir.");
    }

    @Test
    void aMismatchedCurrencyLeavesThePayoutProcessing() {
        Payout payout = payout(PayoutStatus.PROCESSING);
        ConfirmPayoutSettlementDto foreign = evidence();
        foreign.setCurrency("EUR");

        assertThrows(ValidationException.class, () -> payoutManager.confirmSettlement(payoutId, foreign));

        assertEquals(PayoutStatus.PROCESSING, payout.getStatus());
    }

    @Test
    void theCurrencyComparisonIsLocaleSafe() {
        payout(PayoutStatus.PROCESSING);
        ConfirmPayoutSettlementDto lowercase = evidence();
        lowercase.setCurrency("try");

        Payout settled = payoutManager.confirmSettlement(payoutId, lowercase);

        assertEquals(PayoutStatus.COMPLETED, settled.getStatus());
    }

    @Test
    void aTransferDatedBeforeTheReleaseCannotBeThisPayoutsTransfer() {
        Payout payout = payout(PayoutStatus.PROCESSING);
        ConfirmPayoutSettlementDto tooEarly = evidence();
        tooEarly.setSettledAt(releasedAt.minus(1, ChronoUnit.HOURS));

        assertThrows(ValidationException.class, () -> payoutManager.confirmSettlement(payoutId, tooEarly));

        assertEquals(PayoutStatus.PROCESSING, payout.getStatus());
    }

    @Test
    void anUnknownPayoutIsNotFound() {
        when(payoutDao.findByIdForUpdate(payoutId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> payoutManager.confirmSettlement(payoutId, evidence()));
    }

    @Test
    void theOverdueQueryAsksOnlyForTheStatusesJavaTreatsAsAwaitingSettlement() {
        List<UUID> stuck = List.of(payoutId);
        when(payoutDao.findIdsAwaitingSettlementSince(any(), any())).thenReturn(stuck);

        assertEquals(stuck, payoutManager.findPayoutsAwaitingSettlementLongerThan(
                java.time.Duration.ofDays(3)));

        verify(payoutDao).findIdsAwaitingSettlementSince(
                org.mockito.ArgumentMatchers.eq(PayoutStatus.AWAITING_SETTLEMENT),
                org.mockito.ArgumentMatchers.any(Instant.class));
        assertNotNull(PayoutStatus.AWAITING_SETTLEMENT);
    }

    @Test
    void aCallerThatNeverPassedThroughHttpStillCannotBackdateTheFutureOntoCompletedAt() {
        Payout payout = payout(PayoutStatus.PROCESSING);
        ConfirmPayoutSettlementDto notYetHappened = evidence();
        notYetHappened.setSettledAt(Instant.now().plus(1, ChronoUnit.DAYS));

        ValidationException refused = assertThrows(ValidationException.class,
                () -> payoutManager.confirmSettlement(payoutId, notYetHappened));

        assertEquals("PAYOUT_SETTLED_AT_REQUIRED", refused.getErrorCode(),
                "Kural yalnizca controller'daki @Valid ile isliyorsa, tasarlandigi soylenen otomatik ayak "
                        + "gelecek tarihli bir transferi completed_at'e yazdirir.");
        assertEquals(PayoutStatus.PROCESSING, payout.getStatus());
        assertNull(payout.getCompletedAt());
        verify(payoutDao, never()).saveAndFlush(any());
        verify(payoutDao, never()).findByIdForUpdate(any());
    }

    @Test
    void aCallerThatNeverPassedThroughHttpGetsAValidationExceptionInsteadOfANullPointer() {
        payout(PayoutStatus.PROCESSING);

        assertEquals("PAYOUT_SETTLEMENT_REFERENCE_REQUIRED", assertThrows(ValidationException.class,
                () -> payoutManager.confirmSettlement(payoutId,
                        new ConfirmPayoutSettlementDto(null, PROVIDER_REFERENCE,
                                new BigDecimal("1000.00"), "TRY", settledAt))).getErrorCode());
        assertEquals("PAYOUT_PROVIDER_REFERENCE_REQUIRED", assertThrows(ValidationException.class,
                () -> payoutManager.confirmSettlement(payoutId,
                        new ConfirmPayoutSettlementDto(STATEMENT_LINE, null,
                                new BigDecimal("1000.00"), "TRY", settledAt))).getErrorCode());
        assertEquals("PAYOUT_SETTLED_AMOUNT_REQUIRED", assertThrows(ValidationException.class,
                () -> payoutManager.confirmSettlement(payoutId,
                        new ConfirmPayoutSettlementDto(STATEMENT_LINE, PROVIDER_REFERENCE,
                                null, "TRY", settledAt))).getErrorCode());
        assertEquals("PAYOUT_SETTLED_CURRENCY_REQUIRED", assertThrows(ValidationException.class,
                () -> payoutManager.confirmSettlement(payoutId,
                        new ConfirmPayoutSettlementDto(STATEMENT_LINE, PROVIDER_REFERENCE,
                                new BigDecimal("1000.00"), null, settledAt))).getErrorCode());
        assertEquals("PAYOUT_SETTLED_AT_REQUIRED", assertThrows(ValidationException.class,
                () -> payoutManager.confirmSettlement(payoutId,
                        new ConfirmPayoutSettlementDto(STATEMENT_LINE, PROVIDER_REFERENCE,
                                new BigDecimal("1000.00"), "TRY", null))).getErrorCode());

        verify(payoutDao, never()).saveAndFlush(any());
    }

    @Test
    void aCallerThatNeverPassedThroughHttpCannotReportSubKurusMoney() {
        payout(PayoutStatus.PROCESSING);
        ConfirmPayoutSettlementDto tooPrecise = evidence();
        tooPrecise.setAmount(new BigDecimal("1000.005"));

        assertEquals("PAYOUT_SETTLED_AMOUNT_REQUIRED", assertThrows(ValidationException.class,
                () -> payoutManager.confirmSettlement(payoutId, tooPrecise)).getErrorCode());
    }
}
