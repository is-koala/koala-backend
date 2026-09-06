package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.JobAssignmentMessages;
import com.exskylab.koala.core.constants.PaymentMessages;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.monitoring.PaymentMetrics;
import com.exskylab.koala.core.properties.PaymentVerificationProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoPaymentResultRequestDto;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.IyzicoPaymentResultResponseDto;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.PaymentDao;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import com.exskylab.koala.entities.PaymentStatus;
import com.exskylab.koala.core.constants.PayoutMessages;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.hibernate.exception.ConstraintViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class PaymentManager implements PaymentService {

    private static final String CURRENCY = "TRY";
    private static final Integer VERIFIED_BY_THREE_D_SECURE = 1;
    private static final List<PaymentAttemptStatus> PENDING_STATUSES =
            List.of(PaymentAttemptStatus.INITIATED, PaymentAttemptStatus.VERIFY_PENDING,
                    PaymentAttemptStatus.IN_REVIEW);

    private final PaymentDao paymentDao;
    private final IyzicoPaymentService iyzicoPaymentService;
    private final PaymentStateMachine stateMachine;
    private final SecurityService securityService;
    private final JobAssignmentDao jobAssignmentDao;
    private final AssignmentParticipants assignmentParticipants;
    private final PaymentMetrics paymentMetrics;
    private final PaymentVerificationProperties verificationProperties;

    public PaymentManager(PaymentDao paymentDao, IyzicoPaymentService iyzicoPaymentService,
                          PaymentStateMachine stateMachine, SecurityService securityService,
                          JobAssignmentDao jobAssignmentDao, AssignmentParticipants assignmentParticipants,
                          PaymentMetrics paymentMetrics, PaymentVerificationProperties verificationProperties) {
        this.paymentDao = paymentDao;
        this.iyzicoPaymentService = iyzicoPaymentService;
        this.stateMachine = stateMachine;
        this.securityService = securityService;
        this.jobAssignmentDao = jobAssignmentDao;
        this.assignmentParticipants = assignmentParticipants;
        this.paymentMetrics = paymentMetrics;
        this.verificationProperties = verificationProperties;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment startAttempt(JobAssignment jobAssignment, CommissionCalculator.Breakdown breakdown) {
        JobAssignment lockedAssignment = jobAssignmentDao.findByIdForUpdate(jobAssignment.getId())
                .orElseThrow(() -> new ResourceNotFoundException(JobAssignmentMessages.NOT_FOUND));
        if (!stateMachine.canInitiate(lockedAssignment)) {
            throw new ConflictException(JobAssignmentMessages.ASSIGNMENT_NOT_READY_FOR_PAYMENT);
        }
        if (hasPendingAttemptForAssignment(lockedAssignment.getId())) {
            throw new ConflictException(PaymentMessages.ATTEMPT_IN_PROGRESS);
        }

        Payment payment = new Payment();
        payment.setJobAssignment(lockedAssignment);
        payment.setConversationId(UUID.randomUUID().toString());
        payment.setStatus(PaymentAttemptStatus.INITIATED);
        payment.setPricingVersion(lockedAssignment.getPricingVersion());
        payment.setCurrency(lockedAssignment.getCurrency() == null ? CURRENCY : lockedAssignment.getCurrency());
        payment.setWorkerPayout(breakdown.workerPayout());
        payment.setCommissionAmount(breakdown.commission());
        payment.setCommissionRate(breakdown.rate());
        payment.setTotalCharged(breakdown.totalCharged());

        Payment saved;
        try {
            saved = paymentDao.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            if (isActiveAttemptConstraint(exception)) {
                throw new ConflictException(PaymentMessages.ATTEMPT_IN_PROGRESS);
            }
            paymentMetrics.initiationPersistenceError();
            throw exception;
        } catch (RuntimeException exception) {
            paymentMetrics.initiationPersistenceError();
            throw exception;
        }
        log.info("Payment attempt {} started for assignment {}", saved.getId(), lockedAssignment.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingAttemptForAssignment(UUID jobAssignmentId) {
        return paymentDao.existsByJobAssignmentIdAndStatusIn(jobAssignmentId, PENDING_STATUSES);
    }

    private boolean isActiveAttemptConstraint(Throwable exception) {
        return hasConstraint(exception, "uk_payments_one_active_attempt");
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

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachProviderSubmerchantKey(Payment payment, String providerSubmerchantKey) {
        payment.setProviderSubmerchantKey(providerSubmerchantKey);
        paymentDao.saveAndFlush(payment);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachProviderToken(Payment payment, String providerToken) {
        payment.setProviderToken(providerToken);
        payment.setNextVerificationAt(Instant.now().plus(verificationProperties.getInitialReconciliationDelay()));
        try {
            paymentDao.saveAndFlush(payment);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uk_payments_provider_token")) {
                throw new ExternalServiceException(PaymentMessages.PROVIDER_RESPONSE_INVALID);
            }
            throw exception;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInitiationUncertain(UUID paymentId) {
        paymentDao.findWithDetailsById(paymentId).ifPresent(payment -> {
            payment.setStatus(PaymentAttemptStatus.VERIFY_PENDING);
            payment.setFailureReason(PaymentMessages.VERIFICATION_PENDING);
            payment.setVerificationAttempts(payment.getVerificationAttempts() + 1);
            payment.setNextVerificationAt(null);
            payment.setCompletedAt(null);
            paymentDao.save(payment);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failInitiation(UUID paymentId) {
        paymentDao.findWithDetailsById(paymentId).ifPresent(payment ->
                fail(payment, PaymentMessages.INITIATION_FAILED));
    }

    @Override
    @Transactional
    public Payment completeFromCallback(String providerToken) {
        UUID assignmentId = paymentDao.findJobAssignmentIdByProviderToken(providerToken).orElse(null);
        if (assignmentId == null) {
            paymentMetrics.callbackUnknownToken();
            throw new ResourceNotFoundException(PaymentMessages.NOT_FOUND);
        }
        lockAssignment(assignmentId);
        Payment payment = paymentDao.findByProviderToken(providerToken).orElse(null);
        if (payment == null) {
            paymentMetrics.callbackUnknownToken();
            throw new ResourceNotFoundException(PaymentMessages.NOT_FOUND);
        }

        if (!PENDING_STATUSES.contains(payment.getStatus())) {
            if (payment.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
                paymentMetrics.callbackAlreadySucceeded();
            }
            log.info("Payment {} is terminal with status {}, callback ignored.",
                    payment.getId(), payment.getStatus());
            return payment;
        }

        return reconcile(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDueVerificationAttemptIds() {
        return paymentDao.findDueVerificationIds(
                PENDING_STATUSES,
                Instant.now(), PageRequest.of(0, verificationProperties.getBatchSize()));
    }

    @Override
    @Transactional
    public boolean reconcileAttempt(UUID paymentId) {
        UUID assignmentId = paymentDao.findJobAssignmentIdById(paymentId).orElse(null);
        if (assignmentId == null) {
            return false;
        }
        lockAssignment(assignmentId);
        Payment payment = paymentDao.findWithDetailsById(paymentId).orElse(null);
        if (payment == null || !PENDING_STATUSES.contains(payment.getStatus())
                || payment.getProviderToken() == null || payment.getProviderToken().isBlank()) {
            return false;
        }
        return reconcile(payment).getStatus() == PaymentAttemptStatus.SUCCEEDED;
    }

    private Payment reconcile(Payment payment) {
        String providerToken = payment.getProviderToken();

        IyzicoPaymentResultRequestDto request = new IyzicoPaymentResultRequestDto();
        request.setToken(providerToken);
        request.setConversationId(payment.getConversationId());

        IyzicoPaymentResultResponseDto result;
        try {
            result = iyzicoPaymentService.retrieveCheckoutFormResult(request);
        } catch (RuntimeException e) {
            paymentMetrics.callbackProviderError();
            log.warn("Payment {} could not be verified with the provider ({})",
                    payment.getId(), e.getClass().getSimpleName());
            return deferVerification(payment);
        }

        String identityMismatch = identityMismatch(payment, result);
        if (identityMismatch != null) {
            paymentMetrics.callbackAmountMismatch();
            log.error("Payment {} remains blocked: provider result does not identify its attempt ({})",
                    payment.getId(), identityMismatch);
            return deferVerification(payment);
        }

        if ("FAILURE".equalsIgnoreCase(result.getPaymentStatus())) {
            paymentMetrics.callbackDeclined();
            log.info("Payment {} was not completed by the provider, paymentStatus={}",
                    payment.getId(), result.getPaymentStatus());
            return fail(payment, "paymentStatus=" + result.getPaymentStatus());
        }

        if (!"SUCCESS".equalsIgnoreCase(result.getPaymentStatus())) {
            log.warn("Payment {} returned an indeterminate paymentStatus={}",
                    payment.getId(), result.getPaymentStatus());
            return deferVerification(payment);
        }

        String snapshotMismatch = successfulSnapshotMismatch(payment, result);
        if (snapshotMismatch != null) {
            paymentMetrics.callbackAmountMismatch();
            log.error("Payment {} remains blocked: provider result does not match its immutable snapshot ({})",
                    payment.getId(), snapshotMismatch);
            return deferVerification(payment);
        }

        if (!VERIFIED_BY_THREE_D_SECURE.equals(result.getMdStatus())) {
            paymentMetrics.callbackThreeDSecureVerificationFailed();
            log.warn("Payment {} rejected: 3-D Secure verification did not succeed, mdStatus={}",
                    payment.getId(), result.getMdStatus());
            return scheduleReconciliation(payment, PaymentAttemptStatus.VERIFY_PENDING,
                    PaymentMessages.THREE_D_SECURE_FAILED);
        }

        if (result.getFraudStatus() != null && result.getFraudStatus() == -1) {
            paymentMetrics.callbackFraudRejected();
            log.warn("Payment {} was rejected by fraud filters.", payment.getId());
            return fail(payment, PaymentMessages.FRAUD_REVIEW);
        }

        if (result.getFraudStatus() != null && result.getFraudStatus() == 0) {
            paymentMetrics.callbackInReview();
            log.warn("Payment {} is under fraud review, escrow is not opened yet.", payment.getId());
            return hold(payment, result);
        }

        if (!Integer.valueOf(1).equals(result.getFraudStatus())) {
            log.warn("Payment {} returned an indeterminate fraudStatus={}",
                    payment.getId(), result.getFraudStatus());
            return deferVerification(payment);
        }

        if (!stateMachine.canInitiate(payment.getJobAssignment())) {
            log.error("Payment {} requires manual review because assignment {} is no longer collectible.",
                    payment.getId(), payment.getJobAssignment().getId());
            return requireManualReview(payment);
        }

        Payment settled = settle(payment, result);
        paymentMetrics.callbackSucceeded();
        return settled;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> getAttemptsForAssignment(UUID jobAssignmentId) {
        JobAssignment assignment = jobAssignmentDao.findById(jobAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(PaymentMessages.ASSIGNMENT_NOT_FOUND));

        if (!assignmentParticipants.isParticipant(assignment, securityService.getAuthenticatedUserId())) {
            throw new ResourceNotFoundException(PaymentMessages.ASSIGNMENT_NOT_FOUND);
        }

        return paymentDao.findAllByJobAssignmentIdOrderByCreatedAtDesc(jobAssignmentId);
    }

    private String identityMismatch(Payment payment, IyzicoPaymentResultResponseDto result) {
        JobAssignment assignment = payment.getJobAssignment();
        if (!Objects.equals(payment.getConversationId(), result.getConversationId())) return "conversationId";
        if (!Objects.equals(payment.getProviderToken(), result.getToken())) return "token";
        if (!Objects.equals(assignment.getId().toString(), result.getBasketId())) return "basketId";
        if (!Objects.equals(payment.getCurrency(), result.getCurrency())) return "currency";
        if (result.getPaymentId() == null || result.getPaymentId().isBlank()) return "paymentId";
        if (payment.getProviderPaymentId() != null
                && !Objects.equals(payment.getProviderPaymentId(), result.getPaymentId())) return "paymentId";
        return null;
    }

    private String successfulSnapshotMismatch(Payment payment, IyzicoPaymentResultResponseDto result) {
        JobAssignment assignment = payment.getJobAssignment();
        if (different(payment.getTotalCharged(), result.getPrice())) return "price";
        if (different(payment.getTotalCharged(), result.getPaidPrice())) return "paidPrice";
        if (result.getItemCount() != 1) return "itemTransactions count";
        if (assignment.getJob() == null
                || !Objects.equals(assignment.getJob().getId().toString(), result.getItemId())) return "itemId";
        if (different(payment.getTotalCharged(), result.getItemPrice())) return "item price";
        if (different(payment.getTotalCharged(), result.getItemPaidPrice())) return "item paidPrice";
        if (different(payment.getWorkerPayout(), result.getItemSubMerchantPrice())) return "subMerchantPrice";
        if (!Objects.equals(payment.getProviderSubmerchantKey(), result.getItemSubMerchantKey())) {
            return "subMerchantKey";
        }
        if (result.getPaymentTransactionId() == null || result.getPaymentTransactionId().isBlank()) {
            return "paymentTransactionId";
        }
        if (payment.getProviderTransactionId() != null
                && !Objects.equals(payment.getProviderTransactionId(), result.getPaymentTransactionId())) {
            return "paymentTransactionId";
        }
        return null;
    }

    private boolean different(BigDecimal expected, BigDecimal actual) {
        return expected == null || actual == null || expected.compareTo(actual) != 0;
    }

    private Payment deferVerification(Payment payment) {
        return scheduleReconciliation(payment, PaymentAttemptStatus.VERIFY_PENDING,
                PaymentMessages.VERIFICATION_PENDING);
    }

    private Payment hold(Payment payment, IyzicoPaymentResultResponseDto result) {
        payment.setProviderPaymentId(result.getPaymentId());
        payment.setProviderTransactionId(result.getPaymentTransactionId());
        return scheduleReconciliation(payment, PaymentAttemptStatus.IN_REVIEW, PaymentMessages.FRAUD_REVIEW);
    }

    private Payment scheduleReconciliation(Payment payment, PaymentAttemptStatus status, String reason) {
        payment.setStatus(status);
        payment.setFailureReason(reason);
        payment.setCompletedAt(null);
        int attempts = payment.getVerificationAttempts() + 1;
        payment.setVerificationAttempts(attempts);
        payment.setNextVerificationAt(attempts >= verificationProperties.getMaxAttempts()
                ? null : Instant.now().plus(verificationBackoffFor(attempts)));
        return paymentDao.save(payment);
    }

    private Payment requireManualReview(Payment payment) {
        payment.setStatus(PaymentAttemptStatus.VERIFY_PENDING);
        payment.setFailureReason(PaymentMessages.VERIFICATION_PENDING);
        payment.setCompletedAt(null);
        payment.setNextVerificationAt(null);
        return paymentDao.save(payment);
    }

    private JobAssignment lockAssignment(UUID assignmentId) {
        return jobAssignmentDao.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(JobAssignmentMessages.NOT_FOUND));
    }

    private java.time.Duration verificationBackoffFor(int attempts) {
        java.time.Duration backoff = verificationProperties.getInitialBackoff()
                .multipliedBy(1L << Math.min(attempts - 1, 20));
        return backoff.compareTo(verificationProperties.getMaxBackoff()) > 0
                ? verificationProperties.getMaxBackoff() : backoff;
    }

    private Payment settle(Payment payment, IyzicoPaymentResultResponseDto result) {
        JobAssignment assignment = payment.getJobAssignment();

        payment.setStatus(PaymentAttemptStatus.SUCCEEDED);
        payment.setProviderPaymentId(result.getPaymentId());
        payment.setProviderTransactionId(result.getPaymentTransactionId());
        payment.setCompletedAt(Instant.now());
        payment.setNextVerificationAt(null);
        payment.setFailureReason(null);

        stateMachine.transition(assignment, PaymentStatus.HELD_IN_ESCROW);
        assignment.setWorkerPayout(payment.getWorkerPayout());
        assignment.setCommissionAmount(payment.getCommissionAmount());
        assignment.setCommissionRate(payment.getCommissionRate());
        assignment.setTotalCharged(payment.getTotalCharged());
        assignment.setFinalPayout(payment.getWorkerPayout());

        log.info("Payment {} settled for assignment {}", payment.getId(), assignment.getId());
        return paymentDao.save(payment);
    }

    private Payment fail(Payment payment, String reason) {
        payment.setStatus(PaymentAttemptStatus.FAILED);
        payment.setFailureReason(reason == null ? null
                : reason.substring(0, Math.min(reason.length(), 512)));
        payment.setCompletedAt(Instant.now());
        payment.setNextVerificationAt(null);

        JobAssignment assignment = payment.getJobAssignment();
        if (stateMachine.canTransition(assignment.getPaymentStatus(), PaymentStatus.FAILED)) {
            assignment.setPaymentStatus(PaymentStatus.FAILED);
        }

        return paymentDao.save(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getSettledPaymentFor(UUID jobAssignmentId) {
        return paymentDao.findAllByJobAssignmentIdOrderByCreatedAtDesc(jobAssignmentId).stream()
                .filter(payment -> payment.getStatus() == PaymentAttemptStatus.SUCCEEDED)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(PayoutMessages.NO_SETTLED_PAYMENT));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingPaymentForUser(UUID userId) {
        return paymentDao.existsPendingForUser(userId, PENDING_STATUSES);
    }
}
