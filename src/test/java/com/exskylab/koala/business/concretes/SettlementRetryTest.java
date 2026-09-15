package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.properties.IyzicoProperties;
import com.exskylab.koala.core.properties.SettlementRetryProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoApprovalResponseDto;
import com.exskylab.koala.dataAccess.PayoutDao;
import com.exskylab.koala.dataAccess.RefundDao;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.PayoutStatus;
import com.exskylab.koala.entities.Refund;
import com.exskylab.koala.entities.RefundStatus;
import com.exskylab.koala.dataAccess.SettlementTaskDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SettlementRetryTest {

    private SettlementTaskDao settlementTaskDao;
    private PayoutDao payoutDao;
    private RefundDao refundDao;
    private IyzicoPaymentService iyzicoPaymentService;
    private IyzicoProperties iyzicoProperties;
    private SettlementRetryProperties retryProperties;
    private RefundManager refundManager;
    private SettlementManager manager;

    private Payment payment;
    private JobAssignment assignment;

    @BeforeEach
    void setUp() {
        settlementTaskDao = mock(SettlementTaskDao.class);
        payoutDao = mock(PayoutDao.class);
        refundDao = mock(RefundDao.class);
        when(refundDao.findAllByPaymentIdOrderByCreatedAtDesc(any())).thenReturn(java.util.List.of());
        iyzicoPaymentService = mock(IyzicoPaymentService.class);
        iyzicoProperties = new IyzicoProperties();
        retryProperties = new SettlementRetryProperties();
        refundManager = mock(RefundManager.class);

        manager = new SettlementManager(settlementTaskDao, payoutDao, refundDao, iyzicoPaymentService,
                iyzicoProperties, retryProperties, refundManager);

        payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setProviderTransactionId("txn-1");

        assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());

        when(settlementTaskDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(settlementTaskDao.findByPaymentIdAndOperation(any(), any())).thenReturn(Optional.empty());
        when(payoutDao.findByJobAssignmentId(any())).thenReturn(Optional.empty());
        
    }

    private SettlementTask pendingTask(SettlementOperation operation) {
        SettlementTask task = new SettlementTask();
        task.setId(UUID.randomUUID());
        task.setPayment(payment);
        task.setJobAssignment(assignment);
        task.setOperation(operation);
        task.setProviderTransactionId("txn-1");
        task.setStatus(SettlementTaskStatus.PENDING);
        task.setNextAttemptAt(Instant.now());
        when(settlementTaskDao.findWithDetailsById(task.getId())).thenReturn(Optional.of(task));
        return task;
    }

    private Refund pendingRefund() {
        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setPayment(payment);
        refund.setStatus(RefundStatus.PENDING);
        when(refundDao.findAllByPaymentIdOrderByCreatedAtDesc(payment.getId()))
                .thenReturn(java.util.List.of(refund));
        return refund;
    }

    private Payout pendingPayout() {
        Payout payout = new Payout();
        payout.setId(UUID.randomUUID());
        payout.setStatus(PayoutStatus.PENDING);
        when(payoutDao.findByJobAssignmentId(assignment.getId())).thenReturn(Optional.of(payout));
        return payout;
    }

    @Test
    void aSuccessfulDisapprovalClosesTheRefundInsteadOfLeavingItPending() {
        Refund refund = pendingRefund();
        SettlementTask task = pendingTask(SettlementOperation.REFUND);
        when(iyzicoPaymentService.refund(any())).thenReturn(refundResponse());

        manager.runTask(task.getId());

        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
        assertNotNull(refund.getCompletedAt());
    }

    @Test
    void aRefundBecomesProcessingBeforeTheProviderCall() {
        pendingRefund();
        SettlementTask task = pendingTask(SettlementOperation.REFUND);
        when(iyzicoPaymentService.refund(any())).thenReturn(refundResponse());

        manager.runTask(task.getId());

        var order = inOrder(refundManager, iyzicoPaymentService);
        order.verify(refundManager).markProcessing(payment.getId());
        order.verify(iyzicoPaymentService).refund(any());
    }

    @Test
    void givingUpOnAnApprovalMarksThePayoutFailed() {
        Payout payout = pendingPayout();
        SettlementTask task = pendingTask(SettlementOperation.APPROVE);
        task.setAttempts(retryProperties.getMaxAttempts());
        when(iyzicoPaymentService.approvePaymentSettlement(any(), any()))
                .thenThrow(new IllegalStateException("saglayici hatasi"));

        manager.runTask(task.getId());

        assertEquals(SettlementTaskStatus.FAILED, task.getStatus());
        assertEquals(PayoutStatus.FAILED, payout.getStatus());
        assertEquals(com.exskylab.koala.core.constants.PayoutMessages.SETTLEMENT_FAILED,
                payout.getFailureReason());
    }

    @Test
    void givingUpOnARefundMarksTheRefundFailed() {
        Refund refund = pendingRefund();
        SettlementTask task = pendingTask(SettlementOperation.REFUND);
        task.setAttempts(retryProperties.getMaxAttempts());
        when(iyzicoPaymentService.refund(any()))
                .thenThrow(new IllegalStateException("saglayici hatasi"));

        manager.runTask(task.getId());

        assertEquals(SettlementTaskStatus.FAILED, task.getStatus());
        assertEquals(RefundStatus.FAILED, refund.getStatus());
    }

    @Test
    void aReleaseQueuesAnApprovalInsteadOfCallingTheProviderInline() {
        var task = manager.enqueue(payment, assignment, SettlementOperation.APPROVE);

        assertTrue(task.isPresent());
        assertEquals(SettlementTaskStatus.PENDING, task.get().getStatus());
        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void queueingTwiceForTheSamePaymentReusesTheTask() {
        SettlementTask existing = pendingTask(SettlementOperation.APPROVE);
        when(settlementTaskDao.findByPaymentIdAndOperation(payment.getId(), SettlementOperation.APPROVE))
                .thenReturn(Optional.of(existing));

        assertSame(existing, manager.enqueue(payment, assignment, SettlementOperation.APPROVE).orElseThrow());
        verify(settlementTaskDao, never()).save(any());
    }

    @Test
    void nothingIsQueuedWithoutAProviderTransactionId() {
        payment.setProviderTransactionId(null);

        assertTrue(manager.enqueue(payment, assignment, SettlementOperation.APPROVE).isEmpty());
        verify(settlementTaskDao, never()).save(any());
    }

    @Test
    void nothingIsQueuedWhenSettlementIsSwitchedOff() {
        iyzicoProperties.setSettlementEnabled(false);

        assertTrue(manager.enqueue(payment, assignment, SettlementOperation.APPROVE).isEmpty());
    }

    @Test
    void aSuccessfulRunClosesTheTaskAndSettlesThePayoutFromTheProviderApproval() {
        SettlementTask task = pendingTask(SettlementOperation.APPROVE);
        Payout payout = new Payout();
        payout.setStatus(PayoutStatus.PENDING);
        when(payoutDao.findByJobAssignmentId(assignment.getId())).thenReturn(Optional.of(payout));
        when(iyzicoPaymentService.approvePaymentSettlement(eq("txn-1"), any()))
                .thenReturn(new IyzicoApprovalResponseDto("success", "txn-1"));

        assertTrue(manager.runTask(task.getId()));

        assertEquals(SettlementTaskStatus.SUCCEEDED, task.getStatus());
        assertNotNull(task.getCompletedAt());
        assertEquals(PayoutStatus.COMPLETED, payout.getStatus());
        assertEquals("txn-1", payout.getProviderReference());
        assertEquals("txn-1", payout.getSettlementReference());
        assertNotNull(payout.getProcessingAt());
        assertEquals(payout.getProcessingAt(), payout.getCompletedAt());
        assertNull(payout.getReconciledByStaffId());
    }

    @Test
    void anApprovalWithoutAResponseIdStillSettlesFromTheQueuedTransaction() {
        SettlementTask task = pendingTask(SettlementOperation.APPROVE);
        Payout payout = new Payout();
        payout.setStatus(PayoutStatus.PENDING);
        when(payoutDao.findByJobAssignmentId(assignment.getId())).thenReturn(Optional.of(payout));
        when(iyzicoPaymentService.approvePaymentSettlement(eq("txn-1"), any()))
                .thenReturn(new IyzicoApprovalResponseDto("success", null));

        assertTrue(manager.runTask(task.getId()));

        assertEquals(PayoutStatus.COMPLETED, payout.getStatus());
        assertEquals("txn-1", payout.getSettlementReference());
        assertEquals("txn-1", payout.getProviderReference());
    }

    @Test
    void aFailedRunStaysPendingAndBacksOff() {
        SettlementTask task = pendingTask(SettlementOperation.APPROVE);
        when(iyzicoPaymentService.approvePaymentSettlement(eq("txn-1"), any()))
                .thenThrow(new RuntimeException("provider down"));

        assertFalse(manager.runTask(task.getId()));

        assertEquals(SettlementTaskStatus.PENDING, task.getStatus());
        assertEquals(1, task.getAttempts());
        assertEquals("provider down", task.getLastError());
        assertTrue(task.getNextAttemptAt().isAfter(Instant.now()));
    }

    @Test
    void theTaskGivesUpAfterTheAttemptLimit() {
        retryProperties.setMaxAttempts(3);
        SettlementTask task = pendingTask(SettlementOperation.APPROVE);
        task.setAttempts(2);
        when(iyzicoPaymentService.approvePaymentSettlement(eq("txn-1"), any()))
                .thenThrow(new RuntimeException("provider down"));

        manager.runTask(task.getId());

        assertEquals(SettlementTaskStatus.FAILED, task.getStatus());
        assertEquals(3, task.getAttempts());
    }

    @Test
    void theBackoffGrowsButIsCapped() {
        retryProperties.setInitialBackoff(Duration.ofMinutes(1));
        retryProperties.setMaxBackoff(Duration.ofHours(6));

        assertEquals(Duration.ofMinutes(1), manager.backoffFor(1));
        assertEquals(Duration.ofMinutes(2), manager.backoffFor(2));
        assertEquals(Duration.ofMinutes(16), manager.backoffFor(5));
        assertEquals(Duration.ofHours(6), manager.backoffFor(20));
    }

    @Test
    void anAlreadySucceededTaskIsNotRunAgain() {
        SettlementTask task = pendingTask(SettlementOperation.APPROVE);
        task.setStatus(SettlementTaskStatus.SUCCEEDED);

        assertFalse(manager.runTask(task.getId()));
        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void aDisapprovalNeverTouchesThePayoutOrTheRefund() {
        Refund refund = pendingRefund();
        SettlementTask task = pendingTask(SettlementOperation.DISAPPROVE);
        when(iyzicoPaymentService.disapprovePaymentSettlement(eq("txn-1"), any()))
                .thenReturn(new IyzicoApprovalResponseDto("success", "txn-1"));

        assertTrue(manager.runTask(task.getId()));

        verify(iyzicoPaymentService).disapprovePaymentSettlement(eq("txn-1"), any());
        verify(iyzicoPaymentService, never()).approvePaymentSettlement(any(), any());
        verify(payoutDao, never()).save(any());
        assertEquals(RefundStatus.PENDING, refund.getStatus());
    }

    @Test
    void theRefundIsSentToTheProviderWithTheQueuedAmount() {
        Refund refund = pendingRefund();
        SettlementTask task = pendingTask(SettlementOperation.REFUND);
        task.setAmount(new java.math.BigDecimal("537.50"));
        when(iyzicoPaymentService.refund(any())).thenReturn(refundResponse());

        assertTrue(manager.runTask(task.getId()));

        verify(iyzicoPaymentService).refund(argThat(request -> {
            assertEquals(0, new java.math.BigDecimal("537.50").compareTo(request.getPrice()));
            assertEquals("txn-1", request.getPaymentTransactionId());
            return true;
        }));
        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
    }

    private static com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoRefundResponseDto refundResponse() {
        var response = new com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoRefundResponseDto();
        response.setStatus("success");
        response.setPaymentTransactionId("txn-1");
        return response;
    }
}
