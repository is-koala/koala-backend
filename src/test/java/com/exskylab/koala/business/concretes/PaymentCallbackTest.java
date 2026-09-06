package com.exskylab.koala.business.concretes;

import com.exskylab.koala.support.FallbackSettings;
import com.exskylab.koala.core.constants.PaymentMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.monitoring.PaymentMetrics;
import com.exskylab.koala.core.properties.CommissionProperties;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentCallbackTest {

    private static final String TOKEN = "iyzico-token-123";

    private PaymentDao paymentDao;
    private IyzicoPaymentService iyzicoPaymentService;
    private JobAssignmentDao jobAssignmentDao;
    private PaymentManager paymentManager;

    @BeforeEach
    void setUp() {
        paymentDao = mock(PaymentDao.class);
        iyzicoPaymentService = mock(IyzicoPaymentService.class);
        jobAssignmentDao = mock(JobAssignmentDao.class);
        paymentManager = new PaymentManager(paymentDao, iyzicoPaymentService, new PaymentStateMachine(),
                mock(SecurityService.class), jobAssignmentDao,
                new AssignmentParticipants(mock(CompanyContactService.class)),
                new PaymentMetrics(new SimpleMeterRegistry()));

        when(paymentDao.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Payment pendingPayment() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setStatus(AssignmentStatus.CONFIRMED);
        assignment.setPaymentStatus(PaymentStatus.PENDING_COLLECTION);

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setJobAssignment(assignment);
        payment.setConversationId(UUID.randomUUID().toString());
        payment.setProviderToken(TOKEN);
        payment.setStatus(PaymentAttemptStatus.INITIATED);
        payment.setCurrency("TRY");
        payment.setWorkerPayout(new BigDecimal("1000.00"));
        payment.setCommissionAmount(new BigDecimal("75.00"));
        payment.setCommissionRate(new BigDecimal("0.075"));
        payment.setTotalCharged(new BigDecimal("1075.00"));
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
        result.setPaidPrice(new BigDecimal("1075.00"));
        result.setItemPaidPrice(new BigDecimal("1075.00"));
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
    void theCallbackBodyIsNeverTrustedOnItsOwn() {
        Payment payment = pendingPayment();
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any()))
                .thenThrow(new RuntimeException("Payment failed with status: FAILURE"));

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.FAILED, result.getStatus());
        assertEquals(PaymentStatus.FAILED, result.getJobAssignment().getPaymentStatus());
        assertTrue(result.getFailureReason().contains("FAILURE"));
    }

    @Test
    void anUnknownTokenIsRejected() {
        when(paymentDao.findByProviderToken("sahte")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentManager.completeFromCallback("sahte"));
        verify(iyzicoPaymentService, never()).retrieveCheckoutFormResult(any());
    }

    @Test
    void aFailedAttemptCanBeRetriedAndThenSucceed() {
        Payment first = pendingPayment();
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(first));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any()))
                .thenThrow(new RuntimeException("kart reddedildi"));

        paymentManager.completeFromCallback(TOKEN);
        assertEquals(PaymentStatus.FAILED, first.getJobAssignment().getPaymentStatus());

        Payment second = pendingPayment();
        second.getJobAssignment().setPaymentStatus(PaymentStatus.FAILED);
        reset(iyzicoPaymentService);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(second));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(providerSuccess());

        paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentStatus.HELD_IN_ESCROW, second.getJobAssignment().getPaymentStatus());
    }

    @Test
    void everyAttemptGetsItsOwnConversationId() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setPricingVersion("2026-09-v1");
        assignment.setCurrency("TRY");
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
    void aShortPaymentIsRefusedInsteadOfSettled() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto tampered = providerSuccess();
        tampered.setPaidPrice(new BigDecimal("1.00"));
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(tampered);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.FAILED, result.getStatus());
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

        assertEquals(PaymentAttemptStatus.FAILED, result.getStatus());
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

        assertEquals(PaymentAttemptStatus.FAILED, result.getStatus());
        assertEquals(PaymentStatus.FAILED, assignment.getPaymentStatus());
        assertNotEquals(PaymentStatus.HELD_IN_ESCROW, assignment.getPaymentStatus());
        assertNotNull(result.getCompletedAt());
        assertNull(assignment.getFinalPayout());
        assertNull(assignment.getTotalCharged());
        assertNull(assignment.getWorkerPayout());
        verifyNoInteractions(jobAssignmentDao);
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
    void everyMdStatusOtherThanOneIsRejected(int mdStatus) {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto unverified = providerSuccess();
        unverified.setMdStatus(mdStatus);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(unverified);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.FAILED, result.getStatus());
        assertNotEquals(PaymentStatus.HELD_IN_ESCROW, result.getJobAssignment().getPaymentStatus());
    }

    @Test
    void anAbsentMdStatusIsRejectedRatherThanAssumedVerified() {
        Payment payment = pendingPayment();
        IyzicoPaymentResultResponseDto silent = providerSuccess();
        silent.setMdStatus(null);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(payment));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(silent);

        Payment result = paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentAttemptStatus.FAILED, result.getStatus());
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

        assertEquals(PaymentAttemptStatus.FAILED, result.getStatus());
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

        assertEquals(PaymentAttemptStatus.FAILED, third.getStatus());
        assertEquals(PaymentStatus.FAILED, third.getJobAssignment().getPaymentStatus());
        assertEquals(PaymentMessages.THREE_D_SECURE_FAILED, second.getFailureReason());
        verify(iyzicoPaymentService, times(3)).retrieveCheckoutFormResult(any());
        verifyNoInteractions(jobAssignmentDao);
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
    void aRejectedVerificationCanBeRetriedAndThenSucceed() {
        Payment first = pendingPayment();
        IyzicoPaymentResultResponseDto unverified = providerSuccess();
        unverified.setMdStatus(0);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(first));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(unverified);

        paymentManager.completeFromCallback(TOKEN);
        assertEquals(PaymentStatus.FAILED, first.getJobAssignment().getPaymentStatus());

        Payment second = pendingPayment();
        second.getJobAssignment().setPaymentStatus(PaymentStatus.FAILED);
        reset(iyzicoPaymentService);
        when(paymentDao.findByProviderToken(TOKEN)).thenReturn(Optional.of(second));
        when(iyzicoPaymentService.retrieveCheckoutFormResult(any())).thenReturn(providerSuccess());

        paymentManager.completeFromCallback(TOKEN);

        assertEquals(PaymentStatus.HELD_IN_ESCROW, second.getJobAssignment().getPaymentStatus());
    }
}
