package com.exskylab.koala.business.concretes;

import com.exskylab.koala.support.FallbackSettings;
import com.exskylab.koala.core.constants.PaymentMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.monitoring.PaymentMetrics;
import com.exskylab.koala.core.properties.PaymentVerificationProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoPaymentResultResponseDto;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.PaymentDao;
import com.exskylab.koala.entities.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.core.events.AssignmentPaidEvent;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentCallbackTest {

    private static final String TOKEN = "iyzico-token-123";
    private static final UUID ASSIGNMENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID JOB_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID WORKER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final String CONVERSATION_ID = "conversation-123";

    private PaymentDao paymentDao;
    private IyzicoPaymentService iyzicoPaymentService;
    private JobAssignmentDao jobAssignmentDao;
    private PaymentManager paymentManager;
    private PaymentVerificationProperties verificationProperties;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        paymentDao = mock(PaymentDao.class);
        iyzicoPaymentService = mock(IyzicoPaymentService.class);
        jobAssignmentDao = mock(JobAssignmentDao.class);
        verificationProperties = new PaymentVerificationProperties();
        eventPublisher = mock(ApplicationEventPublisher.class);
        paymentManager = new PaymentManager(paymentDao, iyzicoPaymentService, new PaymentStateMachine(),
                mock(SecurityService.class), jobAssignmentDao,
                new AssignmentParticipants(mock(CompanyContactService.class)),
                new PaymentMetrics(new SimpleMeterRegistry()), verificationProperties, eventPublisher);

        when(paymentDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(paymentDao.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Payment pendingPayment() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(ASSIGNMENT_ID);
        assignment.setStatus(AssignmentStatus.CONFIRMED);
        assignment.setPaymentStatus(PaymentStatus.PENDING_COLLECTION);
        Job job = new Job();
        job.setId(JOB_ID);
        assignment.setJob(job);
        User worker = new User();
        worker.setId(WORKER_ID);
        assignment.setWorker(worker);

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setJobAssignment(assignment);
        payment.setConversationId(CONVERSATION_ID);
        payment.setProviderToken(TOKEN);
        payment.setProviderSubmerchantKey("submerchant-key");
        payment.setStatus(PaymentAttemptStatus.INITIATED);
        payment.setCurrency("TRY");
        payment.setWorkerPayout(new BigDecimal("1000.00"));
        payment.setCommissionAmount(new BigDecimal("75.00"));
        payment.setCommissionRate(new BigDecimal("0.075"));
        payment.setTotalCharged(new BigDecimal("1075.00"));
        when(paymentDao.findJobAssignmentIdByProviderToken(TOKEN)).thenReturn(Optional.of(ASSIGNMENT_ID));
        when(paymentDao.findJobAssignmentIdById(payment.getId())).thenReturn(Optional.of(ASSIGNMENT_ID));
        when(jobAssignmentDao.findByIdForUpdate(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        return payment;
    }

    private IyzicoPaymentResultResponseDto providerSuccess() {
        IyzicoPaymentResultResponseDto result = new IyzicoPaymentResultResponseDto();
        result.setStatus("success");
        result.setPaymentStatus("SUCCESS");
        result.setFraudStatus(1);
        result.setMdStatus(1);
        result.setPaymentId("pay-1");
        result.setPaymentTransactionId("txn-1");
        result.setConversationId(CONVERSATION_ID);
        result.setToken(TOKEN);
        result.setBasketId(ASSIGNMENT_ID.toString());
        result.setPrice(new BigDecimal("1075.00"));
        result.setPaidPrice(new BigDecimal("1075.00"));
        result.setItemCount(1);
        result.setItemId(JOB_ID.toString());
        result.setItemPrice(new BigDecimal("1075.00"));
        result.setItemPaidPrice(new BigDecimal("1075.00"));
        result.setItemSubMerchantPrice(new BigDecimal("1000.00"));
        result.setItemSubMerchantKey("submerchant-key");
        result.setCurrency("TRY");
        return result;
    }

    @Test
    void aSuccessfulCallbackMovesTheAssignmentIntoEscrowAndRecordsTheBreakdown() {
        Payment payment = pendingPayment();
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(providerSuccess());

        Payment settled = paymentManager.completeFromCallback(TOKEN);
        JobAssignment assignment = settled.getJobAssignment();

        assertEquals(PaymentAttemptStatus.SUCCEEDED, settled.getStatus());
        assertEquals("pay-1", settled.getProviderPaymentId());
        assertEquals("txn-1", settled.getProviderTransactionId());
        assertNotNull(settled.getCompletedAt());

        assertEquals(PaymentStatus.HELD_IN_ESCROW, assignment.getPaymentStatus());
        assertEquals(new BigDecimal("1000.00"), assignment.getFinalPayout());
        assertEquals(new BigDecimal("0.075"), assignment.getCommissionRate());
        assertEquals(new BigDecimal("1075.00"), assignment.getTotalCharged());
        verify(jobAssignmentDao).findByIdForUpdate(ASSIGNMENT_ID);
        verify(eventPublisher).publishEvent(new AssignmentPaidEvent(JOB_ID, WORKER_ID, ASSIGNMENT_ID));
    }

    @Test
    void thePaidEventIsPublishedOnlyAfterThePaymentRowIsSaved() {
        Payment payment = pendingPayment();
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(providerSuccess());

        paymentManager.completeFromCallback(TOKEN);

        InOrder order = inOrder(paymentDao, eventPublisher);
        order.verify(paymentDao).save(payment);
        order.verify(eventPublisher).publishEvent(any(AssignmentPaidEvent.class));
    }

    @Test
    void aDeclinedPaymentPublishesNoPaidEvent() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto result = providerSuccess();
        result.setPaymentStatus("FAILURE");
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(result);

        paymentManager.completeFromCallback(TOKEN);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void aMismatchedProviderConversationStaysBlockedForReconciliation() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto result = providerSuccess();
        result.setConversationId("another-attempt");
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(result);

        Payment reconciled = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, reconciled.getStatus());
        assertEquals(PaymentStatus.PENDING_COLLECTION, payment.getJobAssignment().getPaymentStatus());
    }

    @Test
    void aDueAmbiguousAttemptCanBeReconciledToSuccess() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentAttemptStatus.VERIFY_PENDING);
        payment.setVerificationAttempts(1);
        payment.setNextVerificationAt(Instant.now().minusSeconds(1));
        when(paymentDao.findWithDetailsById(payment.getId())).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(providerSuccess());

        assertTrue(paymentManager.reconcileAttempt(payment.getId()));
        assertEquals(PaymentAttemptStatus.SUCCEEDED, payment.getStatus());
        assertNull(payment.getNextVerificationAt());
    }

    @Test
    void theSameCallbackArrivingTwiceIsProcessedOnlyOnce() {
        Payment payment = pendingPayment();
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(providerSuccess());

        paymentManager.completeFromCallback(TOKEN);
        paymentManager.completeFromCallback(TOKEN);
        paymentManager.completeFromCallback(TOKEN);

        verify(iyzicoPaymentService, times(1)).retrieveCheckoutFormResult(any());
        assertEquals(PaymentStatus.HELD_IN_ESCROW, payment.getJobAssignment().getPaymentStatus());
    }

    @Test
    void aProviderVerificationErrorRemainsActiveUntilReconciled() {
        Instant before = Instant.now();
        Payment payment = pendingPayment();
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(paymentDao.existsByJobAssignmentIdAndStatusIn(eq(payment.getJobAssignment().getId()), any()))
                .thenAnswer(invocation -> invocation.<java.util.List<PaymentAttemptStatus>>getArgument(1)
                        .contains(PaymentAttemptStatus.VERIFY_PENDING));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any()))
                .thenThrow(new RuntimeException("Payment failed with status: FAILURE"));

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
        assertEquals(PaymentStatus.PENDING_COLLECTION, result.getJobAssignment().getPaymentStatus());
        assertTrue(paymentManager.hasPendingAttemptForAssignment(
                result.getJobAssignment().getId()));
        assertEquals(1, result.getVerificationAttempts());
        assertNotNull(result.getNextVerificationAt());
        assertTrue(result.getNextVerificationAt().isAfter(before));
    }

    @Test
    void automaticReconciliationStopsAfterTheConfiguredBoundButRemainsActiveForManualReview() {
        verificationProperties.setMaxAttempts(2);
        Payment payment = pendingPayment();
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any()))
                .thenThrow(new RuntimeException("provider unavailable"));

        paymentManager.completeFromCallback(TOKEN);
        paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, payment.getStatus());
        assertEquals(2, payment.getVerificationAttempts());
        assertNull(payment.getNextVerificationAt());
        assertEquals(PaymentStatus.PENDING_COLLECTION, payment.getJobAssignment().getPaymentStatus());
    }

    @Test
    void anUnknownTokenIsRejected() {
        when(paymentDao.findByProviderToken("sahte")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentManager.completeFromCallback("sahte"));
        verify(iyzicoPaymentService, never()).retrieveCheckoutFormResult(any());
    }

    @Test
    void replayingTheRecordedOutcomeNeverContactsTheProviderOrTouchesTheRow() {
        PaymentDao.RecordedOutcomeView pending = mock(PaymentDao.RecordedOutcomeView.class);
        when(pending.getStatus()).thenReturn(PaymentAttemptStatus.VERIFY_PENDING);
        when(pending.getJobAssignmentId()).thenReturn(ASSIGNMENT_ID);
        when(paymentDao.findRecordedOutcomeByProviderToken(TOKEN)).thenReturn(Optional.of(pending));

        var outcome = paymentManager.findRecordedOutcome(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, outcome.orElseThrow().status());
        assertEquals(ASSIGNMENT_ID, outcome.orElseThrow().jobAssignmentId());
        verify(iyzicoPaymentService, never()).retrieveCheckoutFormResult(any());
        verify(paymentDao, never()).findByProviderToken(any());
        verify(paymentDao, never()).save(any());
        verify(jobAssignmentDao, never()).findByIdForUpdate(any());
    }

    @Test
    void replayingAnUnknownTokenIsEmptyRatherThanAnError() {
        when(paymentDao.findRecordedOutcomeByProviderToken("sahte")).thenReturn(Optional.empty());

        assertTrue(paymentManager.findRecordedOutcome("sahte").isEmpty());
        verify(iyzicoPaymentService, never()).retrieveCheckoutFormResult(any());
    }

    @Test
    void anAmbiguousProviderErrorCannotBeRetriedAsANewPayment() {
        Payment first = pendingPayment();
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(first));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any()))
                .thenThrow(new RuntimeException("kart reddedildi"));

        paymentManager.completeFromCallback(TOKEN);
        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, first.getStatus());
        assertEquals(PaymentStatus.PENDING_COLLECTION, first.getJobAssignment().getPaymentStatus());
    }

    @Test
    void everyAttemptGetsItsOwnConversationId() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setPricingVersion("2026-09-v1");
        assignment.setCurrency("TRY");
        assignment.setStatus(AssignmentStatus.CONFIRMED);
        assignment.setPaymentStatus(PaymentStatus.PENDING_COLLECTION);
        when(jobAssignmentDao.findByIdForUpdate(assignment.getId())).thenReturn(Optional.of(assignment));
        CommissionCalculator calculator = FallbackSettings.commissionCalculator();
        var breakdown = calculator.calculate(new BigDecimal("1000.00"));

        Payment first = paymentManager.startAttempt(assignment, breakdown);
        Payment second = paymentManager.startAttempt(assignment, breakdown);

        assertNotEquals(first.getConversationId(), second.getConversationId(),
                "Her deneme kendi conversationId'sini almali; aksi halde mutabakat yapilamaz.");
        assertNotEquals(assignment.getId().toString(), first.getConversationId(),
                "conversationId assignmentId ile esitlenmemeli.");
        assertEquals("2026-09-v1", first.getPricingVersion());
        assertEquals("TRY", first.getCurrency());
    }

    @Test
    void aDeclinedPaymentIsRecordedAsFailedWithoutOpeningEscrow() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto declined = providerSuccess();
        declined.setPaymentStatus("FAILURE");
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(declined);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.FAILED, result.getStatus());
        assertEquals(PaymentStatus.FAILED, result.getJobAssignment().getPaymentStatus());
    }

    @Test
    void aTerminallyFailedAttemptCannotLaterOpenEscrow() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentAttemptStatus.FAILED);
        payment.getJobAssignment().setPaymentStatus(PaymentStatus.FAILED);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.FAILED, result.getStatus());
        assertEquals(PaymentStatus.FAILED, result.getJobAssignment().getPaymentStatus());
        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void aSuccessfulProviderResultForACancelledAssignmentRequiresManualReview() {
        Payment payment = pendingPayment();
        payment.getJobAssignment().setStatus(AssignmentStatus.CANCELLED);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(providerSuccess());

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
        assertEquals(PaymentStatus.PENDING_COLLECTION, result.getJobAssignment().getPaymentStatus());
        assertNull(result.getNextVerificationAt());
    }

    @Test
    void aDeclineForAnotherConversationCannotTerminallyFailThisAttempt() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto declined = providerSuccess();
        declined.setPaymentStatus("FAILURE");
        declined.setConversationId("another-conversation");
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(declined);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
        assertEquals(PaymentStatus.PENDING_COLLECTION, result.getJobAssignment().getPaymentStatus());
    }

    @Test
    void aPaymentRejectedByFraudFiltersFails() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto rejected = providerSuccess();
        rejected.setFraudStatus(-1);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(rejected);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.FAILED, result.getStatus());
        assertNotEquals(PaymentStatus.HELD_IN_ESCROW, result.getJobAssignment().getPaymentStatus());
    }

    @Test
    void aPaymentUnderFraudReviewDoesNotOpenEscrowYet() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto inReview = providerSuccess();
        inReview.setFraudStatus(0);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(inReview);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.IN_REVIEW, result.getStatus());
        assertEquals(PaymentStatus.PENDING_COLLECTION, result.getJobAssignment().getPaymentStatus());
        assertEquals("txn-1", result.getProviderTransactionId());
    }

    @Test
    void providerIdentifiersCannotChangeAfterFraudReview() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentAttemptStatus.IN_REVIEW);
        payment.setProviderPaymentId("pay-original");
        payment.setProviderTransactionId("txn-original");
        IyzicoPaymentResultResponseDto changed = providerSuccess();
        changed.setPaymentId("pay-different");
        changed.setPaymentTransactionId("txn-different");
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(changed);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
        assertEquals("pay-original", result.getProviderPaymentId());
        assertEquals("txn-original", result.getProviderTransactionId());
        assertEquals(PaymentStatus.PENDING_COLLECTION, result.getJobAssignment().getPaymentStatus());
    }

    @Test
    void anAbsentFraudDecisionRemainsPendingInsteadOfOpeningEscrow() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto indeterminate = providerSuccess();
        indeterminate.setFraudStatus(null);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(indeterminate);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
        assertEquals(PaymentStatus.PENDING_COLLECTION, result.getJobAssignment().getPaymentStatus());
    }

    @Test
    void anUnknownFraudDecisionRemainsPendingInsteadOfOpeningEscrow() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto indeterminate = providerSuccess();
        indeterminate.setFraudStatus(2);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(indeterminate);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
        assertEquals(PaymentStatus.PENDING_COLLECTION, result.getJobAssignment().getPaymentStatus());
    }

    @Test
    void aShortPaymentIsRefusedInsteadOfSettled() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto tampered = providerSuccess();
        tampered.setPaidPrice(new BigDecimal("1.00"));
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(tampered);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
        assertNotEquals(PaymentStatus.HELD_IN_ESCROW, result.getJobAssignment().getPaymentStatus());
    }

    @Test
    void aPaymentInAnotherCurrencyIsRefused() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto foreign = providerSuccess();
        foreign.setCurrency("USD");
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(foreign);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
    }

    @Test
    void aSuccessfulPaymentForAnotherSubmerchantNeverOpensEscrow() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto tampered = providerSuccess();
        tampered.setItemSubMerchantKey("another-submerchant");
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(tampered);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
        assertEquals(PaymentStatus.PENDING_COLLECTION, result.getJobAssignment().getPaymentStatus());
    }

    @Test
    void aPaymentWhoseThreeDSecureVerificationFailedNeverOpensEscrow() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto unverified = providerSuccess();
        unverified.setMdStatus(0);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(unverified);

        Payment result = paymentManager.completeFromCallback(TOKEN);
        JobAssignment assignment = result.getJobAssignment();

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
        assertEquals(PaymentStatus.PENDING_COLLECTION, assignment.getPaymentStatus());
        assertNotEquals(PaymentStatus.HELD_IN_ESCROW, assignment.getPaymentStatus());
        assertNull(result.getCompletedAt());
        assertNotNull(result.getNextVerificationAt());
        assertNull(assignment.getFinalPayout());
        assertNull(assignment.getTotalCharged());
        assertNull(assignment.getWorkerPayout());
        verify(jobAssignmentDao, never()).save(any());
    }

    @Test
    void theRejectionReasonIsAMessageKeySoNoProviderDetailReachesTheClient() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto unverified = providerSuccess();
        unverified.setMdStatus(0);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(unverified);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentMessages.THREE_D_SECURE_FAILED, result.getFailureReason());
        assertFalse(result.getFailureReason().contains("mdStatus"));
        assertFalse(result.getFailureReason().contains("0"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2, 3, 4, 5, 6, 7, 8})
    void everyMdStatusOtherThanOneRemainsPendingVerification(int mdStatus) {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto unverified = providerSuccess();
        unverified.setMdStatus(mdStatus);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(unverified);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
        assertNotEquals(PaymentStatus.HELD_IN_ESCROW, result.getJobAssignment().getPaymentStatus());
    }

    @Test
    void anAbsentMdStatusRemainsPendingRatherThanBeingAssumedVerified() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto silent = providerSuccess();
        silent.setMdStatus(null);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(silent);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
        assertEquals(PaymentMessages.THREE_D_SECURE_FAILED, result.getFailureReason());
        assertNotEquals(PaymentStatus.HELD_IN_ESCROW, result.getJobAssignment().getPaymentStatus());
    }

    @Test
    void aFailedVerificationOutranksTheFraudReviewHold() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto unverified = providerSuccess();
        unverified.setMdStatus(0);
        unverified.setFraudStatus(0);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(unverified);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, result.getStatus());
        assertNotEquals(PaymentAttemptStatus.IN_REVIEW, result.getStatus());
    }

    @Test
    void aSecondCallbackOnAVerificationFailureStillDoesNotOpenEscrow() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto unverified = providerSuccess();
        unverified.setMdStatus(0);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(unverified);

        paymentManager.completeFromCallback(TOKEN);
        Payment second = paymentManager.completeFromCallback(TOKEN);
        Payment third = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, third.getStatus());
        assertEquals(PaymentStatus.PENDING_COLLECTION, third.getJobAssignment().getPaymentStatus());
        assertEquals(PaymentMessages.THREE_D_SECURE_FAILED, second.getFailureReason());
        verify(iyzicoPaymentService, times(3)).retrieveCheckoutFormResult(any());
        verify(jobAssignmentDao, never()).save(any());
    }

    @Test
    void aVerifiedPaymentThatAlreadySettledIsStillShortCircuited() {
        Payment payment = pendingPayment();
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(providerSuccess());

        paymentManager.completeFromCallback(TOKEN);

        reset(iyzicoPaymentService);
        IyzicoPaymentResultResponseDto unverified = providerSuccess();
        unverified.setMdStatus(0);
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(unverified);

        Payment again = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.SUCCEEDED, again.getStatus());
        assertEquals(PaymentStatus.HELD_IN_ESCROW, again.getJobAssignment().getPaymentStatus());
        verify(iyzicoPaymentService, never()).retrieveCheckoutFormResult(any());
    }

    @Test
    void aPendingThreeDsResultCanLaterBeReconciledToSuccess() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto unverified = providerSuccess();
        unverified.setMdStatus(0);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any()))
                .thenReturn(unverified, providerSuccess());

        paymentManager.completeFromCallback(TOKEN);
        assertEquals(PaymentAttemptStatus.VERIFY_PENDING, payment.getStatus());
        assertEquals(PaymentStatus.PENDING_COLLECTION, payment.getJobAssignment().getPaymentStatus());

        paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentStatus.HELD_IN_ESCROW, payment.getJobAssignment().getPaymentStatus());
    }
}
