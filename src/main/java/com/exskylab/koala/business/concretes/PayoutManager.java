package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SettlementService;
import com.exskylab.koala.core.constants.DisputeMessages;
import com.exskylab.koala.core.constants.PayoutMessages;
import com.exskylab.koala.core.dtos.payout.request.ConfirmPayoutSettlementDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.monitoring.PaymentMetrics;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.dataAccess.DisputeDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.PayoutDao;
import com.exskylab.koala.dataAccess.RefundDao;
import com.exskylab.koala.entities.*;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
    private final PaymentMetrics paymentMetrics;

    public PayoutManager(PayoutDao payoutDao, RefundDao refundDao, PaymentService paymentService,
                         JobAssignmentDao jobAssignmentDao, BankAccountService bankAccountService,
                         DisputeDao disputeDao, PaymentStateMachine stateMachine,
                         SettlementService settlementService,
                         ActivityNotificationService activityNotificationService,
                         PaymentMetrics paymentMetrics) {
        this.payoutDao = payoutDao;
        this.refundDao = refundDao;
        this.paymentService = paymentService;
        this.jobAssignmentDao = jobAssignmentDao;
        this.bankAccountService = bankAccountService;
        this.disputeDao = disputeDao;
        this.stateMachine = stateMachine;
        this.settlementService = settlementService;
        this.activityNotificationService = activityNotificationService;
        this.paymentMetrics = paymentMetrics;
    }

    @Override
    @Transactional
    public Payout releaseToWorker(UUID jobAssignmentId) {
        JobAssignment assignment = jobAssignmentDao.findByIdForUpdate(jobAssignmentId)
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

        BankAccount destination = bankAccountService
                .findVerifiedPrimaryAccount(assignment.getWorker().getId())
                .orElseThrow(() -> {
                    log.warn("Release refused on assignment {}: worker {} has not proven IBAN ownership.",
                            jobAssignmentId, assignment.getWorker().getId());
                    return new ConflictException(PayoutMessages.WORKER_IBAN_NOT_VERIFIED);
                });

        Payment payment = settledPayment(jobAssignmentId);

        Payout payout = new Payout();
        payout.setJobAssignment(assignment);
        payout.setPayment(payment);
        payout.setWorker(assignment.getWorker());
        payout.setBankAccount(destination);
        payout.setAmount(assignment.getWorkerPayout());
        payout.setCurrency(CURRENCY);
        payout.setStatus(PayoutStatus.PENDING);

        settlementService.enqueue(payment, assignment, SettlementOperation.APPROVE)
                .orElseThrow(() -> new ExternalServiceException(PayoutMessages.SETTLEMENT_NOT_QUEUED));
        stateMachine.transition(assignment, PaymentStatus.RELEASED_TO_WORKER);

        Payout saved = payoutDao.save(payout);
        log.info("Payout {} created for assignment {} amount {}", saved.getId(), jobAssignmentId, saved.getAmount());
        activityNotificationService.payoutReleased(assignment);
        return saved;
    }

    @Override
    @Transactional
    public Refund refundEmployer(UUID jobAssignmentId, BigDecimal amount, String reason) {
        JobAssignment assignment = jobAssignmentDao.findByIdForUpdate(jobAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(PayoutMessages.ASSIGNMENT_NOT_FOUND));

        if (disputeDao.existsByJobAssignmentIdAndStatusIn(jobAssignmentId, DisputeManager.ACTIVE)) {
            throw new ConflictException(DisputeMessages.BLOCKS_RELEASE);
        }

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

        settlementService.enqueueRefund(payment, assignment, refundAmount)
                .orElseThrow(() -> new ExternalServiceException(PayoutMessages.SETTLEMENT_NOT_QUEUED));

        if (refundAmount.compareTo(payment.getTotalCharged()) < 0) {
            settlementService.enqueue(payment, assignment, SettlementOperation.DISAPPROVE)
                    .orElseThrow(() -> new ExternalServiceException(PayoutMessages.SETTLEMENT_NOT_QUEUED));
        }

        stateMachine.transition(assignment, PaymentStatus.REFUNDED);

        Refund saved = refundDao.save(refund);
        log.info("Refund {} created for assignment {} amount {}", saved.getId(), jobAssignmentId, refundAmount);
        return saved;
    }

    @Override
    @Transactional
    public Payout confirmSettlement(UUID payoutId, ConfirmPayoutSettlementDto evidence) {
        Payout payout = payoutDao.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException(PayoutMessages.NOT_FOUND));

        String reported = evidence.getSettlementReference().trim();

        if (payout.getStatus() == PayoutStatus.COMPLETED) {
            if (reported.equals(payout.getSettlementReference())) {
                paymentMetrics.payoutAlreadySettled();
                log.info("Payout {} is already settled by statement line {}, request ignored.",
                        payoutId, reported);
                return payout;
            }
            throw new ConflictException(PayoutMessages.SETTLEMENT_REFERENCE_CONFLICT);
        }

        if (!PayoutStatus.AWAITING_SETTLEMENT.contains(payout.getStatus())) {
            log.warn("Payout {} cannot be settled from status {}.", payoutId, payout.getStatus());
            throw new ConflictException(PayoutMessages.NOT_AWAITING_SETTLEMENT);
        }

        if (payout.getProviderReference() == null || payout.getProviderReference().isBlank()) {
            log.error("Payout {} carries no provider reference; there is nothing to match a statement line "
                    + "against and the row must be escalated instead of settled.", payoutId);
            throw new ConflictException(PayoutMessages.NO_PROVIDER_REFERENCE);
        }

        String mismatch = firstMismatch(payout, evidence);
        if (mismatch != null) {
            paymentMetrics.payoutSettlementEvidenceMismatch();
            log.error("Payout {} settlement evidence rejected on field {}; the payout stays {}.",
                    payoutId, mismatch, payout.getStatus());
            throw new ValidationException(PayoutMessages.SETTLEMENT_EVIDENCE_MISMATCH);
        }

        payout.setStatus(PayoutStatus.COMPLETED);
        payout.setSettlementReference(reported);
        payout.setCompletedAt(evidence.getSettledAt());
        payout.setFailureReason(null);

        Payout settled;
        try {
            settled = payoutDao.saveAndFlush(payout);
        } catch (DataIntegrityViolationException violation) {
            if (hasConstraint(violation, "uk_payouts_settlement_reference")) {
                throw new ConflictException(PayoutMessages.SETTLEMENT_REFERENCE_TAKEN);
            }
            throw violation;
        }

        paymentMetrics.payoutSettled();
        log.info("Payout {} settled at {} against statement line {}",
                settled.getId(), settled.getCompletedAt(), reported);
        return settled;
    }

    @Override
    @Transactional(readOnly = true)
    public long countPayoutsAwaitingSettlementLongerThan(Duration age) {
        return payoutDao.countAwaitingSettlementSince(
                PayoutStatus.AWAITING_SETTLEMENT, Instant.now().minus(age));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payout> getPayoutsForWorker(UUID workerId) {
        return payoutDao.findAllByWorkerIdOrderByCreatedAtDesc(workerId);
    }

    private String firstMismatch(Payout payout, ConfirmPayoutSettlementDto evidence) {
        if (!payout.getProviderReference().equals(evidence.getProviderReference().trim())) {
            return "providerReference";
        }
        if (payout.getAmount() == null || payout.getAmount().compareTo(evidence.getAmount()) != 0) {
            return "amount";
        }
        if (!payout.getCurrency().equals(evidence.getCurrency().trim().toUpperCase(Locale.ROOT))) {
            return "currency";
        }
        if (payout.getCreatedAt() != null && evidence.getSettledAt().isBefore(payout.getCreatedAt())) {
            return "settledAt";
        }
        return null;
    }

    private boolean hasConstraint(Throwable exception, String constraintName) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return constraintName.equals(violation.getConstraintName());
            }
            current = current.getCause();
        }
        return false;
    }

    private Payment settledPayment(UUID jobAssignmentId) {
        return paymentService.getSettledPaymentFor(jobAssignmentId);
    }

}
