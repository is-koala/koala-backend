package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.properties.IyzicoProperties;
import com.exskylab.koala.core.properties.SettlementRetryProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoApprovalResponseDto;
import com.exskylab.koala.dataAccess.PayoutDao;
import com.exskylab.koala.dataAccess.SettlementTaskDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.business.abstracts.PayoutService;
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
    private IyzicoPaymentService iyzicoPaymentService;
    private IyzicoProperties iyzicoProperties;
    private SettlementRetryProperties retryProperties;
    private SettlementManager manager;

    private Payment payment;
    private JobAssignment assignment;

    @BeforeEach
    void setUp() {
        settlementTaskDao = mock(SettlementTaskDao.class);
        payoutDao = mock(PayoutDao.class);
        iyzicoPaymentService = mock(IyzicoPaymentService.class);
        iyzicoProperties = new IyzicoProperties();
        retryProperties = new SettlementRetryProperties();

        manager = new SettlementManager(settlementTaskDao, payoutDao, iyzicoPaymentService,
                iyzicoProperties, retryProperties);

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
    void aSuccessfulRunClosesTheTaskAndMovesThePayoutToProcessing() {
        SettlementTask task = pendingTask(SettlementOperation.APPROVE);
        Payout payout = new Payout();
        payout.setStatus(PayoutStatus.PENDING);
        when(payoutDao.findByJobAssignmentId(assignment.getId())).thenReturn(Optional.of(payout));
        when(iyzicoPaymentService.approvePaymentSettlement("txn-1"))
                .thenReturn(new IyzicoApprovalResponseDto("success", "txn-1"));

        assertTrue(manager.runTask(task.getId()));

        assertEquals(SettlementTaskStatus.SUCCEEDED, task.getStatus());
        assertNotNull(task.getCompletedAt());
        assertEquals(PayoutStatus.PROCESSING, payout.getStatus());
    }

    @Test
    void aFailedRunStaysPendingAndBacksOff() {
        SettlementTask task = pendingTask(SettlementOperation.APPROVE);
        when(iyzicoPaymentService.approvePaymentSettlement("txn-1"))
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
        when(iyzicoPaymentService.approvePaymentSettlement("txn-1"))
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
    void aRefundQueuesADisapprovalAndRunsIt() {
        SettlementTask task = pendingTask(SettlementOperation.DISAPPROVE);
        when(iyzicoPaymentService.disapprovePaymentSettlement("txn-1"))
                .thenReturn(new IyzicoApprovalResponseDto("success", "txn-1"));

        assertTrue(manager.runTask(task.getId()));

        verify(iyzicoPaymentService).disapprovePaymentSettlement("txn-1");
        verify(iyzicoPaymentService, never()).approvePaymentSettlement(any());
        verify(payoutDao, never()).save(any());
    }
}
