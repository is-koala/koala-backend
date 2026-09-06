package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SettlementService;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.constants.PayoutMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.dataAccess.DisputeDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.PayoutDao;
import com.exskylab.koala.dataAccess.RefundDao;
import com.exskylab.koala.entities.*;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class PayoutManager implements PayoutService {

    private static final String CURRENCY = "TRY";

    private final PayoutDao payoutDao;
    private final RefundDao refundDao;
    private final PaymentService paymentService;
    private final JobAssignmentDao jobAssignmentDao;
    private final BankAccountService bankAccountService;
    private final DisputeDao disputeDao;
    private final PaymentStateMachine stateMachine;
    private final SettlementService settlementService;
    private final ActivityNotificationService activityNotificationService;

    public PayoutManager(PayoutDao payoutDao, RefundDao refundDao, PaymentService paymentService,
                         JobAssignmentDao jobAssignmentDao, BankAccountService bankAccountService,
                         DisputeDao disputeDao, PaymentStateMachine stateMachine,
                         SettlementService settlementService,
                         ActivityNotificationService activityNotificationService) {
        this.payoutDao = payoutDao;
        this.refundDao = refundDao;
        this.paymentService = paymentService;
        this.jobAssignmentDao = jobAssignmentDao;
        this.bankAccountService = bankAccountService;
        this.disputeDao = disputeDao;
        this.stateMachine = stateMachine;
        this.settlementService = settlementService;
        this.activityNotificationService = activityNotificationService;
    }

    @Override
    @Transactional
    public Payout releaseToWorker(UUID jobAssignmentId) {
        JobAssignment assignment = jobAssignmentDao.findById(jobAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(PayoutMessages.ASSIGNMENT_NOT_FOUND));

        if (payoutDao.existsByJobAssignmentId(jobAssignmentId)) {
            log.info("Payout already exists for assignment {}, request ignored.", jobAssignmentId);
            return payoutDao.findByPaymentId(settledPayment(jobAssignmentId).getId())
                    .orElseThrow(() -> new ResourceNotFoundException(PayoutMessages.NOT_FOUND));
        }

        if (!stateMachine.canRelease(assignment)) {
            throw new ConflictException(PayoutMessages.NOT_READY_FOR_RELEASE);
        }

        if (disputeDao.existsByJobAssignmentIdAndStatusIn(jobAssignmentId, DisputeManager.ACTIVE)) {
            throw new ConflictException(DisputeMessages.BLOCKS_RELEASE);
        }

        Payment payment = settledPayment(jobAssignmentId);

        Payout payout = new Payout();
        payout.setJobAssignment(assignment);
        payout.setPayment(payment);
        payout.setWorker(assignment.getWorker());
        payout.setBankAccount(bankAccountService.findPrimaryAccount(assignment.getWorker().getId()).orElse(null));
        payout.setAmount(assignment.getWorkerPayout());
        payout.setCurrency(CURRENCY);
        payout.setStatus(PayoutStatus.PENDING);

        settlementService.enqueue(payment, assignment, SettlementOperation.APPROVE);
        stateMachine.transition(assignment, PaymentStatus.RELEASED_TO_WORKER);

        Payout saved = payoutDao.save(payout);
        log.info("Payout {} created for assignment {} amount {}", saved.getId(), jobAssignmentId, saved.getAmount());
        activityNotificationService.payoutReleased(assignment);
        return saved;
    }

    @Override
    @Transactional
    public Refund refundEmployer(UUID jobAssignmentId, BigDecimal amount, String reason) {
        JobAssignment assignment = jobAssignmentDao.findById(jobAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(PayoutMessages.ASSIGNMENT_NOT_FOUND));

        if (!stateMachine.canRefund(assignment)) {
            throw new ConflictException(PayoutMessages.NOT_REFUNDABLE);
        }

        Payment payment = settledPayment(jobAssignmentId);
        BigDecimal refundAmount = amount == null ? payment.getTotalCharged() : amount;

        if (refundAmount.signum() <= 0 || refundAmount.compareTo(payment.getTotalCharged()) > 0) {
            throw new ValidationException(PayoutMessages.INVALID_REFUND_AMOUNT);
        }

        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setAmount(refundAmount);
        refund.setCurrency(CURRENCY);
        refund.setStatus(RefundStatus.PENDING);
        refund.setReason(reason);

        settlementService.enqueueRefund(payment, assignment, refundAmount);

        if (refundAmount.compareTo(payment.getTotalCharged()) < 0) {
            settlementService.enqueue(payment, assignment, SettlementOperation.DISAPPROVE);
        }

        stateMachine.transition(assignment, PaymentStatus.REFUNDED);

        Refund saved = refundDao.save(refund);
        log.info("Refund {} created for assignment {} amount {}", saved.getId(), jobAssignmentId, refundAmount);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payout> getPayoutsForWorker(UUID workerId) {
        return payoutDao.findAllByWorkerIdOrderByCreatedAtDesc(workerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Refund> getLatestRefundForAssignment(UUID jobAssignmentId) {
        return refundDao.findFirstByPaymentJobAssignmentIdOrderByCreatedAtDesc(jobAssignmentId);
    }

    private Payment settledPayment(UUID jobAssignmentId) {
        return paymentService.getSettledPaymentFor(jobAssignmentId);
    }

}
