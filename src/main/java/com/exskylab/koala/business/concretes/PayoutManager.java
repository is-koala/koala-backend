package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.core.constants.PayoutMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.dataAccess.BankAccountDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.PaymentDao;
import com.exskylab.koala.dataAccess.PayoutDao;
import com.exskylab.koala.dataAccess.RefundDao;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PayoutManager implements PayoutService {

    private static final String CURRENCY = "TRY";

    private final Logger logger = LoggerFactory.getLogger(PayoutManager.class);

    private final PayoutDao payoutDao;
    private final RefundDao refundDao;
    private final PaymentDao paymentDao;
    private final JobAssignmentDao jobAssignmentDao;
    private final BankAccountDao bankAccountDao;
    private final PaymentStateMachine stateMachine;

    public PayoutManager(PayoutDao payoutDao, RefundDao refundDao, PaymentDao paymentDao,
                         JobAssignmentDao jobAssignmentDao, BankAccountDao bankAccountDao,
                         PaymentStateMachine stateMachine) {
        this.payoutDao = payoutDao;
        this.refundDao = refundDao;
        this.paymentDao = paymentDao;
        this.jobAssignmentDao = jobAssignmentDao;
        this.bankAccountDao = bankAccountDao;
        this.stateMachine = stateMachine;
    }

    @Override
    @Transactional
    public Payout releaseToWorker(UUID jobAssignmentId) {
        JobAssignment assignment = jobAssignmentDao.findById(jobAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(PayoutMessages.ASSIGNMENT_NOT_FOUND));

        if (payoutDao.existsByJobAssignmentId(jobAssignmentId)) {
            logger.info("Payout already exists for assignment {}, request ignored.", jobAssignmentId);
            return payoutDao.findByPaymentId(settledPayment(jobAssignmentId).getId())
                    .orElseThrow(() -> new ResourceNotFoundException(PayoutMessages.NOT_FOUND));
        }

        if (!stateMachine.canRelease(assignment)) {
            throw new IllegalStateException(PayoutMessages.NOT_READY_FOR_RELEASE);
        }

        Payment payment = settledPayment(jobAssignmentId);

        Payout payout = new Payout();
        payout.setJobAssignment(assignment);
        payout.setPayment(payment);
        payout.setWorker(assignment.getWorker());
        payout.setBankAccount(bankAccountDao
                .findByUserIdAndPrimaryAccountTrueAndActiveTrue(assignment.getWorker().getId())
                .orElse(null));
        payout.setAmount(assignment.getWorkerPayout());
        payout.setCurrency(CURRENCY);
        payout.setStatus(PayoutStatus.PENDING);

        stateMachine.transition(assignment, PaymentStatus.RELEASED_TO_WORKER);

        Payout saved = payoutDao.save(payout);
        logger.info("Payout {} created for assignment {} amount {}", saved.getId(), jobAssignmentId, saved.getAmount());
        return saved;
    }

    @Override
    @Transactional
    public Refund refundEmployer(UUID jobAssignmentId, BigDecimal amount, String reason) {
        JobAssignment assignment = jobAssignmentDao.findById(jobAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(PayoutMessages.ASSIGNMENT_NOT_FOUND));

        if (!stateMachine.canRefund(assignment)) {
            throw new IllegalStateException(PayoutMessages.NOT_REFUNDABLE);
        }

        Payment payment = settledPayment(jobAssignmentId);
        BigDecimal refundAmount = amount == null ? payment.getTotalCharged() : amount;

        if (refundAmount.signum() <= 0 || refundAmount.compareTo(payment.getTotalCharged()) > 0) {
            throw new IllegalArgumentException(PayoutMessages.INVALID_REFUND_AMOUNT);
        }

        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setAmount(refundAmount);
        refund.setCurrency(CURRENCY);
        refund.setStatus(RefundStatus.PENDING);
        refund.setReason(reason);

        stateMachine.transition(assignment, PaymentStatus.REFUNDED);

        Refund saved = refundDao.save(refund);
        logger.info("Refund {} created for assignment {} amount {}", saved.getId(), jobAssignmentId, refundAmount);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payout> getPayoutsForWorker(UUID workerId) {
        return payoutDao.findAllByWorkerIdOrderByCreatedAtDesc(workerId);
    }

    private Payment settledPayment(UUID jobAssignmentId) {
        return paymentDao.findAllByJobAssignmentIdOrderByCreatedAtDesc(jobAssignmentId).stream()
                .filter(p -> p.getStatus() == PaymentAttemptStatus.SUCCEEDED)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(PayoutMessages.NO_SETTLED_PAYMENT));
    }
}
