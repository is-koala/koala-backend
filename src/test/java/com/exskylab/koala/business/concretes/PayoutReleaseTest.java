package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.business.abstracts.SettlementService;
import com.exskylab.koala.dataAccess.BankAccountDao;
import com.exskylab.koala.dataAccess.DisputeDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.PaymentDao;
import com.exskylab.koala.dataAccess.PayoutDao;
import com.exskylab.koala.dataAccess.RefundDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.PaymentService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PayoutReleaseTest {

    private PayoutDao payoutDao;
    private RefundDao refundDao;
    private PaymentService paymentService;
    private JobAssignmentDao jobAssignmentDao;
    private DisputeDao disputeDao;
    private SettlementService settlementService;
    private BankAccountService bankAccountService;
    private PayoutManager payoutManager;

    private final UUID assignmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        payoutDao = mock(PayoutDao.class);
        refundDao = mock(RefundDao.class);
        paymentService = mock(PaymentService.class);
        jobAssignmentDao = mock(JobAssignmentDao.class);
        bankAccountService = mock(BankAccountService.class);
        disputeDao = mock(DisputeDao.class);
        settlementService = mock(SettlementService.class);

        payoutManager = new PayoutManager(payoutDao, refundDao, paymentService, jobAssignmentDao,
                bankAccountService, disputeDao, new PaymentStateMachine(), settlementService, mock(com.exskylab.koala.business.abstracts.ActivityNotificationService.class));

        when(payoutDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(refundDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(settlementService.enqueue(any(), any(), any())).thenReturn(Optional.of(new SettlementTask()));
        when(settlementService.enqueueRefund(any(), any(), any())).thenReturn(Optional.of(new SettlementTask()));
        when(bankAccountService.findVerifiedPrimaryAccount(any()))
                .thenAnswer(invocation -> Optional.of(verifiedAccount()));
    }

    private BankAccount verifiedAccount() {
        BankAccount account = new BankAccount();
        account.setId(UUID.randomUUID());
        account.setIban("TR180006200119000006672315");
        account.setPrimaryAccount(true);
        account.setActive(true);
        account.setVerificationStatus(BankAccountVerificationStatus.VERIFIED);
        return account;
    }

    private JobAssignment assignment(AssignmentStatus status, PaymentStatus paymentStatus) {
        User worker = new User();
        worker.setId(UUID.randomUUID());

        JobAssignment assignment = new JobAssignment();
        assignment.setId(assignmentId);
        assignment.setWorker(worker);
        assignment.setStatus(status);
        assignment.setPaymentStatus(paymentStatus);
        assignment.setWorkerPayout(new BigDecimal("1000.00"));
        assignment.setTotalCharged(new BigDecimal("1075.00"));

        when(jobAssignmentDao.findByIdForUpdate(assignmentId)).thenReturn(Optional.of(assignment));
        return assignment;
    }

    private Payment settledPaymentExists() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setStatus(PaymentAttemptStatus.SUCCEEDED);
        payment.setTotalCharged(new BigDecimal("1075.00"));
        when(paymentService.getSettledPaymentFor(assignmentId)).thenReturn(payment);
        return payment;
    }

    private Payment settledPaymentFor(JobAssignment assignment) {
        return settledPaymentExists();
    }

    @Test
    void anActiveDisputeFreezesAnEmployerRefund() {
        JobAssignment assignment = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);
        settledPaymentFor(assignment);
        when(disputeDao.existsByJobAssignmentIdAndStatusIn(assignmentId, DisputeManager.ACTIVE))
                .thenReturn(true);

        assertThrows(ConflictException.class,
                () -> payoutManager.refundEmployer(assignmentId, null, "uyuşmazlık sürüyor"));

        assertEquals(PaymentStatus.HELD_IN_ESCROW, assignment.getPaymentStatus());
        verify(refundDao, never()).save(any());
        verify(settlementService, never()).enqueueRefund(any(), any(), any());
        verify(jobAssignmentDao).findByIdForUpdate(assignmentId);
    }

    @Test
    void refundIsNotReportedWhenItCannotBeQueued() {
        JobAssignment assignment = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);
        settledPaymentFor(assignment);
        when(settlementService.enqueueRefund(any(), any(), any())).thenReturn(Optional.empty());

        assertThrows(ExternalServiceException.class,
                () -> payoutManager.refundEmployer(assignmentId, null, "tam iade"));

        assertEquals(PaymentStatus.HELD_IN_ESCROW, assignment.getPaymentStatus());
        verify(refundDao, never()).save(any());
    }

    @Test
    void aCompletedAssignmentInEscrowCanBeReleased() {
        JobAssignment assignment = assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW);
        settledPaymentExists();

        Payout payout = payoutManager.releaseToWorker(assignmentId);

        assertEquals(PayoutStatus.PENDING, payout.getStatus());
        assertEquals(new BigDecimal("1000.00"), payout.getAmount());
        assertEquals(PaymentStatus.RELEASED_TO_WORKER, assignment.getPaymentStatus());
    }

    @Test
    void moneyIsNotReleasedBeforeTheWorkIsCompleted() {
        JobAssignment assignment = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);
        settledPaymentExists();

        assertThrows(ConflictException.class, () -> payoutManager.releaseToWorker(assignmentId));

        assertEquals(PaymentStatus.HELD_IN_ESCROW, assignment.getPaymentStatus());
        verify(payoutDao, never()).save(any());
    }

    @Test
    void releaseIsNotReportedWhenItCannotBeQueued() {
        JobAssignment assignment = assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW);
        settledPaymentExists();
        when(settlementService.enqueue(any(), any(), eq(SettlementOperation.APPROVE)))
                .thenReturn(Optional.empty());

        assertThrows(ExternalServiceException.class, () -> payoutManager.releaseToWorker(assignmentId));

        assertEquals(PaymentStatus.HELD_IN_ESCROW, assignment.getPaymentStatus());
        verify(payoutDao, never()).save(any());
    }

    @Test
    void releasingWithoutASettledPaymentIsRejected() {
        assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW);
        when(paymentService.getSettledPaymentFor(assignmentId))
                .thenThrow(new ResourceNotFoundException("odeme yok"));

        assertThrows(ResourceNotFoundException.class, () -> payoutManager.releaseToWorker(assignmentId));
        verify(payoutDao, never()).save(any());
    }

    @Test
    void theSameAssignmentIsNeverPaidOutTwice() {
        assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW);
        settledPaymentExists();
        when(payoutDao.existsByJobAssignmentId(assignmentId)).thenReturn(true);
        when(payoutDao.findByPaymentId(any())).thenReturn(Optional.of(new Payout()));

        payoutManager.releaseToWorker(assignmentId);

        verify(payoutDao, never()).save(any());
    }

    @Test
    void moneyIsNotReleasedToAWorkerWhoseIbanOwnershipIsUnproven() {
        JobAssignment assignment = assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW);
        settledPaymentExists();
        when(bankAccountService.findVerifiedPrimaryAccount(any())).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> payoutManager.releaseToWorker(assignmentId));

        assertEquals(PaymentStatus.HELD_IN_ESCROW, assignment.getPaymentStatus());
        verify(payoutDao, never()).save(any());
        verify(settlementService, never()).enqueue(any(), any(), any());
    }

    @Test
    void theReleasedPayoutPointsAtTheVerifiedAccount() {
        assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW);
        settledPaymentExists();

        Payout payout = payoutManager.releaseToWorker(assignmentId);

        assertEquals(BankAccountVerificationStatus.VERIFIED, payout.getBankAccount().getVerificationStatus());
    }

    @Test
    void escrowedMoneyCanBeRefundedToTheEmployer() {
        JobAssignment assignment = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);
        settledPaymentExists();

        Refund refund = payoutManager.refundEmployer(assignmentId, new BigDecimal("1075.00"), "is iptal edildi");

        assertEquals(RefundStatus.PENDING, refund.getStatus());
        assertEquals(new BigDecimal("1075.00"), refund.getAmount());
        assertEquals(PaymentStatus.REFUNDED, assignment.getPaymentStatus());
    }

    @Test
    void aRefundCannotExceedWhatWasCharged() {
        assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);
        settledPaymentExists();

        assertThrows(ValidationException.class,
                () -> payoutManager.refundEmployer(assignmentId, new BigDecimal("2000.00"), "fazla"));
        assertThrows(ValidationException.class,
                () -> payoutManager.refundEmployer(assignmentId, BigDecimal.ZERO, "sifir"));
    }

    @Test
    void alreadyReleasedMoneyCannotBeRefunded() {
        JobAssignment assignment = assignment(AssignmentStatus.COMPLETED, PaymentStatus.RELEASED_TO_WORKER);
        settledPaymentExists();

        assertThrows(ConflictException.class,
                () -> payoutManager.refundEmployer(assignmentId, null, "gec kalindi"));
        assertEquals(PaymentStatus.RELEASED_TO_WORKER, assignment.getPaymentStatus());
    }

    @Test
    void omittingTheAmountRefundsTheFullCharge() {
        assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);
        settledPaymentExists();

        Refund refund = payoutManager.refundEmployer(assignmentId, null, "tam iade");

        assertEquals(new BigDecimal("1075.00"), refund.getAmount());
    }

    @Test
    void anOpenDisputeFreezesTheEscrow() {
        JobAssignment assignment = assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW);
        when(jobAssignmentDao.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(payoutDao.existsByJobAssignmentId(assignmentId)).thenReturn(false);
        when(disputeDao.existsByJobAssignmentIdAndStatusIn(eq(assignmentId), any())).thenReturn(true);

        assertThrows(ConflictException.class, () -> payoutManager.releaseToWorker(assignmentId));

        verify(payoutDao, never()).save(any());
        assertEquals(PaymentStatus.HELD_IN_ESCROW, assignment.getPaymentStatus());
    }

    @Test
    void releasingTheEscrowQueuesTheProviderApprovalInsteadOfCallingItInline() {
        JobAssignment assignment = assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW);
        Payment payment = settledPaymentExists();
        payment.setProviderTransactionId("txn-123");

        payoutManager.releaseToWorker(assignmentId);

        verify(settlementService).enqueue(eq(payment), eq(assignment), eq(SettlementOperation.APPROVE));
    }

    @Test
    void aPayoutStartsPendingUntilTheProviderConfirms() {
        assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW);
        settledPaymentExists().setProviderTransactionId("txn-123");

        assertEquals(PayoutStatus.PENDING, payoutManager.releaseToWorker(assignmentId).getStatus());
    }

    @Test
    void aFullRefundSendsTheMoneyBackToTheCard() {
        JobAssignment assignment = assignment(AssignmentStatus.CANCELLED, PaymentStatus.HELD_IN_ESCROW);
        Payment payment = settledPaymentExists();
        payment.setProviderTransactionId("txn-456");

        payoutManager.refundEmployer(assignmentId, null, "iptal");

        verify(settlementService).enqueueRefund(eq(payment), eq(assignment), eq(new BigDecimal("1075.00")));
    }

    @Test
    void aFullRefundNeedsNoDisapprovalBecauseTheLineIsReversed() {
        assignment(AssignmentStatus.CANCELLED, PaymentStatus.HELD_IN_ESCROW);
        Payment payment = settledPaymentExists();
        payment.setProviderTransactionId("txn-456");

        payoutManager.refundEmployer(assignmentId, null, "iptal");

        verify(settlementService, never()).enqueue(any(), any(), eq(SettlementOperation.DISAPPROVE));
    }

    @Test
    void aPartialRefundAlsoBlocksTheWorkerShareOnWhatIsKept() {
        JobAssignment assignment = assignment(AssignmentStatus.CANCELLED, PaymentStatus.HELD_IN_ESCROW);
        Payment payment = settledPaymentExists();
        payment.setProviderTransactionId("txn-456");

        payoutManager.refundEmployer(assignmentId, new BigDecimal("537.50"), "gec iptal");

        verify(settlementService).enqueueRefund(eq(payment), eq(assignment), eq(new BigDecimal("537.50")));
        verify(settlementService).enqueue(eq(payment), eq(assignment), eq(SettlementOperation.DISAPPROVE));
    }

}
