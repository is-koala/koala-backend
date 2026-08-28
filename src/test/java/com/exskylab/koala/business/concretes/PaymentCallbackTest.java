package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.properties.CommissionProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoPaymentResultResponseDto;
import com.exskylab.koala.dataAccess.PaymentDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private PaymentManager paymentManager;

    @BeforeEach
    void setUp() {
        paymentDao = mock(PaymentDao.class);
        iyzicoPaymentService = mock(IyzicoPaymentService.class);
        paymentManager = new PaymentManager(paymentDao, iyzicoPaymentService, new PaymentStateMachine());

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
        CommissionCalculator calculator = new CommissionCalculator(new CommissionProperties());
        var breakdown = calculator.calculate(new BigDecimal("1000.00"));

        Payment first = paymentManager.startAttempt(assignment, breakdown);
        Payment second = paymentManager.startAttempt(assignment, breakdown);

        assertNotEquals(first.getConversationId(), second.getConversationId(),
                "Her deneme kendi conversationId'sini almali; aksi halde mutabakat yapilamaz.");
        assertNotEquals(assignment.getId().toString(), first.getConversationId(),
                "conversationId assignmentId ile esitlenmemeli.");
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
}
